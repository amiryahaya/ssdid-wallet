import Foundation

/// Error types for OpenID4VCI operations.
public enum OpenId4VciError: Error, LocalizedError {
    case invalidOffer(String)
    case metadataError(String)
    case tokenError(String)
    case transportError(String)
    case proofError(String)
    case credentialError(String)
    case unsupported(String)

    public var errorDescription: String? {
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
public struct CredentialOfferReview {
    public let offer: CredentialOffer
    public let metadata: IssuerMetadata
    public let credentialConfigNames: [String]

    public init(offer: CredentialOffer, metadata: IssuerMetadata, credentialConfigNames: [String]) {
        self.offer = offer
        self.metadata = metadata
        self.credentialConfigNames = credentialConfigNames
    }
}

/// Result of a credential issuance attempt.
public enum IssuanceResult {
    case success(StoredSdJwtVc)
    case deferred(transactionId: String, deferredEndpoint: String, accessToken: String)
    case failed(String)
}

/// Protocol for storing SD-JWT VCs, to be implemented by vault storage.
public protocol SdJwtVcStorage {
    func saveSdJwtVc(_ sdJwtVc: StoredSdJwtVc) async throws
}

/// Orchestrates the OpenID4VCI credential issuance flow.
public final class OpenId4VciHandler: @unchecked Sendable {

    private let metadataResolver: IssuerMetadataResolver
    private let tokenClient: TokenClient
    private let nonceManager: NonceManager
    private let transport: OpenId4VciTransport
    private let vcStorage: SdJwtVcStorage

    public     init(
        metadataResolver: IssuerMetadataResolver,
        tokenClient: TokenClient,
        nonceManager: NonceManager,
        transport: OpenId4VciTransport,
        vcStorage: SdJwtVcStorage
    ) {
        self.metadataResolver = metadataResolver
        self.tokenClient = tokenClient
        self.nonceManager = nonceManager
        self.transport = transport
        self.vcStorage = vcStorage
    }

    /// Processes a credential offer URI: parses, resolves metadata, and returns review data.
    public     func processOffer(uri: String) async throws -> CredentialOfferReview {
        let offer = try CredentialOffer.parseFromUri(uri)
        let metadata = try await metadataResolver.resolve(issuerUrl: offer.credentialIssuer)

        return CredentialOfferReview(
            offer: offer,
            metadata: metadata,
            credentialConfigNames: offer.credentialConfigurationIds
        )
    }

    /// Processes a credential offer from a raw JSON string: parses, resolves metadata, and returns review data.
    public     func processOfferJson(json: String) async throws -> CredentialOfferReview {
        let offer = try CredentialOffer.parse(json)
        let metadata = try await metadataResolver.resolve(issuerUrl: offer.credentialIssuer)

        return CredentialOfferReview(
            offer: offer,
            metadata: metadata,
            credentialConfigNames: offer.credentialConfigurationIds
        )
    }

    /// Accepts a credential offer and performs the issuance flow.
    public     func acceptOffer(
        offer: CredentialOffer,
        metadata: IssuerMetadata,
        selectedConfigId: String,
        txCode: String? = nil,
        walletDid: String,
        keyId: String,
        algorithm: String,
        signer: @escaping @Sendable (Data) -> Data
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
        signer: @Sendable (Data) -> Data
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

        let requestBody: [String: Any] = [
            "format": "vc+sd-jwt",
            "credential_definition": ["vct": selectedConfigId],
            "proof": [
                "proof_type": "jwt",
                "jwt": proofJwt
            ]
        ]

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
}
