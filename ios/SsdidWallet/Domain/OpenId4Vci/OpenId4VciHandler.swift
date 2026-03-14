import Foundation

/// Error types for OpenID4VCI operations.
enum OpenId4VciError: Error, LocalizedError {
    case invalidOffer(String)
    case metadataError(String)
    case tokenError(String)
    case transportError(String)
    case proofError(String)
    case credentialError(String)
    case unsupported(String)

    var errorDescription: String? {
        switch self {
        case .invalidOffer(let reason):
            return "Invalid credential offer: \(reason)"
        case .metadataError(let reason):
            return "Metadata error: \(reason)"
        case .tokenError(let reason):
            return "Token error: \(reason)"
        case .transportError(let reason):
            return "VCI transport error: \(reason)"
        case .proofError(let reason):
            return "Proof error: \(reason)"
        case .credentialError(let reason):
            return "Credential error: \(reason)"
        case .unsupported(let reason):
            return "Unsupported: \(reason)"
        }
    }
}

/// Result of processing a credential offer before user consent.
struct CredentialOfferReview {
    let offer: CredentialOffer
    let metadata: IssuerMetadata
    let credentialConfigNames: [String]
}

/// Result of a credential issuance attempt.
enum IssuanceResult {
    case success(StoredSdJwtVc)
    case mdocSuccess(StoredMDoc)
    case deferred(transactionId: String, deferredEndpoint: String, accessToken: String)
    case failed(String)
}

/// Protocol for storing SD-JWT VCs, to be implemented by vault storage.
protocol SdJwtVcStorage {
    func saveSdJwtVc(_ sdJwtVc: StoredSdJwtVc) async throws
}

/// Protocol for storing mdoc credentials, to be implemented by vault storage.
protocol MDocStorage {
    func saveMDoc(_ mdoc: StoredMDoc) async throws
}

/// Orchestrates the OpenID4VCI credential issuance flow.
final class OpenId4VciHandler {

    private let metadataResolver: IssuerMetadataResolver
    private let tokenClient: TokenClient
    private let nonceManager: NonceManager
    private let transport: OpenId4VciTransport
    private let vcStorage: SdJwtVcStorage
    private let mdocStorage: MDocStorage?

    init(
        metadataResolver: IssuerMetadataResolver,
        tokenClient: TokenClient,
        nonceManager: NonceManager,
        transport: OpenId4VciTransport,
        vcStorage: SdJwtVcStorage,
        mdocStorage: MDocStorage? = nil
    ) {
        self.metadataResolver = metadataResolver
        self.tokenClient = tokenClient
        self.nonceManager = nonceManager
        self.transport = transport
        self.vcStorage = vcStorage
        self.mdocStorage = mdocStorage
    }

    /// Processes a credential offer URI: parses, resolves metadata, and returns review data.
    func processOffer(uri: String) async throws -> CredentialOfferReview {
        let offer = try CredentialOffer.parseFromUri(uri)
        let metadata = try await metadataResolver.resolve(issuerUrl: offer.credentialIssuer)

        return CredentialOfferReview(
            offer: offer,
            metadata: metadata,
            credentialConfigNames: offer.credentialConfigurationIds
        )
    }

    /// Accepts a credential offer and performs the issuance flow.
    func acceptOffer(
        offer: CredentialOffer,
        metadata: IssuerMetadata,
        selectedConfigId: String,
        txCode: String? = nil,
        walletDid: String,
        keyId: String,
        algorithm: String,
        signer: @escaping (Data) -> Data
    ) async throws -> IssuanceResult {
        // Step 1: Token exchange
        guard let preAuthCode = offer.preAuthorizedCode else {
            throw OpenId4VciError.unsupported(
                "Authorization code flow requires browser interaction"
            )
        }

        let tokenResponse = try await tokenClient.exchangePreAuthorizedCode(
            tokenEndpoint: metadata.tokenEndpoint,
            preAuthorizedCode: preAuthCode,
            txCode: txCode
        )

        // Step 2: Update nonce from token response
        if let nonce = tokenResponse.cNonce {
            await nonceManager.update(
                nonce: nonce,
                expiresIn: tokenResponse.cNonceExpiresIn ?? 300
            )
        }

        // Step 3: Request credential
        return try await requestCredential(
            metadata: metadata,
            accessToken: tokenResponse.accessToken,
            selectedConfigId: selectedConfigId,
            walletDid: walletDid,
            keyId: keyId,
            algorithm: algorithm,
            signer: signer
        )
    }

    // MARK: - Private

