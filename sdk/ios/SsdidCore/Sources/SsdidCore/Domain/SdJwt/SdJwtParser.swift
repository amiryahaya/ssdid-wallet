import Foundation

public enum SdJwtParser {
    public static func parse(_ compact: String) throws -> SdJwtVc {
        let parts = compact.split(separator: "~", omittingEmptySubsequences: false).map(String.init)
        guard !parts.isEmpty else { throw SdJwtError.emptyInput }

        let issuerJwt = parts[0]
        let remaining = parts.dropFirst().filter { !$0.isEmpty }

        let lastPart = remaining.last
        let isLastKbJwt: Bool = {
            guard let last = lastPart, last.filter({ $0 == "." }).count == 2 else { return false }
            let headerB64 = String(last.prefix(while: { $0 != "." }))
            guard let headerData = Data(base64URLEncoded: headerB64),
                  let headerDict = try? JSONSerialization.jsonObject(with: headerData) as? [String: Any],
                  let typ = headerDict["typ"] as? String else { return false }
            return typ == "kb+jwt"
        }()

        let disclosures: [Disclosure]
        let kbJwt: String?

        if isLastKbJwt && !remaining.isEmpty {
            disclosures = try remaining.dropLast().map { try Disclosure.decode($0) }
            kbJwt = lastPart
        } else {
            disclosures = try remaining.map { try Disclosure.decode($0) }
            kbJwt = nil
        }

        return SdJwtVc(issuerJwt: issuerJwt, disclosures: disclosures, keyBindingJwt: kbJwt)
    }
}
