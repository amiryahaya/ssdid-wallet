import Foundation

/// Matches stored SD-JWT VCs against a DCQL (Digital Credentials Query Language) query.
final class DcqlMatcher {

    func match(
        dcql: [String: Any],
        credentials: [StoredSdJwtVc]
    ) -> [VpMatchResult] {
        guard let credSpecs = dcql["credentials"] as? [[String: Any]] else {
            return []
        }
        var results: [VpMatchResult] = []

        for spec in credSpecs {
            guard let id = spec["id"] as? String else { continue }

            if let format = spec["format"] as? String, format != "vc+sd-jwt" {
                continue
            }

            let vctValues: Set<String>? = {
                guard let meta = spec["meta"] as? [String: Any],
                      let values = meta["vct_values"] as? [String] else {
                    return nil
                }
                return Set(values)
            }()

            let claimsSpec = spec["claims"] as? [[String: Any]]

            for cred in credentials {
                if let vctValues = vctValues, !vctValues.contains(cred.type) {
                    continue
                }

                let (required, optional): ([String], [String])
                if let claimsSpec = claimsSpec {
                    (required, optional) = extractClaims(claimsSpec: claimsSpec, cred: cred)
                } else {
                    (required, optional) = (cred.disclosableClaims, [])
                }

                results.append(VpMatchResult(
                    credential: cred,
                    descriptorId: id,
                    requiredClaims: required,
                    optionalClaims: optional
                ))
            }
        }
        return results
    }

    // MARK: - Private

    private func extractClaims(
        claimsSpec: [[String: Any]],
        cred: StoredSdJwtVc
    ) -> (required: [String], optional: [String]) {
        var required: [String] = []
        var optional: [String] = []

        for claim in claimsSpec {
            guard let paths = claim["path"] as? [String],
                  let claimName = paths.first else {
                continue
            }
            let isOptional = claim["optional"] as? Bool == true

            if cred.claims[claimName] != nil || cred.disclosableClaims.contains(claimName) {
                if isOptional {
                    optional.append(claimName)
                } else {
                    required.append(claimName)
                }
            }
        }
        return (required, optional)
    }
}
