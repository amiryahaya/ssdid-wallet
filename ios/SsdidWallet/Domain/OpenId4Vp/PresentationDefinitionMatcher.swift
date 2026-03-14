import Foundation

/// Result of matching a credential against a presentation definition or DCQL query.
struct VpMatchResult {
    let credentialRef: CredentialRef
    let descriptorId: String
    let requiredClaims: [String]
    let optionalClaims: [String]
}

/// Matches stored credentials against a Presentation Exchange presentation_definition.
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
                        credentialRef: .sdJwt(cred),
                        descriptorId: descriptorId,
                        requiredClaims: required,
                        optionalClaims: optional
                    ))
                }
            }
        }
        return results
    }

    func matchMDoc(
        pd: [String: Any],
        mdocs: [StoredMDoc]
    ) -> [VpMatchResult] {
        guard let descriptors = pd["input_descriptors"] as? [[String: Any]] else {
            return []
        }
        var results: [VpMatchResult] = []

        for desc in descriptors {
            guard let descriptorId = desc["id"] as? String else { continue }

            if let format = desc["format"] as? [String: Any] {
                guard format["mso_mdoc"] != nil else { continue }
            }

            guard let constraints = desc["constraints"] as? [String: Any],
                  let fields = constraints["fields"] as? [[String: Any]] else {
                continue
            }

            let requiredDocType = extractDocTypeFilter(fields: fields)

            for mdoc in mdocs {
                if let requiredDocType = requiredDocType, mdoc.docType != requiredDocType {
                    continue
                }
                if matchesMDocConstraints(mdoc: mdoc, fields: fields) {
                    let (required, optional) = extractMDocClaims(fields: fields, mdoc: mdoc)
                    results.append(VpMatchResult(
                        credentialRef: .mdoc(mdoc),
                        descriptorId: descriptorId,
                        requiredClaims: required,
                        optionalClaims: optional
                    ))
                }
            }
        }
        return results
    }

    func matchAll(
        pd: [String: Any],
        sdJwtVcs: [StoredSdJwtVc],
        mdocs: [StoredMDoc]
    ) -> [VpMatchResult] {
        return match(pd: pd, credentials: sdJwtVcs) + matchMDoc(pd: pd, mdocs: mdocs)
    }

    // MARK: - SD-JWT private helpers

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

    // MARK: - MDoc private helpers

    private func extractDocTypeFilter(fields: [[String: Any]]) -> String? {
        for field in fields {
            guard let paths = field["path"] as? [String] else { continue }
            if paths.contains("$.doctype") {
                return (field["filter"] as? [String: Any])?["const"] as? String
            }
        }
        return nil
    }

    private func matchesMDocConstraints(
        mdoc: StoredMDoc,
        fields: [[String: Any]]
    ) -> Bool {
        for field in fields {
            let isOptional = field["optional"] as? Bool == true
            if isOptional { continue }
            guard let paths = field["path"] as? [String] else { continue }

            for path in paths {
                if path == "$.doctype" { continue }
                guard let (namespace, element) = parseMDocPath(path) else { continue }
                guard let elements = mdoc.nameSpaces[namespace] else { return false }
                if !elements.contains(element) { return false }
            }
        }
        return true
    }

    private func extractMDocClaims(
        fields: [[String: Any]],
        mdoc: StoredMDoc
    ) -> (required: [String], optional: [String]) {
        var required: [String] = []
        var optional: [String] = []

        for field in fields {
            guard let paths = field["path"] as? [String] else { continue }
            let isOptional = field["optional"] as? Bool == true

            for path in paths {
                if path == "$.doctype" { continue }
                guard let (namespace, element) = parseMDocPath(path) else { continue }
                guard let elements = mdoc.nameSpaces[namespace] else { continue }
                if elements.contains(element) {
                    if isOptional {
                        optional.append(element)
                    } else {
                        required.append(element)
                    }
                }
            }
        }
        return (required, optional)
    }

    /// Parses mdoc PE paths like `$['namespace']['element']` into (namespace, element).
    private func parseMDocPath(_ path: String) -> (String, String)? {
        let pattern = #"\$\['([^']+)'\]\['([^']+)'\]"#
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(
                  in: path,
                  range: NSRange(path.startIndex..., in: path)
              ),
              match.numberOfRanges == 3,
              let nsRange = Range(match.range(at: 1), in: path),
              let elemRange = Range(match.range(at: 2), in: path) else {
            return nil
        }
        return (String(path[nsRange]), String(path[elemRange]))
    }
}
