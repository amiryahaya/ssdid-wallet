import SwiftUI

struct TxSigningScreen: View {
    @Environment(AppRouter.self) private var router

    let serverUrl: String
    let sessionToken: String

    enum TxState {
        case idle
        case loading
        case confirmed
        case failed(String)
    }

    @State private var state: TxState = .idle
    @State private var timerSeconds = 120
    @State private var txDetails: [(key: String, value: String)] = []
    @State private var timer: Timer?

    private var timerColor: Color {
        if timerSeconds > 30 { return .success }
        if timerSeconds > 10 { return .warning }
        return .danger
    }

    private var timerBgColor: Color {
        if timerSeconds > 30 { return .successDim }
        if timerSeconds > 10 { return .warningDim }
        return .dangerDim
    }

    private var timerText: String {
        let m = timerSeconds / 60
        let s = timerSeconds % 60
        return "\(m):\(String(format: "%02d", s))"
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

                Text("Sign Transaction")
                    .font(.ssdidHeadline)
                    .foregroundStyle(Color.textPrimary)

                Spacer()
            }
            .padding(.vertical, 12)
            .padding(.trailing, 20)

            switch state {
            case .idle, .loading:
                let isLoading = { if case .loading = state { return true }; return false }()

                ScrollView {
                    LazyVStack(spacing: 12) {
                        // Transaction details card
                        VStack(alignment: .leading, spacing: 0) {
                            Text("TRANSACTION DETAILS")
                                .font(.ssdidCaption)
                                .foregroundStyle(Color.textTertiary)
                                .padding(.bottom, 14)

                            if txDetails.isEmpty {
                                Text("Loading transaction details...")
                                    .font(.system(size: 13))
                                    .foregroundStyle(Color.textSecondary)
                            } else {
                                ForEach(Array(txDetails.enumerated()), id: \.offset) { index, detail in
                                    let isAmount = detail.key.lowercased() == "amount"
                                    let isDid = detail.value.hasPrefix("did:")

                                    txDetailRow(
                                        label: detail.key.prefix(1).uppercased() + detail.key.dropFirst(),
                                        value: detail.value,
                                        highlight: isAmount,
                                        mono: isDid
                                    )
                                    if index < txDetails.count - 1 {
                                        Divider()
                                            .background(Color.ssdidBorder)
                                            .padding(.vertical, 8)
                                    }
                                }
                                Divider()
                                    .background(Color.ssdidBorder)
                                    .padding(.vertical, 8)
                                txDetailRow(label: "Server", value: serverUrl, mono: true)
                            }
                        }
                        .ssdidCard()

                        // Security card
                        VStack(alignment: .leading, spacing: 0) {
                            Text("SECURITY")
                                .font(.ssdidCaption)
                                .foregroundStyle(Color.textTertiary)
                                .padding(.bottom, 10)

                            HStack {
                                Text("Challenge expires in")
                                    .font(.system(size: 13))
                                    .foregroundStyle(Color.textSecondary)
                                Spacer()
                                Text(timerText)
                                    .font(.system(size: 13, weight: .bold, design: .monospaced))
                                    .foregroundStyle(timerColor)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 4)
                                    .background(timerBgColor)
                                    .cornerRadius(6)
                            }

                            Spacer().frame(height: 10)

                            securityBullet("Transaction bound to challenge hash", color: .success)
                            Spacer().frame(height: 4)
                            securityBullet("SHA3-256 integrity verification", color: .success)
                            Spacer().frame(height: 4)
                            securityBullet("Post-quantum signature (if available)", color: .pqc)
                        }
                        .ssdidCard()

                        // Biometric card
                        HStack(spacing: 12) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 10)
                                    .fill(Color.warningDim)
                                    .frame(width: 36, height: 36)
                                Image(systemName: "lock.open")
                                    .font(.system(size: 18))
                                    .foregroundStyle(Color.warning)
                            }
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Biometric Required")
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundStyle(Color.textPrimary)
                                Text("Touch sensor to authorize signing")
                                    .font(.system(size: 12))
                                    .foregroundStyle(Color.textTertiary)
                            }
                        }
                        .ssdidCard()
                    }
                    .padding(.horizontal, 20)
                }

                // Sign button
                Button {
                    signTransaction()
                } label: {
                    if isLoading {
                        HStack(spacing: 10) {
                            ProgressView().progressViewStyle(CircularProgressViewStyle(tint: Color.bgPrimary))
                            Text("Signing...")
                                .foregroundStyle(Color.bgPrimary)
                        }
                    } else {
                        Text("Sign Transaction")
                            .foregroundStyle(Color.bgPrimary)
                    }
                }
                .font(.ssdidBody.weight(.semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(!isLoading && timerSeconds > 0 ? Color.warning : Color.bgElevated)
                .cornerRadius(12)
                .disabled(isLoading || timerSeconds <= 0)
                .padding(20)

            case .confirmed:
                Spacer()
                statusView(
                    success: true,
                    title: "Transaction Confirmed",
                    message: "Your transaction has been signed and submitted."
                )
                Spacer()
                Button { router.pop() } label: { Text("Done") }
                    .buttonStyle(.ssdidPrimary)
                    .padding(20)

            case .failed(let message):
                Spacer()
                statusView(
                    success: false,
                    title: "Transaction Failed",
                    message: message
                )
                Spacer()
                Button { router.pop() } label: { Text("Go Back") }
                    .buttonStyle(.ssdidDanger)
                    .padding(20)
            }
        }
        .background(Color.bgPrimary)
        .onAppear {
            loadTransactionDetails()
            startTimer()
        }
        .onDisappear {
            timer?.invalidate()
        }
    }

    @ViewBuilder
    private func txDetailRow(label: String, value: String, highlight: Bool = false, mono: Bool = false) -> some View {
        HStack {
            Text(label)
                .font(.system(size: 13))
                .foregroundStyle(Color.textSecondary)
            Spacer()
            Text(value)
                .font(.system(size: highlight ? 16 : 13, weight: highlight ? .bold : .regular, design: mono ? .monospaced : .default))
                .foregroundStyle(highlight ? Color.warning : Color.textPrimary)
                .lineLimit(1)
        }
    }

    @ViewBuilder
    private func securityBullet(_ text: String, color: Color) -> some View {
        HStack(alignment: .center, spacing: 8) {
            Circle()
                .fill(color)
                .frame(width: 6, height: 6)
            Text(text)
                .font(.system(size: 12))
                .foregroundStyle(Color.textTertiary)
        }
    }

    @ViewBuilder
    private func statusView(success: Bool, title: String, message: String) -> some View {
        VStack(spacing: 0) {
            ZStack {
                RoundedRectangle(cornerRadius: 20)
                    .fill(success ? Color.successDim : Color.dangerDim)
                    .frame(width: 72, height: 72)
                Image(systemName: success ? "checkmark" : "xmark")
                    .font(.system(size: 32, weight: .bold))
                    .foregroundStyle(success ? Color.success : Color.danger)
            }
            Spacer().frame(height: 20)
            Text(title)
                .font(.ssdidHeadline)
                .foregroundStyle(Color.textPrimary)
            Spacer().frame(height: 8)
            Text(message)
                .font(.system(size: 14))
                .foregroundStyle(Color.textSecondary)
                .multilineTextAlignment(.center)
        }
        .padding(.horizontal, 32)
    }

    private func loadTransactionDetails() {
        // Mock transaction details
        txDetails = [
            (key: "type", value: "Transfer"),
            (key: "amount", value: "1,000.00 MYR"),
            (key: "recipient", value: "did:ssdid:abc123def456"),
            (key: "reference", value: "INV-2024-001")
        ]
    }

    private func startTimer() {
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            if timerSeconds > 0 {
                timerSeconds -= 1
            } else {
                timer?.invalidate()
                if case .idle = state {
                    state = .failed("Challenge expired. Please scan QR again.")
                }
            }
        }
    }

    private func signTransaction() {
        state = .loading
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            state = .confirmed
            timer?.invalidate()
        }
    }
}
