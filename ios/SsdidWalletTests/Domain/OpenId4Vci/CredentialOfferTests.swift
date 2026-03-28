@testable import SsdidCore
import XCTest
@testable import SsdidWallet

final class CredentialOfferTests: XCTestCase {

    func testParsePreAuthorizedCodeOffer() throws {
        let json = """
        {
            "credential_issuer": "https://issuer.example.com",
            "credential_configuration_ids": ["UnivDegree"],
            "grants": {
                "urn:ietf:params:oauth:grant-type:pre-authorized_code": {
                    "pre-authorized_code": "abc123"
                }
            }
        }
        """
        let offer = try CredentialOffer.parse( json)
        XCTAssertEqual(offer.credentialIssuer, "https://issuer.example.com")
        XCTAssertEqual(offer.credentialConfigurationIds, ["UnivDegree"])
        XCTAssertEqual(offer.preAuthorizedCode, "abc123")
        XCTAssertNil(offer.txCode)
        XCTAssertFalse(offer.authorizationCodeGrant)
    }

    func testParseOfferWithTxCode() throws {
        let json = """
        {
            "credential_issuer": "https://issuer.example.com",
            "credential_configuration_ids": ["IdCard"],
            "grants": {
                "urn:ietf:params:oauth:grant-type:pre-authorized_code": {
                    "pre-authorized_code": "xyz789",
                    "tx_code": {
                        "input_mode": "numeric",
                        "length": 6,
                        "description": "Enter PIN from email"
                    }
                }
            }
        }
        """
        let offer = try CredentialOffer.parse( json)
        XCTAssertNotNil(offer.txCode)
        XCTAssertEqual(offer.txCode?.inputMode, "numeric")
        XCTAssertEqual(offer.txCode?.length, 6)
        XCTAssertEqual(offer.txCode?.description, "Enter PIN from email")
    }

    func testParseAuthorizationCodeGrant() throws {
        let json = """
        {
            "credential_issuer": "https://issuer.example.com",
            "credential_configuration_ids": ["Diploma"],
            "grants": {
                "authorization_code": {
                    "issuer_state": "state-abc"
                }
            }
        }
        """
        let offer = try CredentialOffer.parse( json)
        XCTAssertTrue(offer.authorizationCodeGrant)
        XCTAssertEqual(offer.issuerState, "state-abc")
        XCTAssertNil(offer.preAuthorizedCode)
    }

    func testRejectHttpIssuer() {
        let json = """
        {"credential_issuer":"http://bad.com","credential_configuration_ids":["x"],"grants":{"authorization_code":{}}}
        """
        XCTAssertThrowsError(try CredentialOffer.parse( json)) { error in
            XCTAssertTrue("\(error)".contains("HTTPS"))
        }
    }

    func testRejectEmptyConfigIds() {
        let json = """
        {"credential_issuer":"https://issuer.example.com","credential_configuration_ids":[],"grants":{"authorization_code":{}}}
        """
        XCTAssertThrowsError(try CredentialOffer.parse( json))
    }

    func testRejectMissingGrants() {
        let json = """
        {"credential_issuer":"https://issuer.example.com","credential_configuration_ids":["x"]}
        """
        XCTAssertThrowsError(try CredentialOffer.parse( json))
    }

    func testParseFromUri() throws {
        let offerJson = """
        {"credential_issuer":"https://issuer.example.com","credential_configuration_ids":["x"],"grants":{"authorization_code":{}}}
        """
        let encoded = offerJson.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)!
        let uri = "openid-credential-offer://?credential_offer=\(encoded)"
        let offer = try CredentialOffer.parseFromUri(uri)
        XCTAssertEqual(offer.credentialIssuer, "https://issuer.example.com")
    }

    func testRejectMissingCredentialIssuer() {
        let json = """
        {"credential_configuration_ids":["x"],"grants":{"authorization_code":{}}}
        """
        XCTAssertThrowsError(try CredentialOffer.parse( json))
    }

    func testParseMultipleConfigIds() throws {
        let json = """
        {
            "credential_issuer": "https://issuer.example.com",
            "credential_configuration_ids": ["IdCard", "Diploma", "License"],
            "grants": {
                "urn:ietf:params:oauth:grant-type:pre-authorized_code": {
                    "pre-authorized_code": "multi-123"
                }
            }
        }
        """
        let offer = try CredentialOffer.parse( json)
        XCTAssertEqual(offer.credentialConfigurationIds.count, 3)
        XCTAssertEqual(offer.credentialConfigurationIds, ["IdCard", "Diploma", "License"])
    }
}
