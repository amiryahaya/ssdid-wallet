@testable import SsdidCore
import XCTest
@testable import SsdidWallet

final class CanonicalJsonTests: XCTestCase {

    // MARK: - Key Sorting

    func testDictionaryKeysSorted() {
        let dict: [String: Any] = ["z": 1, "a": 2, "m": 3]
        let result = VaultImpl.canonicalJson(dict)
        XCTAssertEqual(result, #"{"a":2,"m":3,"z":1}"#)
    }

    func testNestedDictionaryKeysSorted() {
        let dict: [String: Any] = [
            "b": ["z": 1, "a": 2],
            "a": "first"
        ]
        let result = VaultImpl.canonicalJson(dict)
        XCTAssertEqual(result, #"{"a":"first","b":{"a":2,"z":1}}"#)
    }

    func testDeepNestedSorting() {
        let dict: [String: Any] = [
            "c": [
                "b": [
                    "z": true,
                    "a": false
                ],
                "a": 1
            ],
            "a": "top"
        ]
        let result = VaultImpl.canonicalJson(dict)
        XCTAssertEqual(result, #"{"a":"top","c":{"a":1,"b":{"a":false,"z":true}}}"#)
    }

    // MARK: - Primitive Types

    func testStringValue() {
        let result = VaultImpl.canonicalJson("hello")
        XCTAssertEqual(result, #""hello""#)
    }

    func testIntegerValue() {
        XCTAssertEqual(VaultImpl.canonicalJson(42), "42")
        XCTAssertEqual(VaultImpl.canonicalJson(0), "0")
        XCTAssertEqual(VaultImpl.canonicalJson(-1), "-1")
    }

    func testBooleanValues() {
        XCTAssertEqual(VaultImpl.canonicalJson(true), "true")
        XCTAssertEqual(VaultImpl.canonicalJson(false), "false")
    }

    func testNullValue() {
        let result = VaultImpl.canonicalJson(NSNull())
        XCTAssertEqual(result, "null")
    }

    func testWholeDoubleRendersAsInt() {
        // Double values that are whole numbers should render without decimal
        XCTAssertEqual(VaultImpl.canonicalJson(Double(5.0)), "5")
        XCTAssertEqual(VaultImpl.canonicalJson(Double(0.0)), "0")
        XCTAssertEqual(VaultImpl.canonicalJson(Double(-3.0)), "-3")
    }

    func testFractionalDouble() {
        let result = VaultImpl.canonicalJson(3.14)
        XCTAssertTrue(result.contains("3.14"), "Expected '3.14' in \(result)")
    }

    // MARK: - Arrays

    func testArrayPreservesOrder() {
        let array: [Any] = [3, 1, 2]
        let result = VaultImpl.canonicalJson(array)
        XCTAssertEqual(result, "[3,1,2]")
    }

    func testArrayWithMixedTypes() {
        let array: [Any] = ["a", 1, true, NSNull()]
        let result = VaultImpl.canonicalJson(array)
        XCTAssertEqual(result, #"["a",1,true,null]"#)
    }

    func testEmptyArray() {
        XCTAssertEqual(VaultImpl.canonicalJson([Any]()), "[]")
    }

    func testEmptyDictionary() {
        XCTAssertEqual(VaultImpl.canonicalJson([String: Any]()), "{}")
    }

    // MARK: - String Escaping

    func testEscapesBackslash() {
        let dict: [String: Any] = ["path": "a\\b"]
        let result = VaultImpl.canonicalJson(dict)
        XCTAssertEqual(result, #"{"path":"a\\b"}"#)
    }

    func testEscapesDoubleQuote() {
        let dict: [String: Any] = ["say": "he said \"hi\""]
        let result = VaultImpl.canonicalJson(dict)
        XCTAssertEqual(result, #"{"say":"he said \"hi\""}"#)
    }

    func testEscapesNewline() {
        let dict: [String: Any] = ["text": "line1\nline2"]
        let result = VaultImpl.canonicalJson(dict)
        XCTAssertEqual(result, #"{"text":"line1\nline2"}"#)
    }

    func testEscapesCarriageReturn() {
        let dict: [String: Any] = ["text": "a\rb"]
        let result = VaultImpl.canonicalJson(dict)
        XCTAssertEqual(result, #"{"text":"a\rb"}"#)
    }

    func testEscapesTab() {
        let dict: [String: Any] = ["text": "a\tb"]
        let result = VaultImpl.canonicalJson(dict)
        XCTAssertEqual(result, #"{"text":"a\tb"}"#)
    }

    func testEscapesInKeys() {
        let dict: [String: Any] = ["key\"with\"quotes": "value"]
        let result = VaultImpl.canonicalJson(dict)
        XCTAssertTrue(result.contains(#"key\"with\"quotes"#))
    }

    // MARK: - No Whitespace

    func testNoExtraneousWhitespace() {
        let dict: [String: Any] = ["a": 1, "b": [1, 2], "c": ["x": true]]
        let result = VaultImpl.canonicalJson(dict)
        // No spaces after colons or commas
        XCTAssertFalse(result.contains(": "))
        XCTAssertFalse(result.contains(", "))
    }

    // MARK: - Determinism

    func testDeterministicOutput() {
        // Same input must produce identical output across multiple calls
        let dict: [String: Any] = [
            "type": "Ed25519Signature2020",
            "created": "2024-01-01T00:00:00Z",
            "verificationMethod": "did:ssdid:abc123#key-1",
            "proofPurpose": "authentication",
            "challenge": "nonce-xyz"
        ]

        let outputs = (0..<100).map { _ in VaultImpl.canonicalJson(dict) }
        let allEqual = outputs.allSatisfy { $0 == outputs[0] }
        XCTAssertTrue(allEqual, "canonicalJson must be deterministic")
    }

    // MARK: - W3C Proof-Style Documents

    func testProofOptionsDocument() {
        let proofOptions: [String: Any] = [
            "type": "Ed25519Signature2020",
            "created": "2024-01-01T00:00:00Z",
            "verificationMethod": "did:ssdid:test123#key-1",
            "proofPurpose": "authentication",
            "challenge": "abc",
            "domain": "registry.ssdid.my"
        ]

        let result = VaultImpl.canonicalJson(proofOptions)

        // Keys must be sorted alphabetically
        let expected = #"{"challenge":"abc","created":"2024-01-01T00:00:00Z","domain":"registry.ssdid.my","proofPurpose":"authentication","type":"Ed25519Signature2020","verificationMethod":"did:ssdid:test123#key-1"}"#
        XCTAssertEqual(result, expected)
    }

    func testDidDocumentStyle() {
        let doc: [String: Any] = [
            "@context": ["https://www.w3.org/ns/did/v1"],
            "id": "did:ssdid:test",
            "verificationMethod": [
                [
                    "id": "did:ssdid:test#key-1",
                    "type": "Ed25519VerificationKey2020",
                    "controller": "did:ssdid:test",
                    "publicKeyMultibase": "uABC123"
                ] as [String: Any]
            ]
        ]

        let result = VaultImpl.canonicalJson(doc)

        // @context sorts before id because @ < i in ASCII
        XCTAssertTrue(result.hasPrefix(#"{"@context""#))
        // id sorts before verificationMethod
        let idIndex = result.range(of: "\"id\"")!.lowerBound
        let vmIndex = result.range(of: "\"verificationMethod\"")!.lowerBound
        XCTAssertTrue(idIndex < vmIndex)
        // Nested dict keys inside array element must also be sorted
        XCTAssertTrue(result.contains(#""controller":"did:ssdid:test","id":"did:ssdid:test#key-1""#))
    }
}
