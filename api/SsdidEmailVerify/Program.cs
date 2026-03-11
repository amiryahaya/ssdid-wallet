using System.Collections.Concurrent;
using System.Security.Cryptography;
using Resend;

var builder = WebApplication.CreateBuilder(args);

// Resend
builder.Services.AddOptions();
builder.Services.AddHttpClient<ResendClient>();
builder.Services.Configure<ResendClientOptions>(o =>
{
    o.ApiToken = builder.Configuration["Resend:ApiToken"]
        ?? Environment.GetEnvironmentVariable("RESEND_APITOKEN")
        ?? throw new InvalidOperationException("Resend API token not configured");
});
builder.Services.AddTransient<IResend, ResendClient>();

// In-memory stores
builder.Services.AddSingleton<OtpStore>();
builder.Services.AddSingleton<RateLimiter>();

// Request size limit (16 KB max)
builder.WebHost.ConfigureKestrel(opts => opts.Limits.MaxRequestBodySize = 16 * 1024);

// CORS — restrict to mobile app (no browser origin needed)
builder.Services.AddCors(opts =>
{
    opts.AddDefaultPolicy(policy => policy
        .AllowNoOrigin()
        .WithMethods("POST", "GET")
        .WithHeaders("Content-Type"));
});

var app = builder.Build();

var fromEmail = builder.Configuration["Resend:FromEmail"] ?? "verify@ssdid.my";
var logger = app.Services.GetRequiredService<ILogger<Program>>();

app.UseCors();

// --- Endpoints ---

app.MapPost("/api/email/verify/send", async (SendRequest req, IResend resend, OtpStore store, RateLimiter limiter, HttpContext ctx) =>
{
    if (string.IsNullOrWhiteSpace(req.Email) || !req.Email.Contains('@'))
        return Results.BadRequest(new ErrorResponse("Invalid email address"));

    if (string.IsNullOrWhiteSpace(req.DeviceId))
        return Results.BadRequest(new ErrorResponse("Device ID is required"));

    // Cap input lengths
    if (req.Email.Length > 254 || req.DeviceId.Length > 128)
        return Results.BadRequest(new ErrorResponse("Invalid input"));

    var email = req.Email.Trim().ToLowerInvariant();
    var clientIp = ctx.Connection.RemoteIpAddress?.ToString() ?? "unknown";

    // IP-based rate limiting
    var ipLimit = limiter.CheckIp(clientIp);
    if (!ipLimit.Allowed)
        return Results.Json(new ErrorResponse(ipLimit.Reason!), statusCode: 429);

    // Per-email / per-device rate limiting
    var limitResult = limiter.Check(email, req.DeviceId);
    if (!limitResult.Allowed)
        return Results.Json(new ErrorResponse(limitResult.Reason!), statusCode: 429);

    // Generate 6-digit OTP (000000–999999)
    var code = RandomNumberGenerator.GetInt32(0, 1_000_000).ToString("D6");
    store.Set(email, code);

    // Send email via Resend
    var message = new EmailMessage
    {
        From = fromEmail,
        Subject = "SSDID Wallet — Verification Code"
    };
    message.To.Add(email);
    message.HtmlBody = EmailTemplate.Render(code);

    try
    {
        await resend.EmailSendAsync(message);
    }
    catch (Exception ex)
    {
        logger.LogError(ex, "Failed to send verification email to {Email} from IP {Ip}", email, clientIp);
        return Results.Json(new ErrorResponse("Failed to send email"), statusCode: 502);
    }

    limiter.RecordSend(email, req.DeviceId, clientIp);
    logger.LogInformation("OTP sent to {Email} from IP {Ip}", email, clientIp);

    return Results.Ok(new SendResponse(ExpiresIn: 600));
});

app.MapPost("/api/email/verify/confirm", (ConfirmRequest req, OtpStore store, RateLimiter limiter, HttpContext ctx) =>
{
    if (string.IsNullOrWhiteSpace(req.Email) || string.IsNullOrWhiteSpace(req.Code))
        return Results.BadRequest(new ErrorResponse("Email and code are required"));

    if (req.Code.Length > 6)
        return Results.BadRequest(new ErrorResponse("Invalid code format"));

    var email = req.Email.Trim().ToLowerInvariant();
    var clientIp = ctx.Connection.RemoteIpAddress?.ToString() ?? "unknown";

    // Check attempt lockout
    if (limiter.IsLockedOut(email))
        return Results.Json(new ErrorResponse("Too many failed attempts. Try again in 15 minutes."), statusCode: 429);

    var result = store.Verify(email, req.Code.Trim());

    if (!result)
    {
        limiter.RecordFailedAttempt(email);
        var remaining = limiter.RemainingAttempts(email);
        logger.LogWarning("Failed OTP attempt for {Email} from IP {Ip}, {Remaining} attempts left", email, clientIp, remaining);
        return Results.Json(new ErrorResponse($"Invalid code. {remaining} attempts remaining."), statusCode: 400);
    }

    limiter.ClearAttempts(email);
    logger.LogInformation("Email verified: {Email} from IP {Ip}", email, clientIp);
    return Results.Ok(new ConfirmResponse(Verified: true));
});

