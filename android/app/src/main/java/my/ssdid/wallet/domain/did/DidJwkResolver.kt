package my.ssdid.wallet.domain.did

import my.ssdid.wallet.domain.model.DidDocument
import my.ssdid.wallet.domain.model.VerificationMethod
import java.util.Base64

class DidJwkResolver : DidResolver {
    override suspend fun resolve(did: String): Result<DidDocument> = runCatching {
        require(did.startsWith("did:jwk:")) { "Not a did:jwk: $did" }
        val encoded = did.removePrefix("did:jwk:")
        Base64.getUrlDecoder().decode(encoded)  // Validate decodable
        val keyId = "$did#0"
        val vm = VerificationMethod(
            id = keyId,
            type = "JsonWebKey2020",
            controller = did,
            publicKeyMultibase = ""
        )
        DidDocument(
            id = did,
            controller = did,
            verificationMethod = listOf(vm),
            authentication = listOf(keyId),
            assertionMethod = listOf(keyId),
            capabilityInvocation = listOf(keyId)
        )
    }
}
