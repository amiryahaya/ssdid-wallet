import XCTest
import zlib
@testable import SsdidWallet

/// 9 unit tests covering offline verification fallback logic (UC-7, UC-9–13).
/// Uses real ClassicalProvider for Ed25519 signatures, MockVerifier for online path,
/// and InMemoryBundleStore for bundle storage.
final class OfflineVerificationTests: XCTestCase {

    private var bundleStore: InMemoryBundleStore!
    private var offlineVerifier: OfflineVerifier!
    private var classicalProvider: ClassicalProvider!
    private var pqcProvider: MockCryptoProvider!

    override func setUp() {
        super.setUp()
        bundleStore = InMemoryBundleStore()
        classicalProvider = ClassicalProvider()
        pqcProvider = MockCryptoProvider()
        offlineVerifier = OfflineVerifier(
            classicalProvider: classicalProvider,
            pqcProvider: pqcProvider,
            bundleStore: bundleStore
        )
    }

    override func tearDown() {
        bundleStore.clear()
        super.tearDown()
    }

    // MARK: - Helper

    private func makeOrchestrator(mockVerifier: MockVerifier) -> VerificationOrchestrator {
        VerificationOrchestrator(
            onlineVerifier: mockVerifier,
            offlineVerifier: offlineVerifier,
            bundleStore: bundleStore
        )
    }

    // MARK: - UC-7: Network error + fresh bundle → verifiedOffline

    func testNetworkError_freshBundle_returnsVerifiedOffline() async throws {
        let (publicKeyMultibase, privateKey) = try OfflineTestHelper.createKeyPair()
        let issuerDid = "did:ssdid:issuer001"
        let keyId = "\(issuerDid)#key-1"

        let didDoc = OfflineTestHelper.createDidDocument(
            did: issuerDid,
            keyId: keyId,
            publicKeyMultibase: publicKeyMultibase
        )
        let bundle = OfflineTestHelper.createBundle(
            issuerDid: issuerDid,
            didDocument: didDoc,
            freshnessRatio: 0.1 // 10% of TTL consumed — very fresh
        )
        try await bundleStore.saveBundle(bundle)

        let credential = try OfflineTestHelper.createTestCredential(
            issuerDid: issuerDid,
            keyId: keyId,
            privateKey: privateKey
        )

        let mockVerifier = MockVerifier(shouldThrow: URLError(.notConnectedToInternet))
        let orchestrator = makeOrchestrator(mockVerifier: mockVerifier)

        let result = await orchestrator.verify(credential: credential)

        XCTAssertEqual(result.status, .verifiedOffline)
        XCTAssertEqual(result.source, .offline)
    }

    // MARK: - UC-9: Network error + stale bundle → degraded

    func testNetworkError_staleBundle_returnsDegraded() async throws {
        let (publicKeyMultibase, privateKey) = try OfflineTestHelper.createKeyPair()
        let issuerDid = "did:ssdid:issuer002"
        let keyId = "\(issuerDid)#key-1"

        let didDoc = OfflineTestHelper.createDidDocument(
            did: issuerDid,
            keyId: keyId,
            publicKeyMultibase: publicKeyMultibase
        )
        // Stale: freshnessRatio > 1.0 (bundle is expired)
        let staleBundle = OfflineTestHelper.createStaleBundle(
            issuerDid: issuerDid,
            didDocument: didDoc
        )
        try await bundleStore.saveBundle(staleBundle)

        let credential = try OfflineTestHelper.createTestCredential(
            issuerDid: issuerDid,
            keyId: keyId,
            privateKey: privateKey
        )

        let mockVerifier = MockVerifier(shouldThrow: URLError(.notConnectedToInternet))
        let orchestrator = makeOrchestrator(mockVerifier: mockVerifier)

        let result = await orchestrator.verify(credential: credential)

        XCTAssertEqual(result.status, .degraded)
        XCTAssertEqual(result.source, .offline)
    }

    // MARK: - UC-10: Network error + no bundle → failed

