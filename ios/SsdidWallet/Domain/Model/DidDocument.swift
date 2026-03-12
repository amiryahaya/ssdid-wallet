import Foundation

struct DidDocument: Codable {
    let context: [String]
    let id: String
    var controller: String = ""
    let verificationMethod: [VerificationMethod]
    var authentication: [String] = []
    var assertionMethod: [String] = []
    var capabilityInvocation: [String] = []
    var nextKeyHash: String? = nil

    enum CodingKeys: String, CodingKey {
        case context = "@context"
        case id, controller, verificationMethod
        case authentication, assertionMethod, capabilityInvocation, nextKeyHash
    }

    init(
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

    static func build(
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
