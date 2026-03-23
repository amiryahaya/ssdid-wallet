import SwiftUI
import UIKit

struct CreateIdentityScreen: View {
    @Environment(AppRouter.self) private var router
    @EnvironmentObject private var services: ServiceContainer

    let acceptedAlgorithms: String?

    // Wizard state
    @State private var currentStep = 1

    // Step 1: Profile
    @State private var displayName = ""
    @State private var email = ""
    @State private var isSendingCode = false

    // Step 2: Email Verification
    @State private var verificationCode = ""
    @State private var isVerifying = false
    @State private var cooldown = 0
    @State private var emailVerified = false

    // Step 3: Identity
    @State private var identityName = ""
    @State private var selectedAlgorithm: Algorithm = .KAZ_SIGN_192
    @State private var isCreating = false

    @State private var resendCount = 0

    @State private var errorMessage: String?

    private nonisolated(unsafe) let emailApi = EmailApi(client: SsdidHttpClient())

    init(acceptedAlgorithms: String? = nil) {
        self.acceptedAlgorithms = acceptedAlgorithms
    }

    // MARK: - Algorithm helpers

    private var availableAlgorithms: [Algorithm] {
        guard let accepted = acceptedAlgorithms, !accepted.isEmpty else {
            return Algorithm.allCases
        }
        // Parse JSON array of algorithm names
        guard let data = accepted.data(using: .utf8),
              let names = try? JSONDecoder().decode([String].self, from: data),
              !names.isEmpty else {
            return Algorithm.allCases
        }
        return Algorithm.allCases.filter { names.contains($0.rawValue) }
    }

    private struct AlgorithmGroup: Identifiable {
        let id: String
        let name: String
        let algorithms: [Algorithm]
    }

    private var algorithmGroups: [AlgorithmGroup] {
        let algos = availableAlgorithms
        var groups: [AlgorithmGroup] = []

        let classical = algos.filter { !$0.isPostQuantum }
        if !classical.isEmpty {
            groups.append(AlgorithmGroup(id: "classical", name: "CLASSICAL", algorithms: classical))
        }

        let kazSign = algos.filter { $0.isKazSign }
        if !kazSign.isEmpty {
            groups.append(AlgorithmGroup(id: "kaz", name: "KAZ-SIGN (PQC)", algorithms: kazSign))
        }

        let mlDsa = algos.filter { $0.isMlDsa }
        if !mlDsa.isEmpty {
            groups.append(AlgorithmGroup(id: "mldsa", name: "ML-DSA (PQC)", algorithms: mlDsa))
        }

        let slhDsa = algos.filter { $0.isSlhDsa }
        if !slhDsa.isEmpty {
            groups.append(AlgorithmGroup(id: "slhdsa", name: "SLH-DSA (PQC)", algorithms: slhDsa))
        }

        return groups
    }

    // MARK: - Validation

    private var isStep1Valid: Bool {
        let trimmedName = displayName.trimmingCharacters(in: .whitespaces)
        let trimmedEmail = email.trimmingCharacters(in: .whitespaces)
        let emailParts = trimmedEmail.split(separator: "@")
        let emailOk = emailParts.count == 2 && !emailParts[0].isEmpty && emailParts[1].contains(".") && emailParts[1].last != "."
        return !trimmedName.isEmpty && !trimmedEmail.isEmpty && emailOk
    }

    private var deviceId: String {
        UIDevice.current.identifierForVendor?.uuidString ?? UUID().uuidString
    }

    // MARK: - Body