app.MapGet("/health", () => Results.Ok(new { status = "healthy" }));

app.Run();

// --- Records ---

record SendRequest(string Email, string DeviceId);
record SendResponse(int ExpiresIn);
record ConfirmRequest(string Email, string Code, string DeviceId);
record ConfirmResponse(bool Verified);
record ErrorResponse(string Error);

// --- CORS extension for mobile API (no Origin header) ---

static class CorsPolicyBuilderExtensions
{
    public static Microsoft.AspNetCore.Cors.Infrastructure.CorsPolicyBuilder AllowNoOrigin(
        this Microsoft.AspNetCore.Cors.Infrastructure.CorsPolicyBuilder builder)
    {
        return builder.SetIsOriginAllowed(_ => false);
    }
}

// --- OTP Store (in-memory) ---

class OtpStore : IDisposable
{
    private readonly ConcurrentDictionary<string, OtpEntry> _store = new();
    private static readonly TimeSpan Expiry = TimeSpan.FromMinutes(10);
    private readonly Timer _cleanupTimer;

    public OtpStore()
    {
        // Purge expired entries every 5 minutes
        _cleanupTimer = new Timer(_ => PurgeExpired(), null, TimeSpan.FromMinutes(5), TimeSpan.FromMinutes(5));
    }

    public void Set(string email, string code)
    {
        _store[email] = new OtpEntry(code, DateTime.UtcNow.Add(Expiry));
    }

    public bool Verify(string email, string code)
    {
        if (!_store.TryRemove(email, out var entry)) return false;
        if (DateTime.UtcNow > entry.ExpiresAt) return false;
        return CryptographicOperations.FixedTimeEquals(
            System.Text.Encoding.UTF8.GetBytes(entry.Code),
            System.Text.Encoding.UTF8.GetBytes(code));
    }

    private void PurgeExpired()
    {
        var now = DateTime.UtcNow;
        foreach (var kvp in _store)
        {
            if (now > kvp.Value.ExpiresAt)
                _store.TryRemove(kvp.Key, out _);
        }
    }

    public void Dispose() => _cleanupTimer.Dispose();

    private record OtpEntry(string Code, DateTime ExpiresAt);
}

// --- Rate Limiter (in-memory) ---

class RateLimiter : IDisposable
{
    private readonly ConcurrentDictionary<string, List<DateTime>> _emailSends = new();
    private readonly ConcurrentDictionary<string, List<DateTime>> _deviceSends = new();
    private readonly ConcurrentDictionary<string, List<DateTime>> _ipSends = new();
    private readonly ConcurrentDictionary<string, List<DateTime>> _failedAttempts = new();
    private readonly Timer _cleanupTimer;

    private const int MaxSendsPerEmailPerHour = 3;
    private const int MaxSendsPerDevicePerDay = 10;
    private const int MaxSendsPerIpPerHour = 20;
    private const int MaxFailedAttempts = 5;
    private static readonly TimeSpan LockoutDuration = TimeSpan.FromMinutes(15);

    public RateLimiter()
    {
        // Purge stale entries every 15 minutes
        _cleanupTimer = new Timer(_ => PurgeStale(), null, TimeSpan.FromMinutes(15), TimeSpan.FromMinutes(15));
    }

    public (bool Allowed, string? Reason) Check(string email, string deviceId)
    {
        var now = DateTime.UtcNow;

        // Per-email: max 3 per hour
        var emailSends = _emailSends.GetOrAdd(email, _ => new List<DateTime>());
        lock (emailSends)
        {
            emailSends.RemoveAll(t => now - t > TimeSpan.FromHours(1));
            if (emailSends.Count >= MaxSendsPerEmailPerHour)
                return (false, "Too many requests for this email. Try again later.");
        }

        // Per-device: max 10 per day
        var deviceSends = _deviceSends.GetOrAdd(deviceId, _ => new List<DateTime>());
        lock (deviceSends)
        {
            deviceSends.RemoveAll(t => now - t > TimeSpan.FromDays(1));
            if (deviceSends.Count >= MaxSendsPerDevicePerDay)
                return (false, "Daily verification limit reached for this device.");
        }

        return (true, null);
    }

    public (bool Allowed, string? Reason) CheckIp(string ip)
    {
        var now = DateTime.UtcNow;
        var ipSends = _ipSends.GetOrAdd(ip, _ => new List<DateTime>());
        lock (ipSends)
        {
            ipSends.RemoveAll(t => now - t > TimeSpan.FromHours(1));
            if (ipSends.Count >= MaxSendsPerIpPerHour)
                return (false, "Too many requests. Try again later.");
        }
        return (true, null);
    }

