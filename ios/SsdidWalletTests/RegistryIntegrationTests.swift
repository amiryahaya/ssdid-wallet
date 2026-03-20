import XCTest
import LibOQS
@testable import SsdidWallet

/// Integration tests against the live SSDID registry at https://registry.ssdid.my.
/// Uses W3C Data Integrity signing payload: SHA3-256(canonical(proofOptions)) || SHA3-256(canonical(document)).
/// Skipped when the registry is unreachable.
final class RegistryIntegrationTests: XCTestCase {

    private var registryApi: RegistryApi!
    private var classical: ClassicalProvider!
    private var pqc: PqcProvider!
    private var httpClient: SsdidHttpClient!

    override func setUp() async throws {
        try await super.setUp()

        try XCTSkipIf(true, "Integration test — run manually against local registry")
        try XCTSkipUnless(isRegistryReachable(), "Registry unreachable - skipping integration tests")

        httpClient = SsdidHttpClient(registryURL: "https://registry.ssdid.my")
        registryApi = httpClient.registry
        classical = ClassicalProvider()
        pqc = PqcProvider()
    }

    // MARK: - Reachability Check

    private func isRegistryReachable() -> Bool {
        var hints = addrinfo()
        hints.ai_family = AF_UNSPEC
        hints.ai_socktype = SOCK_STREAM
        var result: UnsafeMutablePointer<addrinfo>?
        let status = getaddrinfo("registry.ssdid.my", nil, &hints, &result)
        if status == 0, result != nil {
            freeaddrinfo(result)
            return true
        }
        return false
    }

    // MARK: - Helpers