    var body: some View {
        VStack(spacing: 0) {
            // Header with back + step indicator
            HStack(spacing: 4) {
                Button {
                    if currentStep > 1 {
                        withAnimation { currentStep -= 1 }
                        errorMessage = nil
                    } else {
                        router.pop()
                    }
                } label: {
                    Image(systemName: "chevron.left")
                        .foregroundStyle(Color.textPrimary)
                        .font(.system(size: 20))
                }
                .padding(.leading, 8)

                Text("Create Identity")
                    .font(.ssdidHeadline)
                    .foregroundStyle(Color.textPrimary)

                Spacer()

                Text("Step \(currentStep) of 3")
                    .font(.system(size: 13))
                    .foregroundStyle(Color.textTertiary)
                    .padding(.trailing, 4)
            }
            .padding(.vertical, 12)
            .padding(.trailing, 20)

            // Step indicator bar
            HStack(spacing: 4) {
                ForEach(1...3, id: \.self) { step in
                    RoundedRectangle(cornerRadius: 2)
                        .fill(step <= currentStep ? Color.ssdidAccent : Color.ssdidBorder)
                        .frame(height: 3)
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 8)

            // Error banner
            if let errorMessage {
                Text(errorMessage)
                    .font(.system(size: 13))
                    .foregroundStyle(Color.danger)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 4)
            }

            // Step content
            switch currentStep {
            case 1: step1ProfileView
            case 2: step2VerificationView
            case 3: step3AlgorithmView
            default: EmptyView()
            }
        }
        .background(Color.bgPrimary)
    }

    // MARK: - Step 1: Profile

    private var step1ProfileView: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Set up your profile for this identity. This information can be shared when you sign in to services.")
                        .font(.system(size: 14))
                        .foregroundStyle(Color.textSecondary)
                        .padding(.bottom, 8)

                    Text("DISPLAY NAME")
                        .font(.ssdidCaption)
                        .foregroundStyle(Color.textTertiary)