    func testNetworkError_noBundle_returnsFailed() async throws {
        let (_, privateKey) = try OfflineTestHelper.createKeyPair()
        let issuerDid = "did:ssdid:issuer003"
        let keyId = "\(issuerDid)#key-1"

        // No bundle stored for this issuer
        let credential = try OfflineTestHelper.createTestCredential(
            issuerDid: issuerDid,
            keyId: keyId,
            privateKey: privateKey
        )

        let mockVerifier = MockVerifier(shouldThrow: URLError(.notConnectedToInternet))
        let orchestrator = makeOrchestrator(mockVerifier: mockVerifier)

        let result = await orchestrator.verify(credential: credential)

        XCTAssertEqual(result.status, .failed)
        XCTAssertEqual(result.source, .offline)
    }

    // MARK: - UC-11: Expired credential → failed

    func testExpiredCredential_returnsFailed() async throws {
        let (publicKeyMultibase, privateKey) = try OfflineTestHelper.createKeyPair()
        let issuerDid = "did:ssdid:issuer004"
        let keyId = "\(issuerDid)#key-1"

        let didDoc = OfflineTestHelper.createDidDocument(
            did: issuerDid,
            keyId: keyId,
            publicKeyMultibase: publicKeyMultibase
        )
        let bundle = OfflineTestHelper.createBundle(
            issuerDid: issuerDid,
            didDocument: didDoc,
            freshnessRatio: 0.1
        )
        try await bundleStore.saveBundle(bundle)

        // Expired 1 day ago
        let expiredDate = ISO8601DateFormatter().string(from: Date().addingTimeInterval(-86400))
        let credential = try OfflineTestHelper.createTestCredential(
            issuerDid: issuerDid,
            keyId: keyId,
            privateKey: privateKey,
            expirationDate: expiredDate
        )

        let mockVerifier = MockVerifier(shouldThrow: URLError(.notConnectedToInternet))
        let orchestrator = makeOrchestrator(mockVerifier: mockVerifier)

        let result = await orchestrator.verify(credential: credential)

        XCTAssertEqual(result.status, .failed)
        XCTAssertEqual(result.source, .offline)
        let expiryCheck = result.checks.first { $0.type == .expiry }
        XCTAssertEqual(expiryCheck?.status, .fail)
    }

    // MARK: - UC-12: Revoked credential → failed

    func testRevokedCredential_returnsFailed() async throws {
        let (publicKeyMultibase, privateKey) = try OfflineTestHelper.createKeyPair()
        let issuerDid = "did:ssdid:issuer005"
        let keyId = "\(issuerDid)#key-1"

        let didDoc = OfflineTestHelper.createDidDocument(
            did: issuerDid,
            keyId: keyId,
            publicKeyMultibase: publicKeyMultibase
        )

        // Build a status list with index 0 revoked
        // BitstringParser uses GZIP-compressed base64url bitstring
        // For testing, use a pre-built revoked bitstring (index 0 set = 0x80)
        let revokedBitstring = buildRevokedBitstring(revokedIndex: 0)
        let statusList = StatusListCredential(
            id: "https://example.com/status/1",
            type: ["VerifiableCredential", "BitstringStatusListCredential"],
            issuer: issuerDid,
            credentialSubject: StatusListSubject(
                type: "BitstringStatusList",
                statusPurpose: "revocation",
                encodedList: revokedBitstring
            ),
            proof: Proof(
                type: "Ed25519Signature2020",
                created: ISO8601DateFormatter().string(from: Date()),
                verificationMethod: keyId,
                proofPurpose: "assertionMethod",
                proofValue: "zFakeProofForTest"
            )
        )

        let bundle = OfflineTestHelper.createBundle(
            issuerDid: issuerDid,
            didDocument: didDoc,
            freshnessRatio: 0.1,
            statusList: statusList
        )
        try await bundleStore.saveBundle(bundle)

        let credentialStatus = CredentialStatus(
            id: "https://example.com/status/1#0",
            type: "BitstringStatusListEntry",
            statusPurpose: "revocation",
            statusListIndex: "0",
            statusListCredential: "https://example.com/status/1"
        )
        let credential = try OfflineTestHelper.createTestCredential(
            issuerDid: issuerDid,
            keyId: keyId,
            privateKey: privateKey,
            credentialStatus: credentialStatus
        )

        let mockVerifier = MockVerifier(shouldThrow: URLError(.notConnectedToInternet))
        let orchestrator = makeOrchestrator(mockVerifier: mockVerifier)

        let result = await orchestrator.verify(credential: credential)

        XCTAssertEqual(result.status, .failed)
        XCTAssertEqual(result.source, .offline)
        let revocationCheck = result.checks.first { $0.type == .revocation }
        XCTAssertEqual(revocationCheck?.status, .fail)
    }

