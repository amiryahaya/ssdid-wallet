import SwiftUI

/// Adapts any VaultStorage to the SdJwtVcStore protocol required by OpenId4VpHandler.
private struct VaultStorageSdJwtVcAdapter: SdJwtVcStore {
    let storage: VaultStorage
    func listSdJwtVcs() async -> [StoredSdJwtVc] {
        await storage.listSdJwtVcs()
    }
}

struct PresentationRequestScreen: View {
    @Environment(AppRouter.self) private var router
    @EnvironmentObject private var services: ServiceContainer

    let rawUri: String

    @State private var viewModel: PresentationRequestViewModel?

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

                Text("Share Credentials")
                    .font(.ssdidHeadline)
                    .foregroundStyle(Color.textPrimary)

                Spacer()
            }
            .padding(.vertical, 12)
            .padding(.trailing, 20)

            if let viewModel {
                stateContent(viewModel)
            } else {
                loadingView(message: "Initializing...")
            }
        }
        .background(Color.bgPrimary)
        .onAppear {
            if viewModel == nil {
                let transport = OpenId4VpTransport()
                let vcStore = VaultStorageSdJwtVcAdapter(storage: services.storage)
                let handler = OpenId4VpHandler(transport: transport, vcStore: vcStore)
                let vm = PresentationRequestViewModel(handler: handler, vault: services.vault)
                viewModel = vm
                vm.processRequest(rawUri: rawUri)
            }
        }
    }

    @ViewBuilder
    private func stateContent(_ vm: PresentationRequestViewModel) -> some View {
        switch vm.state {
        case .loading:
            loadingView(message: "Processing request...")

        case .credentialMatch(let verifierName, let claims, _, _):
            credentialMatchContent(verifierName: verifierName, claims: claims, vm: vm)

        case .submitting:
            loadingView(message: "Sharing credentials...")

        case .success:
            successView()

        case .error(let message):
            errorView(message: message)
        }
    }

    // MARK: - Loading

    @ViewBuilder
    private func loadingView(message: String) -> some View {
        Spacer()
        VStack(spacing: 16) {
            ProgressView()
                .progressViewStyle(CircularProgressViewStyle(tint: Color.ssdidAccent))
            Text(message)
                .font(.system(size: 14))
                .foregroundStyle(Color.textSecondary)
        }
        Spacer()
    }

    // MARK: - Credential Match

    @ViewBuilder
    private func credentialMatchContent(
        verifierName: String,
        claims: [PresentationRequestViewModel.ClaimItem],
        vm: PresentationRequestViewModel
    ) -> some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                // Verifier info
                VStack(alignment: .leading, spacing: 0) {
                    Text("VERIFIER")
                        .font(.ssdidCaption)
                        .foregroundStyle(Color.textTertiary)
                    Spacer().frame(height: 8)
                    HStack(spacing: 8) {
                        Image(systemName: "building.2")
                            .foregroundStyle(Color.ssdidAccent)
                            .font(.system(size: 16))
                        Text(verifierName)
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(Color.textPrimary)
                            .lineLimit(1)
                    }
                }
                .ssdidCard()

                // Claims section
                VStack(alignment: .leading, spacing: 0) {
                    Text("REQUESTED CLAIMS")
                        .font(.ssdidCaption)
                        .foregroundStyle(Color.textTertiary)
                    Spacer().frame(height: 8)

                    ForEach(claims) { claim in
                        claimRow(claim: claim, vm: vm)
                    }
                }
                .ssdidCard()
            }
            .padding(.horizontal, 20)
        }

        // Action buttons
        HStack(spacing: 12) {
            Button { vm.decline() } label: { Text("Decline") }
                .buttonStyle(.ssdidDanger)

            Button { vm.approve() } label: { Text("Share") }
                .buttonStyle(.ssdidPrimary)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
    }

    @ViewBuilder
    private func claimRow(
        claim: PresentationRequestViewModel.ClaimItem,
        vm: PresentationRequestViewModel
    ) -> some View {
        Button {
            if !claim.required {
                vm.toggleClaim(name: claim.name)
            }
        } label: {
            HStack(spacing: 10) {
                Image(systemName: claim.selected ? "checkmark.square.fill" : "square")
                    .foregroundStyle(claim.required ? Color.textTertiary : Color.ssdidAccent)
                    .font(.system(size: 18))

                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 4) {
                        Text(claim.name)
                            .font(.system(size: 13, weight: .medium))
                            .foregroundStyle(Color.textPrimary)
                        if claim.required {
                            Text("Required")
                                .font(.system(size: 10))
                                .foregroundStyle(Color.warning)
                        } else {
                            Text("Optional")
                                .font(.system(size: 10))
                                .foregroundStyle(Color.textTertiary)
                        }
                    }
                    if !claim.value.isEmpty {
                        Text(claim.value)
                            .font(.system(size: 12))
                            .foregroundStyle(Color.textSecondary)
                            .lineLimit(1)
                    }
                }

                Spacer()
            }
            .padding(.vertical, 6)
        }
        .disabled(claim.required)
    }

    // MARK: - Success

    @ViewBuilder
    private func successView() -> some View {
        Spacer()
        VStack(spacing: 0) {
            ZStack {
                RoundedRectangle(cornerRadius: 20)
                    .fill(Color.successDim)
                    .frame(width: 72, height: 72)
                Image(systemName: "checkmark")
                    .font(.system(size: 32, weight: .bold))
                    .foregroundStyle(Color.success)
            }
            Spacer().frame(height: 20)
            Text("Credentials Shared")
                .font(.ssdidHeadline)
                .foregroundStyle(Color.textPrimary)
            Spacer().frame(height: 8)
            Text("Your credentials have been shared with the verifier.")
                .font(.system(size: 14))
                .foregroundStyle(Color.textSecondary)
                .multilineTextAlignment(.center)
        }
        .padding(.horizontal, 32)
        Spacer()
        Button {
            HapticManager.notification(.success)
            router.pop()
        } label: { Text("Done") }
            .buttonStyle(.ssdidPrimary)
            .padding(20)
    }

    // MARK: - Error

    @ViewBuilder
    private func errorView(message: String) -> some View {
        Spacer()
        VStack(spacing: 0) {
            ZStack {
                RoundedRectangle(cornerRadius: 20)
                    .fill(Color.dangerDim)
                    .frame(width: 72, height: 72)
                Image(systemName: "xmark")
                    .font(.system(size: 32, weight: .bold))
                    .foregroundStyle(Color.danger)
            }
            Spacer().frame(height: 20)
            Text("Request Failed")
                .font(.ssdidHeadline)
                .foregroundStyle(Color.textPrimary)
            Spacer().frame(height: 8)
            Text(message)
                .font(.system(size: 14))
                .foregroundStyle(Color.textSecondary)
                .multilineTextAlignment(.center)
        }
        .padding(.horizontal, 32)
        Spacer()
        Button { router.pop() } label: { Text("Go Back") }
            .buttonStyle(.ssdidDanger)
            .padding(20)
    }
}
