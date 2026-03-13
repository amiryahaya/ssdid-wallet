import SwiftUI

extension String {
    /// Truncates a DID for display: `did:ssdid:Ab3x...7kQ9`
    /// Shows the method prefix + first 4 chars + ... + last 4 chars of the method-specific ID.
    var truncatedDid: String {
        guard hasPrefix("did:") else { return self }
        let parts = split(separator: ":", maxSplits: 2)
        guard parts.count == 3 else { return self }
        let methodSpecificId = String(parts[2])
        if methodSpecificId.count <= 12 { return self }
        let prefix = methodSpecificId.prefix(4)
        let suffix = methodSpecificId.suffix(4)
        return "did:\(parts[1]):\(prefix)...\(suffix)"
    }
}
