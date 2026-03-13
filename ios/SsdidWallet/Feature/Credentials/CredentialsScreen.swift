import SwiftUI

struct CredentialsScreen: View {
    @Environment(AppRouter.self) private var router

    @State private var credentials: [VerifiableCredential] = []

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

                Text("Credentials")
                    .font(.ssdidHeadline)
                    .foregroundStyle(Color.textPrimary)

                Spacer()
            }
            .padding(.vertical, 12)
            .padding(.trailing, 20)

            ScrollView {
                LazyVStack(spacing: 10) {
                    ForEach(credentials) { vc in
                        Button {
                            router.push(.credentialDetail(credentialId: vc.id))
                        } label: {
                            HStack(spacing: 0) {
                                Rectangle()
                                    .fill(Color.ssdidAccent)
                                    .frame(width: 4)

                                VStack(alignment: .leading, spacing: 0) {
                                    let isExpired = isCredentialExpired(vc)

                                    HStack {
                                        Text(vc.type.last ?? "Credential")
                                            .font(.ssdidCaption)
                                            .foregroundStyle(Color.textTertiary)
                                        Spacer()
                                        Text(isExpired ? "Expired" : "Valid")
                                            .font(.system(size: 10))
                                            .foregroundStyle(isExpired ? Color.danger : Color.success)
                                            .padding(.horizontal, 8)
                                            .padding(.vertical, 2)
                                            .background(isExpired ? Color.dangerDim : Color.successDim)
                                            .cornerRadius(4)
                                    }

                                    Spacer().frame(height: 4)

                                    Text(String(vc.id.suffix(20)))
                                        .font(.ssdidHeadline)
                                        .foregroundStyle(Color.textPrimary)

                                    Spacer().frame(height: 4)

                                    Text("Issuer: \(vc.issuer.truncatedDid)")
                                        .font(.system(size: 11, design: .monospaced))
                                        .foregroundStyle(Color.textTertiary)
                                        .lineLimit(1)

                                    Spacer().frame(height: 8)

                                    HStack {
                                        Text("Issued: \(String(vc.issuanceDate.prefix(10)))")
                                            .font(.system(size: 12))
                                            .foregroundStyle(Color.textSecondary)
                                        Spacer()
                                        if let exp = vc.expirationDate {
                                            Text("Expires: \(String(exp.prefix(10)))")
                                                .font(.system(size: 12))
                                                .foregroundStyle(Color.textSecondary)
                                        }
                                    }
                                }
                                .padding(14)
                            }
                            .background(Color.bgCard)
                            .cornerRadius(16)
                        }
                    }

                    if credentials.isEmpty {
                        VStack(spacing: 0) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 20)
                                    .fill(Color.accentDim)
                                    .frame(width: 72, height: 72)
                                Image(systemName: "doc.text")
                                    .font(.system(size: 32))
                                    .foregroundStyle(Color.ssdidAccent)
                            }
                            Spacer().frame(height: 16)
                            Text("No credentials yet")
                                .font(.ssdidHeadline)
                                .foregroundStyle(Color.textPrimary)
                            Spacer().frame(height: 4)
                            Text("Register with a service to receive your first credential")
                                .font(.system(size: 14))
                                .foregroundStyle(Color.textSecondary)
                                .multilineTextAlignment(.center)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(32)
                        .background(Color.bgCard)
                        .cornerRadius(16)
                    }
                }
                .padding(.horizontal, 20)
            }
        }
        .background(Color.bgPrimary)
    }

    private func isCredentialExpired(_ vc: VerifiableCredential) -> Bool {
        guard let exp = vc.expirationDate else { return false }
        let formatter = ISO8601DateFormatter()
        guard let date = formatter.date(from: exp) else { return false }
        return Date() > date
    }
}
