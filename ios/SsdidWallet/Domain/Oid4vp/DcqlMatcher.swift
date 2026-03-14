import Foundation

class DcqlMatcher {

    func match(
        _ dcqlJson: String,
        storedCredentials: [StoredSdJwtVc]
    ) -> [MatchResult] {
        let query = toCredentialQuery(dcqlJson)
        return PresentationDefinitionMatcher.matchQuery(query, storedCredentials: storedCredentials)
    }

    func toCredentialQuery(_ dcqlJson: String) -> CredentialQuery {
        guard let data = dcqlJson.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let credentials = root["credentials"] as? [[String: Any]] else {
            return CredentialQuery(descriptors: [])
        }

        let descriptors = credentials.map { cred -> CredentialQueryDescriptor in
            let id = cred["id"] as? String ?? ""
            let format = cred["format"] as? String ?? "vc+sd-jwt"

            var vctFilter: String?
            if let meta = cred["meta"] as? [String: Any],
               let vctValues = meta["vct_values"] as? [String] {
                vctFilter = vctValues.first
            }

            var requiredClaims: [String] = []
            var optionalClaims: [String] = []

            if let claims = cred["claims"] as? [[String: Any]] {
                for claim in claims {
                    guard let pathArray = claim["path"] as? [String],
                          let path = pathArray.first else { continue }
                    let optional = claim["optional"] as? Bool ?? false
                    if optional {
                        optionalClaims.append(path)
                    } else {
                        requiredClaims.append(path)
                    }
                }
            }

            return CredentialQueryDescriptor(
                id: id, format: format, vctFilter: vctFilter,
                requiredClaims: requiredClaims, optionalClaims: optionalClaims
            )
        }

        return CredentialQuery(descriptors: descriptors)
    }
}
