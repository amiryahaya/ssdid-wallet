import Foundation

enum SdJwtParser {
    static func parse(_ compact: String) throws -> SdJwtVc {
        let parts = compact.split(separator: "~", omittingEmptySubsequences: false).map(String.init)
        guard !parts.isEmpty else { throw SdJwtError.emptyInput }

        let issuerJwt = parts[0]
        let remaining = parts.dropFirst().filter { !$0.isEmpty }

        let lastPart = remaining.last
        let isLastKbJwt = lastPart != nil && lastPart!.filter({ $0 == "." }).count == 2

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
