import XCTest
@testable import SsdidWallet

final class OpenId4VpIntegrationTests: XCTestCase {

    private let testSigner: (Data) -> Data = { _ in "test-sig".data(using: .utf8)! }

    // MARK: - Helpers

    /// Issue a real SD-JWT VC and return both the SdJwtVc and a StoredSdJwtVc built from it.
    private func issueAndStore(
        type: String = "IdentityCredential",
        claims: [String: Any] = ["given_name": "Alice", "family_name": "Smith", "email": "alice@example.com"],
        disclosable: Set<String> = ["given_name", "family_name", "email"]
    ) throws -> (sdJwtVc: SdJwtVc, stored: StoredSdJwtVc) {
        let issuer = SdJwtIssuer(signer: testSigner, algorithm: "EdDSA")
        let vc = try issuer.issue(
            issuer: "https://issuer.example.com",
            subject: "did:ssdid:holder123",
            type: ["VerifiableCredential", type],
            claims: claims,
            disclosable: disclosable
        )

        let compact = try vc.present(selectedDisclosures: vc.disclosures)

        // Extract claim values as [String: String] for StoredSdJwtVc
        var claimStrings: [String: String] = [:]
        for d in vc.disclosures {
            claimStrings[d.claimName] = "\(d.claimValue)"
        }

        let stored = StoredSdJwtVc(
            id: "vc-integration-1",
            compact: compact,
            issuer: "https://issuer.example.com",
            subject: "did:ssdid:holder123",
            type: type,
            claims: claimStrings,
            disclosableClaims: Array(disclosable),
            issuedAt: 1700000000
        )

        return (vc, stored)
    }

    private func makeHandler() -> OpenId4VpHandler {
        OpenId4VpHandler(
            transport: OpenId4VpTransport(),
            peMatcher: PresentationDefinitionMatcher(),
            dcqlMatcher: DcqlMatcher(),
            vpTokenBuilder: VpTokenBuilder()
        )
    }

    private func makePeUri(vctFilter: String, requiredClaims: [String], optionalClaims: [String] = []) -> String {
        var fields: [String] = []
        fields.append("{\"path\":[\"$.vct\"],\"filter\":{\"const\":\"\(vctFilter)\"}}")
        for claim in requiredClaims {
            fields.append("{\"path\":[\"$.\(claim)\"]}")
        }
        for claim in optionalClaims {
            fields.append("{\"path\":[\"$.\(claim)\"],\"optional\":true}")
        }

        let pdJson = """
        {"id":"test-pd","input_descriptors":[{"id":"desc-1","format":{"vc+sd-jwt":{}},"constraints":{"fields":[\(fields.joined(separator: ","))]}}]}
        """
        let encodedPd = pdJson.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)!

