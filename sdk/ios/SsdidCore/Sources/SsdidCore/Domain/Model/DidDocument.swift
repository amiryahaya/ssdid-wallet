import Foundation

public struct DidDocument: Codable, Sendable {
    public let context: [String]
    public let id: String
    public var controller: String = ""
    public let verificationMethod: [VerificationMethod]
    public var authentication: [String] = []
    public var assertionMethod: [String] = []
    public var capabilityInvocation: [String] = []
    public var nextKeyHash: String? = nil

    enum CodingKeys: String, CodingKey {
        case context = "@context"
        case id, controller, verificationMethod
        case authentication, assertionMethod, capabilityInvocation, nextKeyHash
    }

    public init(
        context: [String] = ["https://www.w3.org/ns/did/v1"],
        id: String,
        controller: String = "",
        verificationMethod: [VerificationMethod],
        authentication: [String] = [],
        assertionMethod: [String] = [],
        capabilityInvocation: [String] = [],
        nextKeyHash: String? = nil
    ) {
        self.context = context
        self.id = id
        self.controller = controller
        self.verificationMethod = verificationMethod
        self.authentication = authentication
        self.assertionMethod = assertionMethod
        self.capabilityInvocation = capabilityInvocation
        self.nextKeyHash = nextKeyHash
    }

    public static func build(
        did: Did,
        keyId: String,
        algorithm: Algorithm,
        publicKeyMultibase: String
    ) -> DidDocument {
        DidDocument(
            id: did.value,
            controller: did.value,
            verificationMethod: [
                VerificationMethod(
                    id: keyId,
                    type: algorithm.w3cType,
                    controller: did.value,
                    publicKeyMultibase: publicKeyMultibase
                )
            ],
            authentication: [keyId],
            assertionMethod: [keyId],
            capabilityInvocation: [keyId]
        )
    }
}