                    TextField("", text: $displayName, prompt: Text("Your full name").foregroundStyle(Color.textTertiary))
                        .textFieldStyle(.plain)
                        .font(.ssdidBody)
                        .foregroundStyle(Color.textPrimary)
                        .padding(14)
                        .background(Color.bgCard)
                        .cornerRadius(12)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.ssdidBorder, lineWidth: 1))

                    Spacer().frame(height: 12)

                    Text("EMAIL")
                        .font(.ssdidCaption)
                        .foregroundStyle(Color.textTertiary)

                    TextField("", text: $email, prompt: Text("your@email.com").foregroundStyle(Color.textTertiary))
                        .textFieldStyle(.plain)
                        .font(.ssdidBody)
                        .foregroundStyle(Color.textPrimary)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .padding(14)
                        .background(Color.bgCard)
                        .cornerRadius(12)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.ssdidBorder, lineWidth: 1))
                        .onChange(of: email) { _, _ in
                            emailVerified = false
                        }
                }
                .padding(.horizontal, 20)
            }

            // Verify Email button
            Button {
                sendVerificationCode()
            } label: {
                if isSendingCode {
                    HStack(spacing: 10) {
                        ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                        Text("Sending...")
                    }
                } else {
                    Text("Verify Email")
                }
            }
            .buttonStyle(.ssdidPrimary(enabled: isStep1Valid && !isSendingCode))
            .disabled(!isStep1Valid || isSendingCode)
            .padding(20)
        }
    }

    // MARK: - Step 2: Email Verification

    private var step2VerificationView: some View {
        VStack(spacing: 0) {
            Spacer().frame(height: 24)

            VStack(spacing: 4) {
                Text("We sent a verification code to")
                    .font(.system(size: 14))
                    .foregroundStyle(Color.textSecondary)
                Text(email)
                    .font(.ssdidBody.weight(.semibold))
                    .foregroundStyle(Color.textPrimary)
            }

            Spacer().frame(height: 32)

            // Code input
            TextField("", text: $verificationCode, prompt: Text("000000").foregroundStyle(Color.textTertiary))
                .textFieldStyle(.plain)
                .font(.system(size: 24, weight: .semibold, design: .monospaced))
                .foregroundStyle(Color.textPrimary)
                .multilineTextAlignment(.center)
                .keyboardType(.numberPad)
                .padding(14)
                .background(Color.bgCard)
                .cornerRadius(12)
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.ssdidBorder, lineWidth: 1))
                .padding(.horizontal, 40)
                .onChange(of: verificationCode) { _, newValue in
                    let filtered = String(newValue.prefix(6).filter { $0.isNumber })
                    if filtered != newValue {
                        verificationCode = filtered
                    }
                }

            Spacer().frame(height: 16)

            // Resend button
            if cooldown > 0 {
                Text("Resend code in \(cooldown)s")
                    .font(.system(size: 14))
                    .foregroundStyle(Color.textTertiary)
            } else {
                Button {
                    sendVerificationCode()
                } label: {
                    Text(isSendingCode ? "Sending..." : "Resend Code")
                        .foregroundStyle(isSendingCode ? Color.textTertiary : Color.ssdidAccent)
                }
                .disabled(isSendingCode)
                .font(.system(size: 14))
            }

            Spacer()

            // Verify button
            Button {
                verifyCode()
            } label: {
                if isVerifying {
                    HStack(spacing: 10) {
                        ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                        Text("Verifying...")
                    }
                } else {
                    Text("Verify")
                }
            }
            .buttonStyle(.ssdidPrimary(enabled: verificationCode.count == 6 && !isVerifying))
            .disabled(verificationCode.count != 6 || isVerifying)
            .padding(20)
        }
    }

    // MARK: - Step 3: Algorithm Selection + Create

    private var step3AlgorithmView: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("IDENTITY NAME")
                        .font(.ssdidCaption)
                        .foregroundStyle(Color.textTertiary)

                    TextField("", text: $identityName, prompt: Text("e.g. Personal, Work").foregroundStyle(Color.textTertiary))
                        .textFieldStyle(.plain)
                        .font(.ssdidBody)
                        .foregroundStyle(Color.textPrimary)
                        .padding(14)
                        .background(Color.bgCard)
                        .cornerRadius(12)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.ssdidBorder, lineWidth: 1))

                    Spacer().frame(height: 4)

                    Text("SIGNATURE ALGORITHM")
                        .font(.ssdidCaption)
                        .foregroundStyle(Color.textTertiary)

                    ForEach(algorithmGroups) { group in
                        Text(group.name)
                            .font(.ssdidCaption)
                            .foregroundStyle(group.id == "classical" ? Color.classical : Color.pqc)
                            .padding(.top, 4)

                        ForEach(group.algorithms, id: \.rawValue) { algo in
                            algorithmRow(algo)
                        }
                    }
                }
                .padding(.horizontal, 20)
            }

            Button {
                createIdentity()
            } label: {
                if isCreating {
                    HStack(spacing: 10) {
                        ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                        Text("Creating...")
                    }
                } else {
                    Text("Create Identity")
                }
            }
            .buttonStyle(.ssdidPrimary(enabled: !identityName.trimmingCharacters(in: .whitespaces).isEmpty && !isCreating))
            .disabled(identityName.trimmingCharacters(in: .whitespaces).isEmpty || isCreating)
            .padding(20)
        }
    }

    // MARK: - Algorithm Row

    @ViewBuilder
    private func algorithmRow(_ algo: Algorithm) -> some View {
        let isSelected = selectedAlgorithm == algo
        Button {
            selectedAlgorithm = algo
        } label: {
            HStack(spacing: 8) {
                Image(systemName: isSelected ? "largecircle.fill.circle" : "circle")
                    .foregroundStyle(isSelected ? Color.ssdidAccent : Color.textTertiary)
                    .font(.system(size: 20))

                VStack(alignment: .leading, spacing: 2) {
                    Text(algo.rawValue.replacingOccurrences(of: "_", with: " "))
                        .font(.ssdidBody.weight(.medium))
                        .foregroundStyle(Color.textPrimary)
                    Text(algo.w3cType)
                        .font(.system(size: 11))
                        .foregroundStyle(Color.textTertiary)
                }

                Spacer()

                AlgorithmBadge(
                    name: algo.isPostQuantum ? "PQC" : "Classical",
                    isPostQuantum: algo.isPostQuantum
                )
            }
            .padding(14)
            .background(isSelected ? Color.accentDim : Color.bgCard)
            .cornerRadius(12)
        }
    }

    // MARK: - Actions

    private func sendVerificationCode() {
        #if DEBUG
        // Skip OTP for UI testing — go directly to step 3
        if ProcessInfo.processInfo.arguments.contains("--skip-otp") {
            emailVerified = true
            withAnimation { currentStep = 3 }
            return
        }
        #endif

        isSendingCode = true
        errorMessage = nil
        let trimmedEmail = email.trimmingCharacters(in: .whitespaces)
        Task {
            do {
                _ = try await emailApi.sendCode(email: trimmedEmail, deviceId: deviceId)
                await MainActor.run {
                    isSendingCode = false
                    if currentStep == 1 {
                        withAnimation { currentStep = 2 }
                    }
                    resendCount += 1
                    let seconds: Int
                    switch resendCount {
                    case 1: seconds = 60
                    case 2: seconds = 120
                    default: seconds = 300
                    }
                    startCooldown(seconds)
                }
            } catch let error as HttpError {
                await MainActor.run {
                    isSendingCode = false
                    errorMessage = mapError(error)
                }
            } catch {
                await MainActor.run {
                    isSendingCode = false
                    errorMessage = "Failed to send code. Please try again."
                }
            }
        }
    }

    private func verifyCode() {
        isVerifying = true
        errorMessage = nil
        let trimmedEmail = email.trimmingCharacters(in: .whitespaces)
        Task {
            do {
                let response = try await emailApi.confirmCode(
                    email: trimmedEmail,
                    code: verificationCode,
                    deviceId: deviceId
                )
                await MainActor.run {
                    isVerifying = false
                    if response.verified {
                        emailVerified = true
                        HapticManager.notification(.success)
                        withAnimation { currentStep = 3 }
                    } else {
                        HapticManager.notification(.error)
                        errorMessage = "Verification failed. Please try again."
                    }
                }
            } catch let error as HttpError {
                await MainActor.run {
                    isVerifying = false
                    HapticManager.notification(.error)
                    errorMessage = mapError(error)
                }
            } catch {
                await MainActor.run {
                    isVerifying = false
                    HapticManager.notification(.error)
                    errorMessage = "Invalid verification code. Please try again."
                }
            }
        }
    }

    private func createIdentity() {
        let trimmedName = identityName.trimmingCharacters(in: .whitespaces)
        guard !trimmedName.isEmpty else { return }
        isCreating = true
        errorMessage = nil

        Task {
            do {
                let identity = try await services.ssdidClient.initIdentity(
                    name: trimmedName,
                    algorithm: selectedAlgorithm
                )
                let trimmedDisplayName = displayName.trimmingCharacters(in: .whitespaces)
                let trimmedEmail = email.trimmingCharacters(in: .whitespaces)
                if !trimmedDisplayName.isEmpty || !trimmedEmail.isEmpty {
                    try? await services.vault.updateIdentityProfile(
                        keyId: identity.keyId,
                        profileName: trimmedDisplayName.isEmpty ? nil : trimmedDisplayName,
                        email: trimmedEmail.isEmpty ? nil : trimmedEmail,
                        emailVerified: emailVerified
                    )
                }
                isCreating = false
                HapticManager.notification(.success)
                router.push(.biometricSetup)
            } catch {
                isCreating = false
                HapticManager.notification(.error)
                errorMessage = error.localizedDescription
            }
        }
    }

    // MARK: - Helpers

    private func mapError(_ error: HttpError) -> String {
        switch error {
        case .requestFailed(let statusCode, let message):
            if statusCode == 429 {
                return "Too many attempts. Please wait before trying again."
            }
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

    private func startCooldown(_ seconds: Int) {
        cooldown = seconds
        Task { @MainActor in
            while cooldown > 0 {
                try? await Task.sleep(for: .seconds(1))
                cooldown -= 1
            }
        }
    }
}
