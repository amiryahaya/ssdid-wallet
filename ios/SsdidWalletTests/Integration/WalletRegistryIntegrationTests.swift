@testable import SsdidCore
@preconcurrency import XCTest
@preconcurrency import LibOQS
@preconcurrency @testable import SsdidWallet

/// Comprehensive wallet<->registry integration tests covering the full identity lifecycle,
/// key rotation with pre-commitment, proof cross-check, challenge replay protection,
/// RFC 7807 error format verification, invalid proof rejection, and multi-algorithm support.
///
/// These tests run against the live SSDID registry at https://registry.ssdid.my
/// and are skipped when the registry is unreachable.
@MainActor
final class WalletRegistryIntegrationTests: XCTestCase {

    private var registryApi: RegistryApi!
    private var classical: ClassicalProvider!
    private var httpClient: SsdidHttpClient!

    // MARK: - Setup

    override func setUp() async throws {
        try await super.setUp()
        try XCTSkipUnless(RegistryAvailability.isReachable, "Registry unreachable - skipping integration tests")

        httpClient = SsdidHttpClient(registryURL: "https://registry.ssdid.my")
        registryApi = httpClient.registry
        classical = ClassicalProvider()
    }

    // MARK: - Helpers

    private func now() -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.string(from: Date())
    }

    private func didDocToDict(_ didDoc: DidDocument) -> [String: Any] {
        let encoder = JSONEncoder()
        guard let data = try? encoder.encode(didDoc),
              let dict = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            fatalError("Failed to convert DidDocument to dictionary")
        }
        return dict
    }

    private func createW3CProof(
        keyId: String,
        document: [String: Any],
        algorithm: Algorithm,
        privateKey: Data,
        provider: CryptoProvider,
        proofPurpose: String = "assertionMethod",
        challenge: String? = nil,
        domain: String? = nil
    ) throws -> Proof {
        let created = now()

        var proofOptions: [String: Any] = [
            "type": algorithm.proofType,
            "created": created,
            "verificationMethod": keyId,
            "proofPurpose": proofPurpose
        ]
        if let challenge = challenge { proofOptions["challenge"] = challenge }
        if let domain = domain { proofOptions["domain"] = domain }

        let optionsJson = JsonUtils.canonicalJson(proofOptions)
        let docJson = JsonUtils.canonicalJson(document)
        let optionsHash = SsdidCore.SHA3.sha256(Data(optionsJson.utf8))
        let docHash = SsdidCore.SHA3.sha256(Data(docJson.utf8))
        var payload = optionsHash
        payload.append(docHash)

        let signature = try provider.sign(algorithm: algorithm, privateKey: privateKey, data: payload)

        return Proof(
            type: algorithm.proofType,
            created: created,
            verificationMethod: keyId,
            proofPurpose: proofPurpose,
            proofValue: Multibase.encode(signature),
            domain: domain,
            challenge: challenge
        )
    }

    /// Holds registration state for cleanup.
    private struct RegisteredDid: @unchecked Sendable {
        let did: Did
        var keyId: String
        var privateKey: Data
        var publicKey: Data
        var pubMultibase: String
        let algorithm: Algorithm
        let provider: CryptoProvider
    }

    /// Register a DID and return registration state for further operations.
    private func registerDid(
        algorithm: Algorithm = .ED25519,
        provider: CryptoProvider? = nil
    ) async throws -> RegisteredDid {
        let prov = provider ?? classical!
        let keyPair = try prov.generateKeyPair(algorithm: algorithm)
        let did = Did.generate()
        let keyId = did.keyId(keyIndex: 1)
        let pubMultibase = Multibase.encode(keyPair.publicKey)

        let didDoc = DidDocument.build(did: did, keyId: keyId, algorithm: algorithm, publicKeyMultibase: pubMultibase)
        let proof = try createW3CProof(
            keyId: keyId,
            document: didDocToDict(didDoc),
            algorithm: algorithm,
            privateKey: keyPair.privateKey,
            provider: prov
        )
        _ = try await registryApi.registerDid(request: RegisterDidRequest(didDocument: didDoc, proof: proof))

        return RegisteredDid(
            did: did,
            keyId: keyId,
            privateKey: keyPair.privateKey,
            publicKey: keyPair.publicKey,
            pubMultibase: pubMultibase,
            algorithm: algorithm,
            provider: prov
        )
    }

    /// Deactivate a DID (for cleanup). Swallows exceptions.
    private func deactivateSafely(_ reg: RegisteredDid) async {
        do {
            let challengeResp = try await registryApi.createChallenge(did: reg.did.value)
            let deactivateData: [String: Any] = [
                "action": "deactivate",
                "did": reg.did.value
            ]
            let deactivateProof = try createW3CProof(
                keyId: reg.keyId,
                document: deactivateData,
                algorithm: reg.algorithm,
                privateKey: reg.privateKey,
                provider: reg.provider,
                proofPurpose: "capabilityInvocation",
                challenge: challengeResp.challenge,
                domain: challengeResp.domain
            )
            try await registryApi.deactivateDid(did: reg.did.value, request: DeactivateDidRequest(proof: deactivateProof))
        } catch {
            // Best-effort cleanup
        }
    }

    // MARK: - Flow 1: Full Identity Lifecycle

    func testFullIdentityLifecycle_createResolveUpdateDeactivate() async throws {
        var reg: RegisteredDid? = nil
        defer {
            if let r = reg {
                let task = Task { await deactivateSafely(r) }
                // Fire-and-forget cleanup; XCTest will wait for the test method to return
                _ = task
            }
        }

        // 1-2. Generate Ed25519 keypair and register DID
        reg = try await registerDid(algorithm: .ED25519)
        let regUnwrapped = reg!

        // 3-4. Resolve and verify it matches
        let resolved = try await registryApi.resolveDid(did: regUnwrapped.did.value)
        XCTAssertEqual(resolved.id, regUnwrapped.did.value)
        XCTAssertEqual(resolved.verificationMethod.count, 1)
        XCTAssertEqual(resolved.verificationMethod[0].id, regUnwrapped.keyId)
        XCTAssertEqual(resolved.verificationMethod[0].publicKeyMultibase, regUnwrapped.pubMultibase)

        // 5. Get challenge for update
        let challengeForUpdate = try await registryApi.createChallenge(did: regUnwrapped.did.value)
        XCTAssertFalse(challengeForUpdate.challenge.isEmpty)

        // 6. Update DID doc (add nextKeyHash)
        let nextKeyPair = try classical.generateKeyPair(algorithm: .ED25519)
        let nextKeyHash = Multibase.encode(SsdidCore.SHA3.sha256(nextKeyPair.publicKey))

        let updatedDoc = DidDocument(
            id: regUnwrapped.did.value,
            controller: regUnwrapped.did.value,
            verificationMethod: resolved.verificationMethod,
            authentication: [regUnwrapped.keyId],
            assertionMethod: [regUnwrapped.keyId],
            capabilityInvocation: [regUnwrapped.keyId],
            nextKeyHash: nextKeyHash
        )
        let updateProof = try createW3CProof(
            keyId: regUnwrapped.keyId,
            document: didDocToDict(updatedDoc),
            algorithm: .ED25519,
            privateKey: regUnwrapped.privateKey,
            provider: classical,
            proofPurpose: "capabilityInvocation",
            challenge: challengeForUpdate.challenge,
            domain: challengeForUpdate.domain
        )
        let updateResp = try await registryApi.updateDid(
            did: regUnwrapped.did.value,
            request: UpdateDidRequest(didDocument: updatedDoc, proof: updateProof)
        )
        XCTAssertEqual(updateResp.did, regUnwrapped.did.value)

        // 7. Resolve again and assert nextKeyHash present
        let resolvedAfterUpdate = try await registryApi.resolveDid(did: regUnwrapped.did.value)
        XCTAssertEqual(resolvedAfterUpdate.nextKeyHash, nextKeyHash)

        // 8. Get challenge for deactivation
        let challengeForDeactivation = try await registryApi.createChallenge(did: regUnwrapped.did.value)
        XCTAssertFalse(challengeForDeactivation.challenge.isEmpty)

        // 9. Deactivate
        let deactivateData: [String: Any] = [
            "action": "deactivate",
            "did": regUnwrapped.did.value
        ]
        let deactivateProof = try createW3CProof(
            keyId: regUnwrapped.keyId,
            document: deactivateData,
            algorithm: .ED25519,
            privateKey: regUnwrapped.privateKey,
            provider: classical,
            proofPurpose: "capabilityInvocation",
            challenge: challengeForDeactivation.challenge,
            domain: challengeForDeactivation.domain
        )
        try await registryApi.deactivateDid(
            did: regUnwrapped.did.value,
            request: DeactivateDidRequest(proof: deactivateProof)
        )

        // 10. Resolve after deactivation should fail (404/410)
        do {
            _ = try await registryApi.resolveDid(did: regUnwrapped.did.value)
            XCTFail("Expected resolve to fail after deactivation")
        } catch {
            // Expected: DID should not be resolvable after deactivation
        }

        reg = nil // Already deactivated, skip cleanup
    }

    // MARK: - Flow 2: Key Rotation with Pre-Commitment

    func testKeyRotationWithPreCommitmentHash() async throws {
        var reg: RegisteredDid? = nil
        defer {
            if let r = reg {
                Task { await deactivateSafely(r) }
            }
        }

        // 1. Register identity
        reg = try await registerDid(algorithm: .ED25519)
        var regUnwrapped = reg!

        // 2. Generate next keypair, compute SHA3-256 hash of the new public key
        let nextKeyPair = try classical.generateKeyPair(algorithm: .ED25519)
        let nextKeyHash = Multibase.encode(SsdidCore.SHA3.sha256(nextKeyPair.publicKey))

        // 3. Update DID doc with nextKeyHash
        let challenge1 = try await registryApi.createChallenge(did: regUnwrapped.did.value)
        let docWithNextKeyHash = DidDocument(
            id: regUnwrapped.did.value,
            controller: regUnwrapped.did.value,
            verificationMethod: [
                VerificationMethod(
                    id: regUnwrapped.keyId,
                    type: Algorithm.ED25519.w3cType,
                    controller: regUnwrapped.did.value,
                    publicKeyMultibase: regUnwrapped.pubMultibase
                )
            ],
            authentication: [regUnwrapped.keyId],
            assertionMethod: [regUnwrapped.keyId],
            capabilityInvocation: [regUnwrapped.keyId],
            nextKeyHash: nextKeyHash
        )
        let proof1 = try createW3CProof(
            keyId: regUnwrapped.keyId,
            document: didDocToDict(docWithNextKeyHash),
            algorithm: .ED25519,
            privateKey: regUnwrapped.privateKey,
            provider: classical,
            proofPurpose: "capabilityInvocation",
            challenge: challenge1.challenge,
            domain: challenge1.domain
        )
        let updateResp1 = try await registryApi.updateDid(
            did: regUnwrapped.did.value,
            request: UpdateDidRequest(didDocument: docWithNextKeyHash, proof: proof1)
        )
        XCTAssertEqual(updateResp1.did, regUnwrapped.did.value)

        // 4. Resolve and assert nextKeyHash is present
        let resolved1 = try await registryApi.resolveDid(did: regUnwrapped.did.value)
        XCTAssertEqual(resolved1.nextKeyHash, nextKeyHash)

        // 5-6. Generate new DID doc with promoted key and update
        let newKeyId = regUnwrapped.did.keyId(keyIndex: 2)
        let newPubMultibase = Multibase.encode(nextKeyPair.publicKey)
        let challenge2 = try await registryApi.createChallenge(did: regUnwrapped.did.value)
        let rotatedDoc = DidDocument(
            id: regUnwrapped.did.value,
            controller: regUnwrapped.did.value,
            verificationMethod: [
                VerificationMethod(
                    id: newKeyId,
                    type: Algorithm.ED25519.w3cType,
                    controller: regUnwrapped.did.value,
                    publicKeyMultibase: newPubMultibase
                )
            ],
            authentication: [newKeyId],
            assertionMethod: [newKeyId],
            capabilityInvocation: [newKeyId]
            // nextKeyHash cleared after rotation
        )
        // Sign with the OLD key (current authorized key) to authorize the rotation
        let proof2 = try createW3CProof(
            keyId: regUnwrapped.keyId,
            document: didDocToDict(rotatedDoc),
            algorithm: .ED25519,
            privateKey: regUnwrapped.privateKey,
            provider: classical,
            proofPurpose: "capabilityInvocation",
            challenge: challenge2.challenge,
            domain: challenge2.domain
        )
        let updateResp2 = try await registryApi.updateDid(
            did: regUnwrapped.did.value,
            request: UpdateDidRequest(didDocument: rotatedDoc, proof: proof2)
        )
        XCTAssertEqual(updateResp2.did, regUnwrapped.did.value)

        // 7. Resolve and assert new key is active, nextKeyHash is cleared
        let resolved2 = try await registryApi.resolveDid(did: regUnwrapped.did.value)
        XCTAssertEqual(resolved2.verificationMethod.count, 1)
        XCTAssertEqual(resolved2.verificationMethod[0].id, newKeyId)
        XCTAssertEqual(resolved2.verificationMethod[0].publicKeyMultibase, newPubMultibase)
        XCTAssertNil(resolved2.nextKeyHash)

        // Update reg for cleanup with new key
        regUnwrapped.keyId = newKeyId
        regUnwrapped.privateKey = nextKeyPair.privateKey
        regUnwrapped.publicKey = nextKeyPair.publicKey
        regUnwrapped.pubMultibase = newPubMultibase
        reg = regUnwrapped
    }

    // MARK: - Flow 3: Proof Cross-Check

    func testProofVerificationCrossCheck_signingPayloadFormatMatchesRegistry() async throws {
        var reg: RegisteredDid? = nil
        defer {
            if let r = reg {
                Task { await deactivateSafely(r) }
            }
        }

        // Explicitly build and verify the entire W3C Data Integrity signing payload
        let keyPair = try classical.generateKeyPair(algorithm: .ED25519)
        let did = Did.generate()
        let keyId = did.keyId(keyIndex: 1)
        let pubMultibase = Multibase.encode(keyPair.publicKey)

        let didDoc = DidDocument.build(did: did, keyId: keyId, algorithm: .ED25519, publicKeyMultibase: pubMultibase)
        let docDict = didDocToDict(didDoc)

        // 1-2. Build canonical JSON of document and proof options
        let created = now()
        let proofOptions: [String: Any] = [
            "type": Algorithm.ED25519.proofType,
            "created": created,
            "verificationMethod": keyId,
            "proofPurpose": "assertionMethod"
        ]

        let canonicalOptions = JsonUtils.canonicalJson(proofOptions)
        let canonicalDoc = JsonUtils.canonicalJson(docDict)

        // 3. SHA3-256 hash both
        let optionsHash = SsdidCore.SHA3.sha256(Data(canonicalOptions.utf8))
        let docHash = SsdidCore.SHA3.sha256(Data(canonicalDoc.utf8))

        // 4. Concatenate hashes
        var payload = optionsHash
        payload.append(docHash)
        XCTAssertEqual(payload.count, 64) // 32 + 32 bytes

        // 5. Sign with private key
        let signature = try classical.sign(algorithm: .ED25519, privateKey: keyPair.privateKey, data: payload)

        // 6. Multibase encode
        let proofValue = Multibase.encode(signature)
        XCTAssertTrue(proofValue.hasPrefix("u"), "Multibase should use base64url prefix 'u'")

        // 7. Submit to registry and assert proof accepted
        let proof = Proof(
            type: Algorithm.ED25519.proofType,
            created: created,
            verificationMethod: keyId,
            proofPurpose: "assertionMethod",
            proofValue: proofValue
        )
        let resp = try await registryApi.registerDid(
            request: RegisterDidRequest(didDocument: didDoc, proof: proof)
        )
        XCTAssertEqual(resp.did, did.value)

        reg = RegisteredDid(
            did: did,
            keyId: keyId,
            privateKey: keyPair.privateKey,
            publicKey: keyPair.publicKey,
            pubMultibase: pubMultibase,
            algorithm: .ED25519,
            provider: classical
        )
    }

    // MARK: - Flow 4: Challenge Replay Protection

    func testChallengeCannotBeReused_replayProtection() async throws {
        var reg: RegisteredDid? = nil
        defer {
            if let r = reg {
                Task { await deactivateSafely(r) }
            }
        }

        // 1. Register identity
        reg = try await registerDid(algorithm: .ED25519)
        let regUnwrapped = reg!

        // 2. Get challenge
        let challengeResp = try await registryApi.createChallenge(did: regUnwrapped.did.value)
        let challenge = challengeResp.challenge
        XCTAssertFalse(challenge.isEmpty)

        // 3. Use challenge in UPDATE -> success
        let keyPair2 = try classical.generateKeyPair(algorithm: .ED25519)
        let keyId2 = regUnwrapped.did.keyId(keyIndex: 2)
        let pubMultibase2 = Multibase.encode(keyPair2.publicKey)

        let updatedDoc = DidDocument(
            id: regUnwrapped.did.value,
            controller: regUnwrapped.did.value,
            verificationMethod: [
                VerificationMethod(
                    id: regUnwrapped.keyId,
                    type: Algorithm.ED25519.w3cType,
                    controller: regUnwrapped.did.value,
                    publicKeyMultibase: regUnwrapped.pubMultibase
                ),
                VerificationMethod(
                    id: keyId2,
                    type: Algorithm.ED25519.w3cType,
                    controller: regUnwrapped.did.value,
                    publicKeyMultibase: pubMultibase2
                )
            ],
            authentication: [regUnwrapped.keyId, keyId2],
            assertionMethod: [regUnwrapped.keyId, keyId2],
            capabilityInvocation: [regUnwrapped.keyId, keyId2]
        )
        let updateProof = try createW3CProof(
            keyId: regUnwrapped.keyId,
            document: didDocToDict(updatedDoc),
            algorithm: .ED25519,
            privateKey: regUnwrapped.privateKey,
            provider: classical,
            proofPurpose: "capabilityInvocation",
            challenge: challenge,
            domain: challengeResp.domain
        )
        let updateResp = try await registryApi.updateDid(
            did: regUnwrapped.did.value,
            request: UpdateDidRequest(didDocument: updatedDoc, proof: updateProof)
        )
        XCTAssertEqual(updateResp.did, regUnwrapped.did.value)

        // 4. Use SAME challenge in another UPDATE -> should fail (replay)
        let anotherDoc = DidDocument(
            id: regUnwrapped.did.value,
            controller: regUnwrapped.did.value,
            verificationMethod: [
                VerificationMethod(
                    id: regUnwrapped.keyId,
                    type: Algorithm.ED25519.w3cType,
                    controller: regUnwrapped.did.value,
                    publicKeyMultibase: regUnwrapped.pubMultibase
                )
            ],
            authentication: [regUnwrapped.keyId],
            assertionMethod: [regUnwrapped.keyId],
            capabilityInvocation: [regUnwrapped.keyId]
        )
        let replayProof = try createW3CProof(
            keyId: regUnwrapped.keyId,
            document: didDocToDict(anotherDoc),
            algorithm: .ED25519,
            privateKey: regUnwrapped.privateKey,
            provider: classical,
            proofPurpose: "capabilityInvocation",
            challenge: challenge, // Reused challenge!
            domain: challengeResp.domain
        )
        do {
            _ = try await registryApi.updateDid(
                did: regUnwrapped.did.value,
                request: UpdateDidRequest(didDocument: anotherDoc, proof: replayProof)
            )
            XCTFail("Expected replay challenge to be rejected")
        } catch let error as HttpError {
            // Verify the error is an HTTP error (401 or 422)
            if case .requestFailed(let statusCode, _) = error {
                XCTAssertTrue(
                    statusCode == 401 || statusCode == 422,
                    "Expected HTTP 401 or 422 for replay, got \(statusCode)"
                )
            }
        }
    }

    // MARK: - Flow 5: RFC 7807 Error Format

    func testErrorResponsesFollowRFC7807Format() async throws {
        // 1. Attempt to resolve non-existent DID -> expect 404
        let nonExistentDid = "did:ssdid:nonexistent_rfc7807_test"
        do {
            _ = try await registryApi.resolveDid(did: nonExistentDid)
            XCTFail("Expected resolve of non-existent DID to fail")
        } catch let error as HttpError {
            guard case .requestFailed(let statusCode, let message) = error else {
                XCTFail("Expected requestFailed error, got \(error)")
                return
            }

            // 2. Assert 404 status
            XCTAssertEqual(statusCode, 404)

            // 3. Parse error body -> assert RFC 7807 fields
            guard let body = message, !body.isEmpty else {
                XCTFail("Error body should not be empty")
                return
            }

            guard let bodyData = body.data(using: .utf8),
                  let errorJson = try? JSONSerialization.jsonObject(with: bodyData) as? [String: Any] else {
                XCTFail("Error body should be valid JSON")
                return
            }

            // RFC 7807 requires at least "type" or "title", and "status" or "detail"
            let hasTypeOrTitle = errorJson["type"] != nil || errorJson["title"] != nil
            let hasStatusOrDetail = errorJson["status"] != nil || errorJson["detail"] != nil
            XCTAssertTrue(hasTypeOrTitle, "RFC 7807 error should contain 'type' or 'title'")
            XCTAssertTrue(hasStatusOrDetail, "RFC 7807 error should contain 'status' or 'detail'")
        }
    }

    // MARK: - Flow 6: Invalid Proof Rejection

    func testRegistrationWithWrongKeySignatureIsRejected() async throws {
        // 1. Generate two different keypairs
        let keyPairA = try classical.generateKeyPair(algorithm: .ED25519)
        let keyPairB = try classical.generateKeyPair(algorithm: .ED25519)

        // 2. Build DID doc with keypair A's public key
        let did = Did.generate()
        let keyId = did.keyId(keyIndex: 1)
        let pubMultibaseA = Multibase.encode(keyPairA.publicKey)
        let didDoc = DidDocument.build(did: did, keyId: keyId, algorithm: .ED25519, publicKeyMultibase: pubMultibaseA)

        // 3. Sign proof with keypair B's private key (wrong key)
        let badProof = try createW3CProof(
            keyId: keyId,
            document: didDocToDict(didDoc),
            algorithm: .ED25519,
            privateKey: keyPairB.privateKey,
            provider: classical
        )

        // 4. Submit -> assert rejection
        do {
            _ = try await registryApi.registerDid(
                request: RegisterDidRequest(didDocument: didDoc, proof: badProof)
            )
            XCTFail("Expected registration with invalid proof to fail")
        } catch let error as HttpError {
            if case .requestFailed(let statusCode, _) = error {
                // Registry may return 401 (proof verification failed) or 422 or 429 (rate limited)
                XCTAssertTrue(
                    [401, 422, 429].contains(statusCode),
                    "Expected HTTP 401, 422, or 429 for invalid proof, got \(statusCode)"
                )
            }
        }
    }

    // MARK: - Flow 7: Multi-Algorithm

    func testRegisterAndResolveWithEd25519() async throws {
        var reg: RegisteredDid? = nil
        defer {
            if let r = reg {
                Task { await deactivateSafely(r) }
            }
        }

        reg = try await registerDid(algorithm: .ED25519)
        let regUnwrapped = reg!

        let resolved = try await registryApi.resolveDid(did: regUnwrapped.did.value)
        XCTAssertEqual(resolved.id, regUnwrapped.did.value)
        XCTAssertEqual(resolved.verificationMethod.count, 1)
        XCTAssertEqual(resolved.verificationMethod[0].type, Algorithm.ED25519.w3cType)
        XCTAssertEqual(resolved.verificationMethod[0].publicKeyMultibase, regUnwrapped.pubMultibase)
    }

    func testRegisterAndResolveWithEcdsaP256() async throws {
        var reg: RegisteredDid? = nil
        defer {
            if let r = reg {
                Task { await deactivateSafely(r) }
            }
        }

        reg = try await registerDid(algorithm: .ECDSA_P256)
        let regUnwrapped = reg!

        let resolved = try await registryApi.resolveDid(did: regUnwrapped.did.value)
        XCTAssertEqual(resolved.id, regUnwrapped.did.value)
        XCTAssertEqual(resolved.verificationMethod.count, 1)
        XCTAssertEqual(resolved.verificationMethod[0].type, Algorithm.ECDSA_P256.w3cType)
        XCTAssertEqual(resolved.verificationMethod[0].publicKeyMultibase, regUnwrapped.pubMultibase)
    }

    func testRegisterAndResolveWithEcdsaP384() async throws {
        var reg: RegisteredDid? = nil
        defer {
            if let r = reg {
                Task { await deactivateSafely(r) }
            }
        }

        reg = try await registerDid(algorithm: .ECDSA_P384)
        let regUnwrapped = reg!

        let resolved = try await registryApi.resolveDid(did: regUnwrapped.did.value)
        XCTAssertEqual(resolved.id, regUnwrapped.did.value)
        XCTAssertEqual(resolved.verificationMethod.count, 1)
        XCTAssertEqual(resolved.verificationMethod[0].type, Algorithm.ECDSA_P384.w3cType)
        XCTAssertEqual(resolved.verificationMethod[0].publicKeyMultibase, regUnwrapped.pubMultibase)
    }
}
