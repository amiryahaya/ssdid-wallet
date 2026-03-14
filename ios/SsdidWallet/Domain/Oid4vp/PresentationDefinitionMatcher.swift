import Foundation

class PresentationDefinitionMatcher {

    func match(
        _ presentationDefinitionJson: String,
        storedCredentials: [StoredSdJwtVc]
    ) -> [MatchResult] {
        let query = toCredentialQuery(presentationDefinitionJson)
        return Self.matchQuery(query, storedCredentials: storedCredentials)
    }

    func toCredentialQuery(_ presentationDefinitionJson: String) -> CredentialQuery {
        guard let data = presentationDefinitionJson.data(using: .utf8),
              let pd = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let inputDescriptors = pd["input_descriptors"] as? [[String: Any]] else {
            return CredentialQuery(descriptors: [])
        }

        let descriptors = inputDescriptors.map { desc -> CredentialQueryDescriptor in
            let id = desc["id"] as? String ?? ""
            let format: String
            if let formatObj = desc["format"] as? [String: Any] {
                format = formatObj.keys.first ?? "vc+sd-jwt"
            } else {
                format = "vc+sd-jwt"
            }

            let fields: [[String: Any]]
            if let constraints = desc["constraints"] as? [String: Any],
               let f = constraints["fields"] as? [[String: Any]] {
                fields = f
            } else {
                fields = []
            }

            var vctFilter: String?
            var requiredClaims: [String] = []
            var optionalClaims: [String] = []

            for field in fields {
                guard let pathArray = field["path"] as? [String],
                      let path = pathArray.first else { continue }
                let filter = field["filter"] as? [String: Any]
                let optional = field["optional"] as? Bool ?? false

                if path == "$.vct", let filter = filter {
                    vctFilter = filter["const"] as? String
                    continue
                }

                let claimName = path.hasPrefix("$.") ? String(path.dropFirst(2)) : path
                if optional {
                    optionalClaims.append(claimName)
                } else {
                    requiredClaims.append(claimName)
                }
            }

            return CredentialQueryDescriptor(
                id: id, format: format, vctFilter: vctFilter,
                requiredClaims: requiredClaims, optionalClaims: optionalClaims
            )
        }

        return CredentialQuery(descriptors: descriptors)
    }

    static func matchQuery(
        _ query: CredentialQuery,
        storedCredentials: [StoredSdJwtVc]
    ) -> [MatchResult] {
        var results: [MatchResult] = []
        for descriptor in query.descriptors {
            for credential in storedCredentials {
                if let vctFilter = descriptor.vctFilter, credential.type != vctFilter {
                    continue
                }
                let allClaims = Set(credential.claims.keys)
                let hasAllRequired = descriptor.requiredClaims.allSatisfy { allClaims.contains($0) }
                if !hasAllRequired { continue }

                var claimInfoMap: [String: ClaimInfo] = [:]
                for claim in descriptor.requiredClaims {
                    claimInfoMap[claim] = ClaimInfo(
                        name: claim, required: true, available: allClaims.contains(claim)
                    )
                }
                for claim in descriptor.optionalClaims {
                    claimInfoMap[claim] = ClaimInfo(
                        name: claim, required: false, available: allClaims.contains(claim)
                    )
                }

                results.append(MatchResult(
                    descriptorId: descriptor.id,
                    credentialId: credential.id,
                    credentialType: credential.type,
                    availableClaims: claimInfoMap,
                    source: .sdJwtVc
                ))
                break
            }
        }
        return results
    }
}