    public void RecordSend(string email, string deviceId, string ip)
    {
        var now = DateTime.UtcNow;

        var emailSends = _emailSends.GetOrAdd(email, _ => new List<DateTime>());
        lock (emailSends) { emailSends.Add(now); }

        var deviceSends = _deviceSends.GetOrAdd(deviceId, _ => new List<DateTime>());
        lock (deviceSends) { deviceSends.Add(now); }

        var ipSends = _ipSends.GetOrAdd(ip, _ => new List<DateTime>());
        lock (ipSends) { ipSends.Add(now); }
    }

    public bool IsLockedOut(string email)
    {
        var attempts = _failedAttempts.GetOrAdd(email, _ => new List<DateTime>());
        lock (attempts)
        {
            attempts.RemoveAll(t => DateTime.UtcNow - t > LockoutDuration);
            return attempts.Count >= MaxFailedAttempts;
        }
    }

    public void RecordFailedAttempt(string email)
    {
        var attempts = _failedAttempts.GetOrAdd(email, _ => new List<DateTime>());
        lock (attempts) { attempts.Add(DateTime.UtcNow); }
    }

    public int RemainingAttempts(string email)
    {
        var attempts = _failedAttempts.GetOrAdd(email, _ => new List<DateTime>());
        lock (attempts)
        {
            attempts.RemoveAll(t => DateTime.UtcNow - t > LockoutDuration);
            return Math.Max(0, MaxFailedAttempts - attempts.Count);
        }
    }

    public void ClearAttempts(string email)
    {
        _failedAttempts.TryRemove(email, out _);
    }

    private void PurgeStale()
    {
        PurgeDictionary(_emailSends, TimeSpan.FromHours(1));
        PurgeDictionary(_deviceSends, TimeSpan.FromDays(1));
        PurgeDictionary(_ipSends, TimeSpan.FromHours(1));
        PurgeDictionary(_failedAttempts, LockoutDuration);
    }

    private static void PurgeDictionary(ConcurrentDictionary<string, List<DateTime>> dict, TimeSpan maxAge)
    {
        var now = DateTime.UtcNow;
        foreach (var kvp in dict)
        {
            lock (kvp.Value)
            {
                kvp.Value.RemoveAll(t => now - t > maxAge);
                if (kvp.Value.Count == 0)
                    dict.TryRemove(kvp.Key, out _);
            }
        }
    }

    public void Dispose() => _cleanupTimer.Dispose();
}

// --- Email Template ---

static class EmailTemplate
{
    public static string Render(string code)
    {
        return $"""
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
        </head>
        <body style="margin:0;padding:0;background-color:#f4f4f5;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
            <table width="100%" cellpadding="0" cellspacing="0" style="background-color:#f4f4f5;padding:40px 20px;">
                <tr>
                    <td align="center">
                        <table width="100%" cellpadding="0" cellspacing="0" style="max-width:460px;background-color:#ffffff;border-radius:12px;overflow:hidden;">
                            <!-- Header -->
                            <tr>
                                <td style="background-color:#18181b;padding:24px 32px;text-align:center;">
                                    <span style="font-size:20px;font-weight:700;color:#ffffff;letter-spacing:1px;">SSDID</span>
                                </td>
                            </tr>
                            <!-- Body -->
                            <tr>
                                <td style="padding:32px;">
                                    <p style="margin:0 0 8px;font-size:18px;font-weight:600;color:#18181b;">
                                        Verify your email
                                    </p>
                                    <p style="margin:0 0 24px;font-size:14px;color:#71717a;line-height:1.5;">
                                        Enter this code in your SSDID Wallet to verify your email address.
                                    </p>
                                    <!-- Code -->
                                    <table width="100%" cellpadding="0" cellspacing="0">
                                        <tr>
                                            <td align="center" style="padding:16px 0;">
                                                <span style="display:inline-block;font-size:32px;font-weight:700;letter-spacing:8px;color:#18181b;background-color:#f4f4f5;padding:16px 32px;border-radius:8px;font-family:'Courier New',monospace;">
                                                    {code}
                                                </span>
                                            </td>
                                        </tr>
                                    </table>
                                    <p style="margin:24px 0 0;font-size:13px;color:#a1a1aa;line-height:1.5;">
                                        This code expires in <strong>10 minutes</strong>. If you didn't request this, you can safely ignore this email.
                                    </p>
                                </td>
                            </tr>
                            <!-- Footer -->
                            <tr>
                                <td style="padding:16px 32px;border-top:1px solid #e4e4e7;text-align:center;">
                                    <p style="margin:0;font-size:12px;color:#a1a1aa;">
                                        SSDID Wallet &mdash; Self-Sovereign Decentralized Identity
                                    </p>
                                </td>
                            </tr>
                        </table>
                    </td>
                </tr>
            </table>
        </body>
        </html>
        """;
    }
}
