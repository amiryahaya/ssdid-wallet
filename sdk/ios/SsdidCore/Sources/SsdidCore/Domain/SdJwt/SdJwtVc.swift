import Foundation

public struct SdJwtVc {
    public let issuerJwt: String
    public let disclosures: [Disclosure]
    public let keyBindingJwt: String?

    public init(issuerJwt: String, disclosures: [Disclosure], keyBindingJwt: String?) {
        self.issuerJwt = issuerJwt
        self.disclosures = disclosures
        self.keyBindingJwt = keyBindingJwt
    }

    public func present(selectedDisclosures: [Disclosure], kbJwt: String? = nil) throws -> String {
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