    // MARK: - UC-13: Happy path (all checks pass) → verifiedOffline

    func testOfflineHappyPath_allChecksPass() async throws {
        let (publicKeyMultibase, privateKey) = try OfflineTestHelper.createKeyPair()
        let issuerDid = "did:ssdid:issuer006"
        let keyId = "\(issuerDid)#key-1"

        let didDoc = OfflineTestHelper.createDidDocument(
            did: issuerDid,
            keyId: keyId,
            publicKeyMultibase: publicKeyMultibase
        )
        let bundle = OfflineTestHelper.createBundle(
            issuerDid: issuerDid,
            didDocument: didDoc,
            freshnessRatio: 0.2
        )
        try await bundleStore.saveBundle(bundle)

        let credential = try OfflineTestHelper.createTestCredential(
            issuerDid: issuerDid,
            keyId: keyId,
            privateKey: privateKey
        )

        let mockVerifier = MockVerifier(shouldThrow: URLError(.notConnectedToInternet))
        let orchestrator = makeOrchestrator(mockVerifier: mockVerifier)

        let result = await orchestrator.verify(credential: credential)

        XCTAssertEqual(result.status, .verifiedOffline)
        XCTAssertEqual(result.source, .offline)
        // All 4 checks should pass
        let sigCheck = result.checks.first { $0.type == .signature }
        XCTAssertEqual(sigCheck?.status, .pass)
        let expiryCheck = result.checks.first { $0.type == .expiry }
        XCTAssertEqual(expiryCheck?.status, .pass)
        XCTAssertEqual(result.checks.first(where: { $0.type == .revocation })?.status, .pass)
        XCTAssertEqual(result.checks.first(where: { $0.type == .bundleFreshness })?.status, .pass)
    }

    // MARK: - UC-7b: Non-network error does NOT fall back to offline

    func testVerificationError_doesNotFallbackToOffline() async throws {
        let (publicKeyMultibase, privateKey) = try OfflineTestHelper.createKeyPair()
        let issuerDid = "did:ssdid:issuer007"
        let keyId = "\(issuerDid)#key-1"

        let didDoc = OfflineTestHelper.createDidDocument(
            did: issuerDid,
            keyId: keyId,
            publicKeyMultibase: publicKeyMultibase
        )
        let bundle = OfflineTestHelper.createBundle(
            issuerDid: issuerDid,
            didDocument: didDoc,
            freshnessRatio: 0.1
        )
        try await bundleStore.saveBundle(bundle)

        let credential = try OfflineTestHelper.createTestCredential(
            issuerDid: issuerDid,
            keyId: keyId,
            privateKey: privateKey
        )

        // A non-network error (e.g. signature invalid) should stay as online failure
        let domainError = NSError(domain: "TestDomain", code: 999, userInfo: [NSLocalizedDescriptionKey: "Signature invalid"])
        let mockVerifier = MockVerifier(shouldThrow: domainError)
        let orchestrator = makeOrchestrator(mockVerifier: mockVerifier)

        let result = await orchestrator.verify(credential: credential)

        XCTAssertEqual(result.status, .failed)
        XCTAssertEqual(result.source, .online)
    }

    // MARK: - UC-9b: Network error + unknown revocation → degraded

