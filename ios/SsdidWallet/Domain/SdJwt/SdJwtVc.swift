import Foundation

struct SdJwtVc {
    let issuerJwt: String
    let disclosures: [Disclosure]
    let keyBindingJwt: String?

    func present(selectedDisclosures: [Disclosure], kbJwt: String? = nil) throws -> String {
        var parts = [issuerJwt]
        parts.append(contentsOf: try selectedDisclosures.map { try $0.encode() })
        let suffix = kbJwt ?? ""
        return parts.joined(separator: "~") + "~\(suffix)"
    }
}