    private func requestCredential(
        metadata: IssuerMetadata,
        accessToken: String,
        selectedConfigId: String,
        walletDid: String,
        keyId: String,
        algorithm: String,
        signer: (Data) -> Data
    ) async throws -> IssuanceResult {
        guard let currentNonce = await nonceManager.current() else {
            throw OpenId4VciError.credentialError("No c_nonce available")
        }

        let proofJwt = try ProofJwtBuilder.build(
            algorithm: algorithm,
            keyId: keyId,
            walletDid: walletDid,
            issuerUrl: metadata.credentialIssuer,
            nonce: currentNonce,
            signer: signer
        )

        // Determine format from credential configuration
        let configObj = metadata.credentialConfigurationsSupported[selectedConfigId]
        let format = configObj?["format"] as? String ?? "vc+sd-jwt"
        let isMDoc = format == "mso_mdoc"

        let requestBody: [String: Any]
        if isMDoc {
            let doctype = configObj?["doctype"] as? String ?? selectedConfigId
            requestBody = [
                "format": "mso_mdoc",
                "doctype": doctype,
                "proof": [
                    "proof_type": "jwt",
                    "jwt": proofJwt
                ]
            ]
        } else {
            requestBody = [
                "format": "vc+sd-jwt",
                "credential_definition": ["vct": selectedConfigId],
                "proof": [
                    "proof_type": "jwt",
                    "jwt": proofJwt
                ]
            ]
        }

        let requestBodyData = try JSONSerialization.data(withJSONObject: requestBody)
        guard let requestBodyString = String(data: requestBodyData, encoding: .utf8) else {
            throw OpenId4VciError.credentialError("Failed to serialize request body")
        }

        let responseStr = try await transport.postCredentialRequest(
            credentialEndpoint: metadata.credentialEndpoint,
            accessToken: accessToken,
            requestBody: requestBodyString
        )

        guard let responseData = responseStr.data(using: .utf8),
              let response = try JSONSerialization.jsonObject(with: responseData) as? [String: Any] else {
            throw OpenId4VciError.credentialError("Invalid credential response JSON")
        }

        // Check for credential in response
        if let credential = response["credential"] as? String {
            // Update nonce from response if present
            if let nonce = response["c_nonce"] as? String {
                let expiresIn = response["c_nonce_expires_in"] as? Int ?? 300
                await nonceManager.update(nonce: nonce, expiresIn: expiresIn)
            }

            if isMDoc {
                return try await handleMDocCredential(
                    credentialBase64: credential,
                    selectedConfigId: selectedConfigId,
                    keyId: keyId,
                    metadata: metadata
                )
            }

            let storedVc = StoredSdJwtVc(
                id: UUID().uuidString,
                compact: credential,
                issuer: metadata.credentialIssuer,
                subject: walletDid,
                type: selectedConfigId,
                claims: [:],
                disclosableClaims: [],
                issuedAt: Int64(Date().timeIntervalSince1970)
            )

            try await vcStorage.saveSdJwtVc(storedVc)
            return .success(storedVc)
        }

        // Check for deferred issuance
        if let transactionId = response["transaction_id"] as? String {
            let deferredEndpoint = metadata.credentialEndpoint
                .replacingOccurrences(of: "/credential", with: "/deferred_credential")
            return .deferred(
                transactionId: transactionId,
                deferredEndpoint: deferredEndpoint,
                accessToken: accessToken
            )
        }

        return .failed("Unexpected response from credential endpoint")
    }

    private func handleMDocCredential(
        credentialBase64: String,
        selectedConfigId: String,
        keyId: String,
        metadata: IssuerMetadata
    ) async throws -> IssuanceResult {
        guard let cborBytes = Data(base64Encoded: credentialBase64
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        ) else {
            throw OpenId4VciError.credentialError("Invalid base64url credential data")
        }

        let decoded = CborCodec.decodeMap(cborBytes) ?? [:]
        let docType = decoded["docType"] as? String ?? selectedConfigId

        // Extract namespace keys from decoded CBOR
        let nameSpacesRaw = decoded["nameSpaces"] as? [String: Any] ?? [:]
        var nameSpaces = [String: [String]]()
        for (ns, value) in nameSpacesRaw {
            if let arr = value as? [Any] {
                nameSpaces[ns] = arr.compactMap { $0 as? String }
            } else if let dict = value as? [String: Any] {
                nameSpaces[ns] = Array(dict.keys)
            }
        }

        let configDoctype = metadata.credentialConfigurationsSupported[selectedConfigId]?["doctype"] as? String

        let storedMDoc = StoredMDoc(
            id: UUID().uuidString,
            docType: configDoctype ?? docType,
            issuerSignedCbor: cborBytes,
            deviceKeyId: keyId,
            issuedAt: Int64(Date().timeIntervalSince1970),
            nameSpaces: nameSpaces
        )

        if let mdocStorage = mdocStorage {
            try await mdocStorage.saveMDoc(storedMDoc)
        }
        return .mdocSuccess(storedMDoc)
    }
}