        return "openid4vp://?response_type=vp_token"
            + "&client_id=https://verifier.example.com"
            + "&nonce=test-nonce-123"
            + "&response_mode=direct_post"
            + "&response_uri=https://verifier.example.com/response"
            + "&presentation_definition=\(encodedPd)"
    }

    private func makeDcqlUri(vctFilter: String, requiredClaims: [String], optionalClaims: [String] = []) -> String {
        var claimsArray: [String] = []
        for claim in requiredClaims {
            claimsArray.append("{\"path\":[\"\(claim)\"]}")
        }
        for claim in optionalClaims {
            claimsArray.append("{\"path\":[\"\(claim)\"],\"optional\":true}")
        }

        let dcqlJson = """
        {"credentials":[{"id":"cred-1","format":"vc+sd-jwt","meta":{"vct_values":["\(vctFilter)"]},"claims":[\(claimsArray.joined(separator: ","))]}]}
        """
        let encodedDcql = dcqlJson.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)!

        return "openid4vp://?response_type=vp_token"
            + "&client_id=https://verifier.example.com"
            + "&nonce=test-nonce-456"
            + "&response_mode=direct_post"
            + "&response_uri=https://verifier.example.com/response"
            + "&dcql_query=\(encodedDcql)"
    }

    // MARK: - Test 1: Full PE 2.0 matching flow

    func testFullPresentationExchangeMatchingFlow() throws {
        let (_, stored) = try issueAndStore()
        let handler = makeHandler()
        let uri = makePeUri(vctFilter: "IdentityCredential", requiredClaims: ["given_name", "family_name"])

        let result = try handler.processRequest(uri: uri, storedVcs: [stored])

        // Verify auth request parsed correctly
        XCTAssertEqual(result.authRequest.clientId, "https://verifier.example.com")
        XCTAssertEqual(result.authRequest.nonce, "test-nonce-123")
        XCTAssertNotNil(result.authRequest.presentationDefinition)

        // Verify match found
        XCTAssertEqual(result.matchResults.count, 1)
        let match = result.matchResults[0]
        XCTAssertEqual(match.credentialId, "vc-integration-1")
        XCTAssertEqual(match.credentialType, "IdentityCredential")
        XCTAssertEqual(match.descriptorId, "desc-1")

        // Verify correct claims returned
        XCTAssertNotNil(match.availableClaims["given_name"])
        XCTAssertTrue(match.availableClaims["given_name"]!.required)
        XCTAssertTrue(match.availableClaims["given_name"]!.available)
        XCTAssertNotNil(match.availableClaims["family_name"])
        XCTAssertTrue(match.availableClaims["family_name"]!.required)
        XCTAssertTrue(match.availableClaims["family_name"]!.available)

        // Verify query descriptors
        XCTAssertEqual(result.query.descriptors.count, 1)
        XCTAssertEqual(result.query.descriptors[0].vctFilter, "IdentityCredential")
    }

    // MARK: - Test 2: Full DCQL matching flow

    func testFullDcqlMatchingFlow() throws {
        let (_, stored) = try issueAndStore()
        let handler = makeHandler()
        let uri = makeDcqlUri(vctFilter: "IdentityCredential", requiredClaims: ["given_name"], optionalClaims: ["email"])

        let result = try handler.processRequest(uri: uri, storedVcs: [stored])

        // Verify auth request parsed correctly
        XCTAssertEqual(result.authRequest.clientId, "https://verifier.example.com")
        XCTAssertEqual(result.authRequest.nonce, "test-nonce-456")
        XCTAssertNotNil(result.authRequest.dcqlQuery)
        XCTAssertNil(result.authRequest.presentationDefinition)

        // Verify match found
        XCTAssertEqual(result.matchResults.count, 1)
        let match = result.matchResults[0]
        XCTAssertEqual(match.credentialId, "vc-integration-1")
        XCTAssertEqual(match.credentialType, "IdentityCredential")

        // Verify required and optional claims
        XCTAssertNotNil(match.availableClaims["given_name"])
        XCTAssertTrue(match.availableClaims["given_name"]!.required)
        XCTAssertTrue(match.availableClaims["given_name"]!.available)
        XCTAssertNotNil(match.availableClaims["email"])
        XCTAssertFalse(match.availableClaims["email"]!.required)
        XCTAssertTrue(match.availableClaims["email"]!.available)
    }

    // MARK: - Test 3: VP Token building

    func testVpTokenBuildingWithKbJwt() throws {
        let (issuedVc, _) = try issueAndStore()

        // Round-trip: present then parse back (simulates what the wallet stores and retrieves)
        let compact = try issuedVc.present(selectedDisclosures: issuedVc.disclosures)
        let parsedVc = try SdJwtParser.parse(compact)

        // Build VP token
        let vpTokenBuilder = VpTokenBuilder()
        let selectedClaims: Set<String> = ["given_name", "family_name"]
        let vpToken = try vpTokenBuilder.build(
            sdJwtVc: parsedVc,
            selectedClaimNames: selectedClaims,
            audience: "https://verifier.example.com",
            nonce: "test-nonce-789",
            algorithm: "EdDSA",
            signer: testSigner
        )

        // Verify token structure: issuerJwt ~ disclosures ~ KB-JWT
        let parts = vpToken.split(separator: "~", omittingEmptySubsequences: false).map(String.init)

        // First part is the issuer JWT
        let issuerJwt = parts[0]
        XCTAssertEqual(issuerJwt.filter { $0 == "." }.count, 2, "Issuer JWT should have 3 dot-separated parts")

        // Middle parts are selected disclosures (should be exactly 2 for given_name and family_name)
        let disclosureParts = parts.dropFirst().dropLast()
        let nonEmptyDisclosures = disclosureParts.filter { !$0.isEmpty }
        XCTAssertEqual(nonEmptyDisclosures.count, 2, "Should have exactly 2 selected disclosures")

        // Verify disclosures decode to the correct claim names
        var disclosedClaimNames: Set<String> = []
        for d in nonEmptyDisclosures {
            let disclosure = try Disclosure.decode(d)
            disclosedClaimNames.insert(disclosure.claimName)
        }
        XCTAssertEqual(disclosedClaimNames, selectedClaims)

        // Last part is the KB-JWT
        let kbJwtString = parts.last!
        XCTAssertFalse(kbJwtString.isEmpty, "KB-JWT should not be empty")
        XCTAssertEqual(kbJwtString.filter { $0 == "." }.count, 2, "KB-JWT should have 3 dot-separated parts")

        // Verify KB-JWT header
        let kbParts = kbJwtString.split(separator: ".").map(String.init)
        let kbHeaderData = Data(base64URLEncoded: kbParts[0])!
        let kbHeader = try JSONSerialization.jsonObject(with: kbHeaderData) as! [String: Any]
        XCTAssertEqual(kbHeader["typ"] as? String, "kb+jwt")
        XCTAssertEqual(kbHeader["alg"] as? String, "EdDSA")

        // Verify KB-JWT payload has correct aud and nonce
        let kbPayloadData = Data(base64URLEncoded: kbParts[1])!
        let kbPayload = try JSONSerialization.jsonObject(with: kbPayloadData) as! [String: Any]
        XCTAssertEqual(kbPayload["aud"] as? String, "https://verifier.example.com")
        XCTAssertEqual(kbPayload["nonce"] as? String, "test-nonce-789")
        XCTAssertNotNil(kbPayload["sd_hash"], "KB-JWT payload should contain sd_hash")
        XCTAssertNotNil(kbPayload["iat"], "KB-JWT payload should contain iat")
    }

    // MARK: - Test 4: No match for non-matching VCT

    func testNoMatchForNonMatchingVct() throws {
        let (_, stored) = try issueAndStore(type: "IdentityCredential")
        let handler = makeHandler()

        // Request a DriverLicenseCredential, but we only have IdentityCredential
        let uri = makePeUri(vctFilter: "DriverLicenseCredential", requiredClaims: ["license_number"])
        let result = try handler.processRequest(uri: uri, storedVcs: [stored])

        XCTAssertTrue(result.matchResults.isEmpty, "Should have no matches when VCT does not match")
    }
}