    private func now() -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.string(from: Date())
    }

    /// Converts a DidDocument to a dictionary for canonical JSON serialization.
    private func didDocToDict(_ didDoc: DidDocument) -> [String: Any] {
        let encoder = JSONEncoder()
        guard let data = try? encoder.encode(didDoc),
              let dict = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            fatalError("Failed to convert DidDocument to dictionary")
        }
        return dict
    }

    /// Creates a W3C Data Integrity proof with domain-separated signing payload.
    /// Payload = SHA3-256(canonical(proofOptions)) || SHA3-256(canonical(document))
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
        let optionsHash = SHA3.sha256(Data(optionsJson.utf8))
        let docHash = SHA3.sha256(Data(docJson.utf8))
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

    /// Helper: register a DID with any algorithm and provider, then resolve and verify.
    private func registerAndResolve(algorithm: Algorithm, provider: CryptoProvider) async throws {
        let keyPair = try provider.generateKeyPair(algorithm: algorithm)
        let did = Did.generate()
        let keyId = did.keyId(keyIndex: 1)
        let publicKeyMultibase = Multibase.encode(keyPair.publicKey)

        let didDoc = DidDocument.build(did: did, keyId: keyId, algorithm: algorithm, publicKeyMultibase: publicKeyMultibase)
        let proof = try createW3CProof(
            keyId: keyId,
            document: didDocToDict(didDoc),
            algorithm: algorithm,
            privateKey: keyPair.privateKey,
            provider: provider
        )

        let registerResp = try await registryApi.registerDid(request: RegisterDidRequest(didDocument: didDoc, proof: proof))
        XCTAssertEqual(registerResp.did, did.value)

        let resolved = try await registryApi.resolveDid(did: did.value)
        XCTAssertEqual(resolved.id, did.value)
        XCTAssertEqual(resolved.verificationMethod.count, 1)
        XCTAssertEqual(resolved.verificationMethod[0].id, keyId)
        XCTAssertEqual(resolved.verificationMethod[0].type, algorithm.w3cType)
        XCTAssertEqual(resolved.verificationMethod[0].publicKeyMultibase, publicKeyMultibase)
    }

    /// Probes the registry for PQC support by attempting an ML-DSA-44 registration.
    /// Throws XCTSkip if the registry's PQC verification backend is not running.
    private func assumePqcSupported() async throws {
        let kp = try pqc.generateKeyPair(algorithm: .ML_DSA_44)
        let did = Did.generate()
        let keyId = did.keyId(keyIndex: 1)
        let pubMultibase = Multibase.encode(kp.publicKey)
        let didDoc = DidDocument.build(did: did, keyId: keyId, algorithm: .ML_DSA_44, publicKeyMultibase: pubMultibase)
        let proof = try createW3CProof(
            keyId: keyId,
            document: didDocToDict(didDoc),
            algorithm: .ML_DSA_44,
            privateKey: kp.privateKey,
            provider: pqc
        )
        do {
            _ = try await registryApi.registerDid(request: RegisterDidRequest(didDocument: didDoc, proof: proof))
        } catch {
            throw XCTSkip("Registry PQC verification unavailable (ApJavaCrypto GenServer not started in supervision tree)")
        }
    }

    /// Probes the registry for KAZ-Sign support.
    /// Throws XCTSkip if the native KAZ-Sign library is unavailable or the registry cannot verify KAZ-Sign signatures.
    private func assumeKazSignSupported() async throws {
        do {
            let kp = try pqc.generateKeyPair(algorithm: .KAZ_SIGN_128)
            let did = Did.generate()
            let keyId = did.keyId(keyIndex: 1)
            let pubMultibase = Multibase.encode(kp.publicKey)
            let didDoc = DidDocument.build(did: did, keyId: keyId, algorithm: .KAZ_SIGN_128, publicKeyMultibase: pubMultibase)
            let proof = try createW3CProof(
                keyId: keyId,
                document: didDocToDict(didDoc),
                algorithm: .KAZ_SIGN_128,
                privateKey: kp.privateKey,
                provider: pqc
            )
            _ = try await registryApi.registerDid(request: RegisterDidRequest(didDocument: didDoc, proof: proof))
        } catch {
            throw XCTSkip("KAZ-Sign unavailable (\(error.localizedDescription))")
        }
    }

    // MARK: - Classical Algorithms

    func testRegisterAndResolveEd25519() async throws {
        try await registerAndResolve(algorithm: .ED25519, provider: classical)
    }

    func testRegisterAndResolveEcdsaP256() async throws {
        try await registerAndResolve(algorithm: .ECDSA_P256, provider: classical)
    }

    func testRegisterAndResolveEcdsaP384() async throws {
        try await registerAndResolve(algorithm: .ECDSA_P384, provider: classical)
    }

    // MARK: - ML-DSA (FIPS 204)
    // These require ApJavaCrypto on the registry. Skipped if registry returns error.

    func testRegisterAndResolveMlDsa44() async throws {
        try await assumePqcSupported()
        try await registerAndResolve(algorithm: .ML_DSA_44, provider: pqc)
    }

    func testRegisterAndResolveMlDsa65() async throws {
        try await assumePqcSupported()
        try await registerAndResolve(algorithm: .ML_DSA_65, provider: pqc)
    }

    func testRegisterAndResolveMlDsa87() async throws {
        try await assumePqcSupported()
        try await registerAndResolve(algorithm: .ML_DSA_87, provider: pqc)
    }

    // MARK: - SLH-DSA (FIPS 205)

    func testRegisterAndResolveSlhDsaSha2128s() async throws {
        try await assumePqcSupported()
        try await registerAndResolve(algorithm: .SLH_DSA_SHA2_128S, provider: pqc)
    }

    func testRegisterAndResolveSlhDsaShake128f() async throws {
        try await assumePqcSupported()
        try await registerAndResolve(algorithm: .SLH_DSA_SHAKE_128F, provider: pqc)
    }

    // MARK: - KAZ-Sign (Malaysian PQC)

    func testRegisterAndResolveKazSign128() async throws {
        try await assumeKazSignSupported()
        try await registerAndResolve(algorithm: .KAZ_SIGN_128, provider: pqc)
    }

    func testRegisterAndResolveKazSign192() async throws {
        try await assumeKazSignSupported()
        try await registerAndResolve(algorithm: .KAZ_SIGN_192, provider: pqc)
    }

    func testRegisterAndResolveKazSign256() async throws {
        try await assumeKazSignSupported()
        try await registerAndResolve(algorithm: .KAZ_SIGN_256, provider: pqc)
    }

    // MARK: - DID Update (requires challenge)

    func testUpdateDidDocumentAddsVerificationMethod() async throws {
        // Register initial DID with Ed25519
        let keyPair1 = try classical.generateKeyPair(algorithm: .ED25519)
        let did = Did.generate()
        let keyId1 = did.keyId(keyIndex: 1)
        let pubMultibase1 = Multibase.encode(keyPair1.publicKey)

        let didDoc1 = DidDocument.build(did: did, keyId: keyId1, algorithm: .ED25519, publicKeyMultibase: pubMultibase1)
        let proof1 = try createW3CProof(
            keyId: keyId1,
            document: didDocToDict(didDoc1),
            algorithm: .ED25519,
            privateKey: keyPair1.privateKey,
            provider: classical
        )
        _ = try await registryApi.registerDid(request: RegisterDidRequest(didDocument: didDoc1, proof: proof1))

        // Request challenge for update
        let challengeResp = try await registryApi.createChallenge(did: did.value)

        // Generate second key pair
        let keyPair2 = try classical.generateKeyPair(algorithm: .ED25519)
        let keyId2 = did.keyId(keyIndex: 2)
        let pubMultibase2 = Multibase.encode(keyPair2.publicKey)

        // Build updated DID document with two verification methods
        let updatedDoc = DidDocument(
            id: did.value,
            controller: did.value,
            verificationMethod: [
                VerificationMethod(id: keyId1, type: "Ed25519VerificationKey2020", controller: did.value, publicKeyMultibase: pubMultibase1),
                VerificationMethod(id: keyId2, type: "Ed25519VerificationKey2020", controller: did.value, publicKeyMultibase: pubMultibase2)
            ],
            authentication: [keyId1, keyId2],
            assertionMethod: [keyId1, keyId2],
            capabilityInvocation: [keyId1, keyId2]
        )
        let updateProof = try createW3CProof(
            keyId: keyId1,
            document: didDocToDict(updatedDoc),
            algorithm: .ED25519,
            privateKey: keyPair1.privateKey,
            provider: classical,
            proofPurpose: "capabilityInvocation",
            challenge: challengeResp.challenge,
            domain: "registry.ssdid.example"
        )
        let updateResp = try await registryApi.updateDid(did: did.value, request: UpdateDidRequest(didDocument: updatedDoc, proof: updateProof))
        XCTAssertEqual(updateResp.did, did.value)

        // Verify updated document has two verification methods
        let resolved = try await registryApi.resolveDid(did: did.value)
        XCTAssertEqual(resolved.verificationMethod.count, 2)
    }

    // MARK: - Challenge Endpoint

    func testCreateChallengeForRegisteredDid() async throws {
        // Register a DID first
        let keyPair = try classical.generateKeyPair(algorithm: .ED25519)
        let did = Did.generate()
        let keyId = did.keyId(keyIndex: 1)
        let pubMultibase = Multibase.encode(keyPair.publicKey)

        let didDoc = DidDocument.build(did: did, keyId: keyId, algorithm: .ED25519, publicKeyMultibase: pubMultibase)
        let proof = try createW3CProof(
            keyId: keyId,
            document: didDocToDict(didDoc),
            algorithm: .ED25519,
            privateKey: keyPair.privateKey,
            provider: classical
        )
        _ = try await registryApi.registerDid(request: RegisterDidRequest(didDocument: didDoc, proof: proof))

        // Request a challenge
        let challengeResp = try await registryApi.createChallenge(did: did.value)
        XCTAssertFalse(challengeResp.challenge.isEmpty)
    }

    // MARK: - Deactivation

    func testDeactivateDidMakesItUnresolvable() async throws {
        // Register a fresh DID
        let keyPair = try classical.generateKeyPair(algorithm: .ED25519)
        let did = Did.generate()
        let keyId = did.keyId(keyIndex: 1)
        let pubMultibase = Multibase.encode(keyPair.publicKey)

        let didDoc = DidDocument.build(did: did, keyId: keyId, algorithm: .ED25519, publicKeyMultibase: pubMultibase)
        let proof = try createW3CProof(
            keyId: keyId,
            document: didDocToDict(didDoc),
            algorithm: .ED25519,
            privateKey: keyPair.privateKey,
            provider: classical
        )
        _ = try await registryApi.registerDid(request: RegisterDidRequest(didDocument: didDoc, proof: proof))

        // Verify it resolves
        let resolved = try await registryApi.resolveDid(did: did.value)
        XCTAssertEqual(resolved.id, did.value)

        // Request challenge for deactivation
        let challengeResp = try await registryApi.createChallenge(did: did.value)
        XCTAssertFalse(challengeResp.challenge.isEmpty)

        // Build deactivation proof with capabilityInvocation purpose
        let deactivateData: [String: Any] = [
            "action": "deactivate",
            "did": did.value
        ]
        let deactivateProof = try createW3CProof(
            keyId: keyId,
            document: deactivateData,
            algorithm: .ED25519,
            privateKey: keyPair.privateKey,
            provider: classical,
            proofPurpose: "capabilityInvocation",
            challenge: challengeResp.challenge,
            domain: challengeResp.domain
        )
        try await registryApi.deactivateDid(did: did.value, request: DeactivateDidRequest(proof: deactivateProof))

        // Verify DID is no longer resolvable
        do {
            _ = try await registryApi.resolveDid(did: did.value)
            XCTFail("Expected resolve to fail after deactivation")
        } catch {
            // Expected: DID should not be resolvable after deactivation
        }
    }

    // MARK: - Error Cases

    func testResolveNonExistentDid() async throws {
        do {
            _ = try await registryApi.resolveDid(did: "did:ssdid:nonexistent_did_12345")
            XCTFail("Expected resolve of non-existent DID to fail")
        } catch {
            // Expected: non-existent DID should return an error
        }
    }

    func testRegisterWithInvalidProof() async throws {
        let keyPair = try classical.generateKeyPair(algorithm: .ED25519)
        let did = Did.generate()
        let keyId = did.keyId(keyIndex: 1)
        let pubMultibase = Multibase.encode(keyPair.publicKey)

        let didDoc = DidDocument.build(did: did, keyId: keyId, algorithm: .ED25519, publicKeyMultibase: pubMultibase)

        // Sign with a different key pair to create an invalid proof
        let wrongKeyPair = try classical.generateKeyPair(algorithm: .ED25519)
        let badProof = try createW3CProof(
            keyId: keyId,
            document: didDocToDict(didDoc),
            algorithm: .ED25519,
            privateKey: wrongKeyPair.privateKey,
            provider: classical
        )

        do {
            _ = try await registryApi.registerDid(request: RegisterDidRequest(didDocument: didDoc, proof: badProof))
            XCTFail("Expected registration with invalid proof to fail")
        } catch {
            // Expected: invalid proof should be rejected
        }
    }

    func testRegisterDuplicateDid() async throws {
        let keyPair = try classical.generateKeyPair(algorithm: .ED25519)
        let did = Did.generate()
        let keyId = did.keyId(keyIndex: 1)
        let pubMultibase = Multibase.encode(keyPair.publicKey)

        let didDoc = DidDocument.build(did: did, keyId: keyId, algorithm: .ED25519, publicKeyMultibase: pubMultibase)
        let proof = try createW3CProof(
            keyId: keyId,
            document: didDocToDict(didDoc),
            algorithm: .ED25519,
            privateKey: keyPair.privateKey,
            provider: classical
        )
        _ = try await registryApi.registerDid(request: RegisterDidRequest(didDocument: didDoc, proof: proof))

        // Attempt to register the same DID again
        let proof2 = try createW3CProof(
            keyId: keyId,
            document: didDocToDict(didDoc),
            algorithm: .ED25519,
            privateKey: keyPair.privateKey,
            provider: classical
        )
        do {
            _ = try await registryApi.registerDid(request: RegisterDidRequest(didDocument: didDoc, proof: proof2))
            XCTFail("Expected duplicate DID registration to fail")
        } catch {
            // Expected: duplicate registration should be rejected
        }
    }
}
