import Foundation

struct SdJwtVc {
    let issuerJwt: String
    let disclosures: [Disclosure]
    let keyBindingJwt: String?

    func present(selectedDisclosures: [Disclosure], kbJwt: String? = nil) -> String {
        var parts = [issuerJwt]
        parts.append(contentsOf: selectedDisclosures.map { $0.encode() })
        let suffix = kbJwt ?? ""
        return parts.joined(separator: "~") + "~\(suffix)"
    }
}
