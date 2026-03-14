import Foundation

/// Matches stored credentials against a DCQL (Digital Credentials Query Language) query.
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
                    credentialRef: .sdJwt(cred),
                    descriptorId: id,
                    requiredClaims: required,
                    optionalClaims: optional
                ))
            }
        }
        return results
    }

    func matchAll(
        dcql: [String: Any],
        sdJwtVcs: [StoredSdJwtVc],
        mdocs: [StoredMDoc]
    ) -> [VpMatchResult] {
        guard let credSpecs = dcql["credentials"] as? [[String: Any]] else {
            return []
        }
        var results: [VpMatchResult] = []

        for spec in credSpecs {
            guard let id = spec["id"] as? String else { continue }
            let format = spec["format"] as? String

            switch format {
            case "mso_mdoc":
                let docTypeValue = (spec["meta"] as? [String: Any])?["doctype_value"] as? String
                let claimsSpec = spec["claims"] as? [[String: Any]]

                for mdoc in mdocs {
                    if let docTypeValue = docTypeValue, mdoc.docType != docTypeValue {
                        continue
                    }
                    let (required, optional): ([String], [String])
                    if let claimsSpec = claimsSpec {
                        guard matchesMDocClaims(claimsSpec: claimsSpec, mdoc: mdoc) else {
                            continue
                        }
                        (required, optional) = extractMDocClaims(claimsSpec: claimsSpec, mdoc: mdoc)
                    } else {
                        (required, optional) = (mdoc.nameSpaces.values.flatMap { $0 }, [])
                    }
                    results.append(VpMatchResult(
                        credentialRef: .mdoc(mdoc),
                        descriptorId: id,
                        requiredClaims: required,
                        optionalClaims: optional
                    ))
                }

            case "vc+sd-jwt", nil:
                let vctValues: Set<String>? = {
                    guard let meta = spec["meta"] as? [String: Any],
                          let values = meta["vct_values"] as? [String] else {
                        return nil
                    }
                    return Set(values)
                }()

                let claimsSpec = spec["claims"] as? [[String: Any]]

                for cred in sdJwtVcs {
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
                        credentialRef: .sdJwt(cred),
                        descriptorId: id,
                        requiredClaims: required,
                        optionalClaims: optional
                    ))
                }

            default:
                continue
            }
        }
        return results
    }

    // MARK: - SD-JWT private helpers

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

    // MARK: - MDoc private helpers

    private func matchesMDocClaims(
        claimsSpec: [[String: Any]],
        mdoc: StoredMDoc
    ) -> Bool {
        for claim in claimsSpec {
            let isOptional = claim["optional"] as? Bool == true
            if isOptional { continue }
            guard let namespace = claim["namespace"] as? String,
                  let claimName = claim["claim_name"] as? String else {
                continue
            }
            guard let elements = mdoc.nameSpaces[namespace] else { return false }
            if !elements.contains(claimName) { return false }
        }
        return true
    }

    private func extractMDocClaims(
        claimsSpec: [[String: Any]],
        mdoc: StoredMDoc
    ) -> (required: [String], optional: [String]) {
        var required: [String] = []
        var optional: [String] = []

        for claim in claimsSpec {
            guard let namespace = claim["namespace"] as? String,
                  let claimName = claim["claim_name"] as? String else {
                continue
            }
            let isOptional = claim["optional"] as? Bool == true
            guard let elements = mdoc.nameSpaces[namespace] else { continue }
            if elements.contains(claimName) {
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
