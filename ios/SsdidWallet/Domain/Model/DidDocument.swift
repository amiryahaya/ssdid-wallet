import Foundation

struct DidDocument: Codable {
    let context: [String]
    let id: String
    var controller: String = ""
    let verificationMethod: [VerificationMethod]
    var authentication: [String] = []
    var assertionMethod: [String] = []
    var capabilityInvocation: [String] = []
    var keyAgreement: [String] = []
    var service: [Service] = []
    var nextKeyHash: String? = nil

    enum CodingKeys: String, CodingKey {
        case context = "@context"
        case id, controller, verificationMethod
        case authentication, assertionMethod, capabilityInvocation
        case keyAgreement, service, nextKeyHash
    }

    init(
        context: [String] = ["https://www.w3.org/ns/did/v1"],
        id: String,
        controller: String = "",
        verificationMethod: [VerificationMethod],
        authentication: [String] = [],
        assertionMethod: [String] = [],
        capabilityInvocation: [String] = [],
        keyAgreement: [String] = [],
        service: [Service] = [],
        nextKeyHash: String? = nil
    ) {
        self.context = context
        self.id = id
        self.controller = controller
        self.verificationMethod = verificationMethod
        self.authentication = authentication
        self.assertionMethod = assertionMethod
        self.capabilityInvocation = capabilityInvocation
        self.keyAgreement = keyAgreement
        self.service = service
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

struct Service: Codable {
    let id: String
    let type: String
    let serviceEndpoint: String
}
