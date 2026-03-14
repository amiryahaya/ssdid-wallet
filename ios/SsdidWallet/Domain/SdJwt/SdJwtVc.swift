import Foundation

struct SdJwtVc {
    let issuerJwt: String
    let disclosures: [Disclosure]
    let keyBindingJwt: String?

    func present(selectedDisclosures: [Disclosure], kbJwt: String? = nil) throws -> String {
        var parts = [issuerJwt]
        for d in selectedDisclosures {
            parts.append(try d.encode())
        }
        if let kb = kbJwt {
            return parts.joined(separator: "~") + "~\(kb)"
        } else if !selectedDisclosures.isEmpty {
            return parts.joined(separator: "~") + "~"
        } else {
            return issuerJwt
        }
    }
}