    func testNetworkError_unknownRevocation_returnsDegraded() async throws {
        let (publicKeyMultibase, privateKey) = try OfflineTestHelper.createKeyPair()
        let issuerDid = "did:ssdid:issuer008"
        let keyId = "\(issuerDid)#key-1"

        let didDoc = OfflineTestHelper.createDidDocument(
            did: issuerDid,
            keyId: keyId,
            publicKeyMultibase: publicKeyMultibase
        )
        // Bundle without a status list — revocation status will be unknown
        let bundle = OfflineTestHelper.createBundle(
            issuerDid: issuerDid,
            didDocument: didDoc,
            freshnessRatio: 0.1,
            statusList: nil
        )
        try await bundleStore.saveBundle(bundle)

        // Credential that references a status list (but bundle has none)
        let credentialStatus = CredentialStatus(
            id: "https://example.com/status/1#0",
            type: "BitstringStatusListEntry",
            statusPurpose: "revocation",
            statusListIndex: "0",
            statusListCredential: "https://example.com/status/1"
        )
        let credential = try OfflineTestHelper.createTestCredential(
            issuerDid: issuerDid,
            keyId: keyId,
            privateKey: privateKey,
            credentialStatus: credentialStatus
        )

        let mockVerifier = MockVerifier(shouldThrow: URLError(.notConnectedToInternet))
        let orchestrator = makeOrchestrator(mockVerifier: mockVerifier)

        let result = await orchestrator.verify(credential: credential)

        // Unknown revocation with valid signature and fresh bundle → degraded
        XCTAssertEqual(result.status, .degraded)
        XCTAssertEqual(result.source, .offline)
        let revocationCheck = result.checks.first { $0.type == .revocation }
        XCTAssertEqual(revocationCheck?.status, .unknown)
    }

    // MARK: - UC-13b: No expiry date → verifiedOffline (no expiry check failure)

    func testNoExpiryDate_returnsVerifiedOffline() async throws {
        let (publicKeyMultibase, privateKey) = try OfflineTestHelper.createKeyPair()
        let issuerDid = "did:ssdid:issuer009"
        let keyId = "\(issuerDid)#key-1"

        let didDoc = OfflineTestHelper.createDidDocument(
            did: issuerDid,
            keyId: keyId,
            publicKeyMultibase: publicKeyMultibase
        )
        let bundle = OfflineTestHelper.createBundle(
            issuerDid: issuerDid,
            didDocument: didDoc,
            freshnessRatio: 0.1
        )
        try await bundleStore.saveBundle(bundle)

        // Credential with NO expirationDate
        let credential = try OfflineTestHelper.createTestCredential(
            issuerDid: issuerDid,
            keyId: keyId,
            privateKey: privateKey,
            expirationDate: nil
        )

        let mockVerifier = MockVerifier(shouldThrow: URLError(.notConnectedToInternet))
        let orchestrator = makeOrchestrator(mockVerifier: mockVerifier)

        let result = await orchestrator.verify(credential: credential)

        XCTAssertEqual(result.status, .verifiedOffline)
        XCTAssertEqual(result.source, .offline)
        let expiryCheck = result.checks.first { $0.type == .expiry }
        XCTAssertEqual(expiryCheck?.status, .pass)
    }

    // MARK: - B2: DID key rotation — credential signed with new key, bundle has old key → failed

    func testKeyRotation_oldBundle_newCredential_returnsFailed() async throws {
        // Key pair A: old key cached in DID Document
        let classicalProvider = ClassicalProvider()
        let kpA = try classicalProvider.generateKeyPair(algorithm: .ED25519)
        let issuerDid = "did:ssdid:issuer010"
        let keyId = "\(issuerDid)#key-1"

        let didDocWithKeyA = OfflineTestHelper.createDidDocument(
            did: issuerDid,
            keyId: keyId,
            publicKeyMultibase: Multibase.encode(kpA.publicKey)
        )
        // Fresh bundle — fetched just now, within 7-day TTL
        let bundle = OfflineTestHelper.createBundle(
            issuerDid: issuerDid,
            didDocument: didDocWithKeyA,
            freshnessRatio: 0.1
        )
        try await bundleStore.saveBundle(bundle)

        // Key pair B: rotated key — signs the credential but is NOT in the cached DID doc
        let kpB = try classicalProvider.generateKeyPair(algorithm: .ED25519)
        let newKeyId = "\(issuerDid)#key-2"

        let credential = try OfflineTestHelper.createTestCredential(
            issuerDid: issuerDid,
            keyId: newKeyId,
            privateKey: kpB.privateKey
        )

        let mockVerifier = MockVerifier(shouldThrow: URLError(.notConnectedToInternet))
        let orchestrator = makeOrchestrator(mockVerifier: mockVerifier)

        let result = await orchestrator.verify(credential: credential)

        // key-2 not in cached DID doc → signature lookup fails → FAILED
        XCTAssertEqual(result.status, .failed)
        XCTAssertEqual(result.source, .offline)
        let sigCheck = result.checks.first { $0.type == .signature }
        XCTAssertEqual(sigCheck?.status, .fail)
    }

