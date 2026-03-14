import Foundation
import CryptoKit

/// Verifies MSO digests and validity windows for mdoc credentials.
enum MsoVerifier {

    /// Verify that an IssuerSignedItem's digest matches the expected value in the MSO.
    ///
    /// Per ISO 18013-5, the digest is computed over the tag-24-wrapped CBOR encoding
    /// of the IssuerSignedItem (i.e., the encoded CBOR data item tag wraps the item bytes).
    static func verifyDigest(
        item: IssuerSignedItem,
        mso: MobileSecurityObject,
        namespace: String
    ) -> Bool {
        guard let expectedDigest = mso.valueDigests[namespace]?[item.digestId] else {
            return false
        }

        guard let computedDigest = CborCodec.digestIssuerSignedItem(item, algorithm: mso.digestAlgorithm) else {
            return false
        }

        return computedDigest == expectedDigest
    }

    /// Verify that the current time falls within the MSO validity window.
    static func verifyValidity(_ mso: MobileSecurityObject) -> Bool {
        let now = Date()
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]

        // Try with fractional seconds first, then without
        let validFrom = formatter.date(from: mso.validityInfo.validFrom)
            ?? ISO8601DateFormatter().date(from: mso.validityInfo.validFrom)
        let validUntil = formatter.date(from: mso.validityInfo.validUntil)
            ?? ISO8601DateFormatter().date(from: mso.validityInfo.validUntil)

        guard let from = validFrom, let until = validUntil else {
            return false
        }

        return now >= from && now <= until
    }
}
