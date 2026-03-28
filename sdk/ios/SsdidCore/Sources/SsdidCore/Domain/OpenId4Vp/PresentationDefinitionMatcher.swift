import Foundation

/// Result of matching a credential against a presentation definition or DCQL query.
public struct VpMatchResult {
    public let credential: StoredSdJwtVc
    public let descriptorId: String
    public let requiredClaims: [String]
    public let optionalClaims: [String]

    public init(credential: StoredSdJwtVc, descriptorId: String, requiredClaims: [String], optionalClaims: [String]) {
        self.credential = credential
        self.descriptorId = descriptorId
        self.requiredClaims = requiredClaims
        self.optionalClaims = optionalClaims
    }
}

/// Matches stored SD-JWT VCs against a Presentation Exchange presentation_definition.
final class PresentationDefinitionMatcher {

    func match(
        pd: [String: Any],
        credentials: [StoredSdJwtVc]
    ) -> [VpMatchResult] {
        guard let descriptors = pd["input_descriptors"] as? [[String: Any]] else {
            return []
        }
        var results: [VpMatchResult] = []

        for desc in descriptors {
            guard let descriptorId = desc["id"] as? String else { continue }

            if let format = desc["format"] as? [String: Any] {
                guard format["vc+sd-jwt"] != nil else { continue }
            }

            guard let constraints = desc["constraints"] as? [String: Any],
                  let fields = constraints["fields"] as? [[String: Any]] else {
                continue
            }

            for cred in credentials {
                if matchesConstraints(cred: cred, fields: fields) {
                    let (required, optional) = extractClaims(fields: fields, cred: cred)
                    results.append(VpMatchResult(
                        credential: cred,
                        descriptorId: descriptorId,
                        requiredClaims: required,
                        optionalClaims: optional
                    ))
                }
            }
        }
        return results
    }

    // MARK: - Private

    private func matchesConstraints(
        cred: StoredSdJwtVc,
        fields: [[String: Any]]
    ) -> Bool {
        for field in fields {
            let isOptional = field["optional"] as? Bool == true
            if isOptional { continue }

            guard let paths = field["path"] as? [String] else { continue }
            let filterConst = (field["filter"] as? [String: Any])?["const"] as? String

            for path in paths {
                if path == "$.vct", let filterConst = filterConst {
                    if cred.type != filterConst { return false }
                } else {
                    let claimName = path.hasPrefix("$.") ? String(path.dropFirst(2)) : path
                    if cred.claims[claimName] == nil && !cred.disclosableClaims.contains(claimName) {
                        return false
                    }
                }
            }
        }
        return true
    }

    private func extractClaims(
        fields: [[String: Any]],
        cred: StoredSdJwtVc
    ) -> (required: [String], optional: [String]) {
        var required: [String] = []
        var optional: [String] = []

        for field in fields {
            guard let paths = field["path"] as? [String] else { continue }
            let isOptional = field["optional"] as? Bool == true

            for path in paths {
                if path == "$.vct" { continue }
                let claimName = path.hasPrefix("$.") ? String(path.dropFirst(2)) : path
                if cred.claims[claimName] != nil || cred.disclosableClaims.contains(claimName) {
                    if isOptional {
                        optional.append(claimName)
                    } else {
                        required.append(claimName)
                    }
                }
            }
        }
        return (required, optional)
    }
}
