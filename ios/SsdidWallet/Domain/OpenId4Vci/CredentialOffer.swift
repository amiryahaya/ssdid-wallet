import Foundation

/// Transaction code requirement for pre-authorized code flow.
struct TxCodeRequirement {
    let inputMode: String
    let length: Int
    let description: String?
}

/// Parsed OpenID4VCI credential offer.
struct CredentialOffer {
    let credentialIssuer: String
    let credentialConfigurationIds: [String]
    let preAuthorizedCode: String?
    let txCode: TxCodeRequirement?
    let authorizationCodeGrant: Bool
    let issuerState: String?

    /// Parses a credential offer from a JSON string.
    static func parse(_ jsonString: String) throws -> CredentialOffer {
        guard let data = jsonString.data(using: .utf8),
              let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw OpenId4VciError.invalidOffer("Invalid JSON")
        }

        guard let issuer = obj["credential_issuer"] as? String else {
            throw OpenId4VciError.invalidOffer("Missing credential_issuer")
        }
        guard issuer.hasPrefix("https://") else {
            throw OpenId4VciError.invalidOffer("credential_issuer must be HTTPS: \(issuer)")
        }

        guard let configIds = obj["credential_configuration_ids"] as? [String],
              !configIds.isEmpty else {
            throw OpenId4VciError.invalidOffer("Missing or empty credential_configuration_ids")
        }

        guard let grants = obj["grants"] as? [String: Any] else {
            throw OpenId4VciError.invalidOffer("Missing grants")
        }

        let preAuthGrant = grants["urn:ietf:params:oauth:grant-type:pre-authorized_code"] as? [String: Any]
        let authCodeGrant = grants["authorization_code"] as? [String: Any]

        guard preAuthGrant != nil || authCodeGrant != nil else {
            throw OpenId4VciError.invalidOffer("Must have at least one grant type")
        }

        let preAuthCode = preAuthGrant?["pre-authorized_code"] as? String

        var txCode: TxCodeRequirement? = nil
        if let txCodeObj = preAuthGrant?["tx_code"] as? [String: Any] {
            txCode = TxCodeRequirement(
                inputMode: txCodeObj["input_mode"] as? String ?? "numeric",
                length: txCodeObj["length"] as? Int ?? 6,
                description: txCodeObj["description"] as? String
            )
        }

        let issuerState = authCodeGrant?["issuer_state"] as? String

        return CredentialOffer(
            credentialIssuer: issuer,
            credentialConfigurationIds: configIds,
            preAuthorizedCode: preAuthCode,
            txCode: txCode,
            authorizationCodeGrant: authCodeGrant != nil,
            issuerState: issuerState
        )
    }

    /// Parses a credential offer from a URI containing credential_offer or credential_offer_uri query parameter.
    static func parseFromUri(_ uriString: String) throws -> CredentialOffer {
        guard let components = URLComponents(string: uriString) else {
            throw OpenId4VciError.invalidOffer("Invalid URI: \(uriString)")
        }

        let queryItems = components.queryItems ?? []
        let offerJson = queryItems.first(where: { $0.name == "credential_offer" })?.value
        let offerUri = queryItems.first(where: { $0.name == "credential_offer_uri" })?.value

        if let offerJson = offerJson {
            return try parse(offerJson)
        } else if let offerUri = offerUri {
            guard offerUri.hasPrefix("https://") else {
                throw OpenId4VciError.invalidOffer("credential_offer_uri must be HTTPS")
            }
            throw OpenId4VciError.unsupported("By-reference offers require network fetch")
        } else {
            throw OpenId4VciError.invalidOffer("Missing credential_offer or credential_offer_uri")
        }
    }
}
