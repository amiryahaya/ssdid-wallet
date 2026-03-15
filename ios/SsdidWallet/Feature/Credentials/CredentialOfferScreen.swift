import SwiftUI

struct CredentialOfferScreen: View {
    @Environment(AppRouter.self) private var router
    @EnvironmentObject private var services: ServiceContainer

    let issuerUrl: String
    let offerId: String

    @State private var viewModel: CredentialOfferViewModel?
    @State private var pinText = ""

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

                Text("Credential Offer")
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
                let metadataResolver = IssuerMetadataResolver()
                let tokenClient = TokenClient()
                let nonceManager = NonceManager()
                let transport = OpenId4VciTransport()
                let handler = OpenId4VciHandler(
                    metadataResolver: metadataResolver,
                    tokenClient: tokenClient,
                    nonceManager: nonceManager,
                    transport: transport,
                    vcStorage: services.storage
                )
                let vm = CredentialOfferViewModel(handler: handler, vault: services.vault)
                viewModel = vm

                if !issuerUrl.isEmpty {
                    let offerUri = buildOfferUri(issuerUrl: issuerUrl, offerId: offerId)
                    vm.processOffer(rawUri: offerUri)
                } else if !offerId.isEmpty {
                    // offerId may contain the full offer data from a deep link
                    vm.processOfferJson(json: offerId)
                }
            }
        }
    }

    // MARK: - State Content

    @ViewBuilder
    private func stateContent(_ vm: CredentialOfferViewModel) -> some View {
        switch vm.state {
        case .loading:
            loadingView(message: "Fetching offer details...")

        case .reviewingOffer(let data):
            reviewContent(data: data, vm: vm)

        case .pinEntry(let pinData):
            pinEntryContent(pinData: pinData, vm: vm)

        case .processing:
            loadingView(message: "Accepting credential...")

        case .success:
            successView()

        case .deferred(let transactionId):
            deferredView(transactionId: transactionId)

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

    // MARK: - Reviewing Offer

    @ViewBuilder
    private func reviewContent(data: CredentialOfferViewModel.ReviewData, vm: CredentialOfferViewModel) -> some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                // Offer details card
                VStack(alignment: .leading, spacing: 0) {
                    Text("OFFER DETAILS")
                        .font(.ssdidCaption)
                        .foregroundStyle(Color.textTertiary)
                    Spacer().frame(height: 8)
                    Text("Issuer")
                        .font(.system(size: 11))
                        .foregroundStyle(Color.textTertiary)
                    Text(data.issuerName)
                        .font(.ssdidMono)
                        .foregroundStyle(Color.textPrimary)
                        .lineLimit(1)
                    Spacer().frame(height: 6)
                    Text("Credential Type")
                        .font(.system(size: 11))
                        .foregroundStyle(Color.textTertiary)

                    ForEach(data.credentialTypes, id: \.self) { type in
                        let isSelected = type == data.selectedConfigId
                        Button {
                            vm.selectConfigId(type)
                        } label: {
                            HStack(spacing: 6) {
                                if data.credentialTypes.count > 1 {
                                    Image(systemName: isSelected ? "largecircle.fill.circle" : "circle")
                                        .foregroundStyle(isSelected ? Color.ssdidAccent : Color.textTertiary)
                                        .font(.system(size: 14))
                                }
                                Text(type)
                                    .font(.system(size: 13))
                                    .foregroundStyle(isSelected ? Color.ssdidAccent : Color.textPrimary)
                            }
                        }
                    }

                    if data.requiresPin {
                        Spacer().frame(height: 6)
                        HStack(spacing: 4) {
                            Image(systemName: "lock.fill")
                                .font(.system(size: 10))
                                .foregroundStyle(Color.warning)
                            Text("PIN required")
                                .font(.system(size: 11))
                                .foregroundStyle(Color.warning)
                        }
                    }
                }
                .ssdidCard()

                // Identity selector
                Text("SELECT IDENTITY")
                    .font(.ssdidCaption)
                    .foregroundStyle(Color.textTertiary)

                ForEach(data.identities) { identity in
                    let isSelected = data.selectedIdentity?.keyId == identity.keyId
                    Button { vm.selectIdentity(identity) } label: {
                        HStack(spacing: 8) {
                            Image(systemName: isSelected ? "largecircle.fill.circle" : "circle")
                                .foregroundStyle(isSelected ? Color.ssdidAccent : Color.textTertiary)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(identity.name)
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundStyle(Color.textPrimary)
                                Text(identity.did)
                                    .font(.system(size: 11, design: .monospaced))
                                    .foregroundStyle(Color.textTertiary)
                                    .lineLimit(1)
                            }
                            Spacer()
                        }
                        .padding(14)
                        .background(isSelected ? Color.accentDim : Color.bgCard)
                        .cornerRadius(12)
                    }
                }

                if data.identities.isEmpty {
                    Text("No identities available")
                        .font(.ssdidBody)
                        .foregroundStyle(Color.textSecondary)
                        .frame(maxWidth: .infinity)
                        .padding(24)
                        .background(Color.bgCard)
                        .cornerRadius(12)
                }
            }
            .padding(.horizontal, 20)
        }

        // Accept/Reject buttons
        HStack(spacing: 12) {
            Button { vm.decline() } label: { Text("Reject") }
                .buttonStyle(.ssdidDanger)

            Button { vm.acceptOffer() } label: { Text("Accept") }
                .buttonStyle(.ssdidPrimary(enabled: data.selectedIdentity != nil))
                .disabled(data.selectedIdentity == nil)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
    }

    // MARK: - PIN Entry

    @ViewBuilder
    private func pinEntryContent(pinData: CredentialOfferViewModel.PinData, vm: CredentialOfferViewModel) -> some View {
        Spacer()
        VStack(spacing: 20) {
            Image(systemName: "lock.shield")
                .font(.system(size: 48))
                .foregroundStyle(Color.ssdidAccent)

            Text("Enter PIN")
                .font(.ssdidHeadline)
                .foregroundStyle(Color.textPrimary)

            if let description = pinData.description {
                Text(description)
                    .font(.system(size: 14))
                    .foregroundStyle(Color.textSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
            }

            TextField("PIN", text: $pinText)
                .textFieldStyle(.roundedBorder)
                .keyboardType(pinData.inputMode == "numeric" ? .numberPad : .default)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 200)
                .font(.system(size: 24, weight: .medium, design: .monospaced))

            if pinData.length > 0 {
                Text("\(pinData.length) characters required")
                    .font(.system(size: 12))
                    .foregroundStyle(Color.textTertiary)
            }
        }
        Spacer()

        HStack(spacing: 12) {
            Button {
                pinText = ""
                // Go back to reviewing
                vm.processOffer(rawUri: buildOfferUri(issuerUrl: issuerUrl, offerId: offerId))
            } label: { Text("Cancel") }
                .buttonStyle(.ssdidDanger)

            Button { vm.submitPin(pinText) } label: { Text("Submit") }
                .buttonStyle(.ssdidPrimary(enabled: !pinText.isEmpty))
                .disabled(pinText.isEmpty)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
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
            Text("Credential Accepted")
                .font(.ssdidHeadline)
                .foregroundStyle(Color.textPrimary)
            Spacer().frame(height: 8)
            Text("The credential has been stored in your wallet.")
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

    // MARK: - Deferred

    @ViewBuilder
    private func deferredView(transactionId: String) -> some View {
        Spacer()
        VStack(spacing: 0) {
            ZStack {
                RoundedRectangle(cornerRadius: 20)
                    .fill(Color.accentDim)
                    .frame(width: 72, height: 72)
                Image(systemName: "clock")
                    .font(.system(size: 32, weight: .bold))
                    .foregroundStyle(Color.ssdidAccent)
            }
            Spacer().frame(height: 20)
            Text("Credential Pending")
                .font(.ssdidHeadline)
                .foregroundStyle(Color.textPrimary)
            Spacer().frame(height: 8)
            Text("Your credential request has been submitted. You will be notified when it is ready.")
                .font(.system(size: 14))
                .foregroundStyle(Color.textSecondary)
                .multilineTextAlignment(.center)
            Spacer().frame(height: 12)
            Text("Transaction: \(transactionId)")
                .font(.system(size: 11, design: .monospaced))
                .foregroundStyle(Color.textTertiary)
                .lineLimit(1)
        }
        .padding(.horizontal, 32)
        Spacer()
        Button { router.pop() } label: { Text("Done") }
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
            Text("Offer Failed")
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

    // MARK: - Helpers

    private func buildOfferUri(issuerUrl: String, offerId: String) -> String {
        let offerJson = """
        {"credential_issuer":"\(issuerUrl)","credential_configuration_ids":["\(offerId)"],"grants":{"urn:ietf:params:oauth:grant-type:pre-authorized_code":{"pre-authorized_code":"\(offerId)"}}}
        """
        guard let encoded = offerJson.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            return "openid-credential-offer://?credential_offer=\(offerJson)"
        }
        return "openid-credential-offer://?credential_offer=\(encoded)"
    }
}
