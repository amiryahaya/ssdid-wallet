import SwiftUI
import UIKit
import SsdidCore

struct EmailVerificationScreen: View {
    @Environment(AppRouter.self) private var router

    let email: String
    let isEditing: Bool

    @State private var code = ""
    @State private var isVerifying = false
    @State private var isSending = false
    @State private var cooldown = 0
    @State private var errorMessage: String?
    @State private var hasSentInitialCode = false

    private nonisolated(unsafe) let emailApi = EmailApi(client: SsdidHttpClient())

    init(email: String, isEditing: Bool = false) {
        self.email = email
        self.isEditing = isEditing
    }

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack(spacing: 4) {
                Button { router.pop() } label: {
                    Image(systemName: "chevron.left")
                        .foregroundStyle(Color.textPrimary)
                        .font(.system(size: 20))
                }
                .padding(.leading, 8)

                Text("Verify Email")
                    .font(.ssdidHeadline)
                    .foregroundStyle(Color.textPrimary)

                Spacer()
            }
            .padding(.vertical, 12)
            .padding(.trailing, 20)

            Spacer().frame(height: 24)

            // Content
            VStack(spacing: 0) {
                Text("We sent a verification code to")
                    .font(.system(size: 14))
                    .foregroundStyle(Color.textSecondary)
                    .multilineTextAlignment(.center)

                Spacer().frame(height: 4)

                Text(email)
                    .font(.ssdidBody.weight(.semibold))
                    .foregroundStyle(Color.textPrimary)
                    .multilineTextAlignment(.center)

                Spacer().frame(height: 32)

                // OTP Input
                TextField("", text: $code, prompt: Text("000000").foregroundStyle(Color.textTertiary))
                    .textFieldStyle(.plain)
                    .font(.system(size: 24, weight: .bold, design: .monospaced))
                    .foregroundStyle(Color.textPrimary)
                    .multilineTextAlignment(.center)
                    .keyboardType(.numberPad)
                    .frame(width: 200)
                    .padding(14)
                    .background(Color.bgCard)
                    .cornerRadius(12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.ssdidBorder, lineWidth: 1)
                    )
                    .onChange(of: code) { _, newValue in
                        // Limit to 6 digits
                        let filtered = String(newValue.prefix(6).filter { $0.isNumber })
                        if filtered != newValue {
                            code = filtered
                        }
                    }

                Spacer().frame(height: 8)

                Text("Enter 6-digit code")
                    .font(.ssdidCaption)
                    .foregroundStyle(Color.textTertiary)

                if let errorMessage {
                    Spacer().frame(height: 12)
                    Text(errorMessage)
                        .font(.system(size: 13))
                        .foregroundStyle(Color.danger)
                        .multilineTextAlignment(.center)
                }

                Spacer().frame(height: 24)

                // Resend
                if cooldown > 0 {
                    Text("Resend code in \(cooldown)s")
                        .font(.system(size: 13))
                        .foregroundStyle(Color.textTertiary)
                } else {
                    Button {
                        sendCode()
                    } label: {
                        Text(isSending ? "Sending..." : "Resend Code")
                            .font(.system(size: 13))
                            .foregroundStyle(isSending ? Color.textTertiary : Color.ssdidAccent)
                    }
                    .disabled(isSending)
                }

                Spacer()
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 20)

            // Verify button
            VStack {
                Button {
                    verify()
                } label: {
                    if isVerifying {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                    } else {
                        Text("Verify")
                    }
                }
                .buttonStyle(.ssdidPrimary(enabled: code.count == 6 && !isVerifying))
                .disabled(code.count != 6 || isVerifying)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
        }
        .background(Color.bgPrimary)
        .onAppear {
            if !hasSentInitialCode {
                hasSentInitialCode = true
                sendCode()
            }
        }
    }

    private var deviceId: String {
        UIDevice.current.identifierForVendor?.uuidString ?? UUID().uuidString
    }

    private func verify() {
        isVerifying = true
        errorMessage = nil
        Task {
            do {
                let response = try await emailApi.confirmCode(
                    email: email,
                    code: code,
                    deviceId: deviceId
                )
                await MainActor.run {
                    isVerifying = false
                    if response.verified {
                        if isEditing {
                            // Pop back to settings (remove both email verification and profile edit)
                            router.pop()
                            router.pop()
                        } else {
                            router.push(.createIdentity())
                        }
                    } else {
                        errorMessage = "Verification failed. Please try again."
                    }
                }
            } catch let error as HttpError {
                await MainActor.run {
                    isVerifying = false
                    errorMessage = mapError(error)
                }
            } catch {
                await MainActor.run {
                    isVerifying = false
                    errorMessage = "Something went wrong. Please try again."
                }
            }
        }
    }

    private func sendCode() {
        isSending = true
        errorMessage = nil
        Task {
            do {
                _ = try await emailApi.sendCode(email: email, deviceId: deviceId)
                await MainActor.run {
                    isSending = false
                    startCooldown()
                }
            } catch let error as HttpError {
                await MainActor.run {
                    isSending = false
                    errorMessage = mapError(error)
                }
            } catch {
                await MainActor.run {
                    isSending = false
                    errorMessage = "Failed to send code. Please try again."
                }
            }
        }
    }

    private func mapError(_ error: HttpError) -> String {
        switch error {
        case .requestFailed(let statusCode, let message):
            if statusCode == 429 {
                return "Too many attempts. Please wait before trying again."
            }
            // Try to parse error JSON from server
            if let message, let data = message.data(using: .utf8),
               let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let errorMsg = json["error"] as? String {
                return errorMsg
            }
            return "Request failed (HTTP \(statusCode))."
        case .timeout:
            return "Request timed out. Check your connection."
        case .networkError:
            return "Network error. Check your connection."
        default:
            return "Something went wrong. Please try again."
        }
    }

    private func startCooldown() {
        cooldown = 60
        Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { timer in
            if cooldown > 0 {
                cooldown -= 1
            } else {
                timer.invalidate()
            }
        }
    }
}