    // MARK: - Bitstring Helper

    /// Builds a GZIP-compressed, base64url-encoded bitstring with the given index revoked.
    /// Uses BitstringParser's expected format (GZIP magic bytes 0x1f 0x8b).
    private func buildRevokedBitstring(revokedIndex: Int) -> String {
        // Create a 16KB (131072-bit) bitstring, set the given bit
        var bytes = [UInt8](repeating: 0, count: 16384)
        let byteIndex = revokedIndex / 8
        let bitIndex = 7 - (revokedIndex % 8)
        if byteIndex < bytes.count {
            bytes[byteIndex] |= UInt8(1 << bitIndex)
        }
        let data = Data(bytes)
        let compressed = gzipCompress(data)
        // Base64url encode — BitstringParser uses Data(base64URLEncoded:)
        return compressed.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    /// Minimal GZIP compression using a hand-built GZIP wrapper around deflate.
    private func gzipCompress(_ data: Data) -> Data {
        // GZIP format: 10-byte header + deflate stream + 8-byte trailer (CRC32 + size)
        var header = Data([
            0x1f, 0x8b,       // Magic number
            0x08,             // Deflate method
            0x00,             // Flags
            0x00, 0x00, 0x00, 0x00, // Modification time
            0x00,             // Extra flags
            0xff              // OS: unknown
        ])

        // Deflate (raw, no header)
        let deflated = zlibDeflate(data)

        // CRC32 and input size
        let crc = crc32Value(data)
        let size = UInt32(data.count)
        var trailer = Data(count: 8)
        trailer.withUnsafeMutableBytes { ptr in
            ptr.storeBytes(of: crc.littleEndian, toByteOffset: 0, as: UInt32.self)
            ptr.storeBytes(of: size.littleEndian, toByteOffset: 4, as: UInt32.self)
        }

        return header + deflated + trailer
    }

    private func zlibDeflate(_ data: Data) -> Data {
        var stream = z_stream()
        // Negative windowBits = raw deflate (no zlib header)
        guard deflateInit2_(&stream, Z_DEFAULT_COMPRESSION, Z_DEFLATED, -15,
                            8, Z_DEFAULT_STRATEGY, ZLIB_VERSION,
                            Int32(MemoryLayout<z_stream>.size)) == Z_OK else {
            return data // Fallback: return uncompressed
        }
        defer { deflateEnd(&stream) }

        let srcData = data as NSData
        stream.next_in = UnsafeMutablePointer<UInt8>(mutating: srcData.bytes.assumingMemoryBound(to: UInt8.self))
        stream.avail_in = uInt(data.count)

        var output = Data()
        let bufSize = 4096
        let buffer = UnsafeMutablePointer<UInt8>.allocate(capacity: bufSize)
        defer { buffer.deallocate() }

        repeat {
            stream.next_out = buffer
            stream.avail_out = uInt(bufSize)
            let status = deflate(&stream, Z_FINISH)
            let written = bufSize - Int(stream.avail_out)
            output.append(buffer, count: written)
            if status == Z_STREAM_END { break }
        } while stream.avail_out == 0

        return output
    }

    private func crc32Value(_ data: Data) -> UInt32 {
        return data.withUnsafeBytes { ptr in
            let bytes = ptr.bindMemory(to: UInt8.self)
            return UInt32(crc32(0, bytes.baseAddress, uInt(data.count)))
        }
    }
}
