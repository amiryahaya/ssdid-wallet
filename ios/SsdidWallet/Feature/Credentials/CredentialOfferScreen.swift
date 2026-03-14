import SwiftUI

struct CredentialOfferScreen: View {
    @Environment(AppRouter.self) private var router

    let issuerUrl: String
    let offerId: String

    enum OfferState {
        case loading
        case loaded
        case accepting
        case success
        case error(String)
    }

    struct OfferDetails {
        let issuerDid: String
        let credentialType: String
        let expiresAt: String?
        let claims: [(key: String, value: String)]
    }

    @State private var state: OfferState = .loading
    @State private var offer: OfferDetails?
    @State private var identities: [Identity] = []
    @State private var selectedIdentity: Identity?

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

            switch state {
            case .loading:
                Spacer()
                VStack(spacing: 16) {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: Color.ssdidAccent))
                    Text("Fetching offer details...")
                        .font(.system(size: 14))
                        .foregroundStyle(Color.textSecondary)
                }
                Spacer()

            case .loaded:
                if let offer {
                    offerContent(offer)
                }

            case .accepting:
                Spacer()
                VStack(spacing: 16) {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: Color.ssdidAccent))
                    Text("Accepting credential...")
                        .font(.system(size: 14))
                        .foregroundStyle(Color.textSecondary)
                }
                Spacer()

            case .success:
                Spacer()
                statusView(success: true, title: "Credential Accepted",
                           message: "The credential has been stored in your wallet.")
                Spacer()
                Button { router.pop() } label: { Text("Done") }
                    .buttonStyle(.ssdidPrimary)
                    .padding(20)

            case .error(let message):
                Spacer()
                statusView(success: false, title: "Offer Failed", message: message)
                Spacer()
                Button { router.pop() } label: { Text("Go Back") }
                    .buttonStyle(.ssdidDanger)
                    .padding(20)
            }
        }
        .background(Color.bgPrimary)
        .onAppear { fetchOffer() }
    }

    @ViewBuilder
    private func offerContent(_ offer: OfferDetails) -> some View {
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
                    Text(offer.issuerDid)
                        .font(.ssdidMono)
                        .foregroundStyle(Color.textPrimary)
                        .lineLimit(1)
                    Spacer().frame(height: 6)
                    Text("Credential Type")
                        .font(.system(size: 11))
                        .foregroundStyle(Color.textTertiary)
                    Text(offer.credentialType)
                        .font(.system(size: 13))
                        .foregroundStyle(Color.ssdidAccent)
                    if let exp = offer.expiresAt {
                        Spacer().frame(height: 6)
                        Text("Expires")
                            .font(.system(size: 11))
                            .foregroundStyle(Color.textTertiary)
                        Text(exp)
                            .font(.system(size: 13))
                            .foregroundStyle(Color.warning)
                    }
                }
                .ssdidCard()

                // Claims
                if !offer.claims.isEmpty {
                    VStack(alignment: .leading, spacing: 0) {
                        Text("CLAIMS")
                            .font(.ssdidCaption)
                            .foregroundStyle(Color.textTertiary)
                        Spacer().frame(height: 8)
                        ForEach(offer.claims, id: \.key) { claim in
                            HStack {
                                Text(claim.key)
                                    .font(.system(size: 12))
                                    .foregroundStyle(Color.textTertiary)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                Text(claim.value)
                                    .font(.system(size: 12))
                                    .foregroundStyle(Color.textPrimary)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                            .padding(.vertical, 2)
                        }
                    }
                    .ssdidCard()
                }

                // Identity selector
                Text("SELECT IDENTITY")
                    .font(.ssdidCaption)
                    .foregroundStyle(Color.textTertiary)

                ForEach(identities) { identity in
                    let isSelected = selectedIdentity?.keyId == identity.keyId
                    Button { selectedIdentity = identity } label: {
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

                if identities.isEmpty {
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
            Button { router.pop() } label: { Text("Reject") }
                .buttonStyle(.ssdidDanger)

            Button { acceptOffer() } label: { Text("Accept") }
                .buttonStyle(.ssdidPrimary(enabled: selectedIdentity != nil))
                .disabled(selectedIdentity == nil)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
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
        }
        .padding(.horizontal, 32)
    }

    private func fetchOffer() {
        state = .loading
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
            offer = OfferDetails(
                issuerDid: "did:ssdid:issuer-example",
                credentialType: "VerifiableCredential",
                expiresAt: "2025-12-31T23:59:59Z",
                claims: [
                    (key: "name", value: "Ahmad Bin Abdullah"),
                    (key: "email", value: "ahmad@example.com")
                ]
            )
            state = .loaded
        }
    }

    private func acceptOffer() {
        state = .accepting
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            HapticManager.notification(.success)
            state = .success
        }
    }
}
