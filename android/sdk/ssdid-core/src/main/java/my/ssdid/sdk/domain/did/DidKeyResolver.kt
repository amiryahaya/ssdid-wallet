package my.ssdid.sdk.domain.did

import my.ssdid.sdk.domain.model.DidDocument
import my.ssdid.sdk.domain.model.VerificationMethod

class DidKeyResolver : DidResolver {
    override suspend fun resolve(did: String): Result<DidDocument> = runCatching {
        require(did.startsWith("did:key:")) { "Not a did:key: $did" }

        val methodSpecificId = did.removePrefix("did:key:")
        require(methodSpecificId.startsWith("z")) { "Expected multibase 'z' (base58btc) prefix" }

        val decoded = Base58.decode(methodSpecificId.substring(1))
        val (codec, _) = Multicodec.decode(decoded)

        val vmType = when (codec) {
            Multicodec.ED25519_PUB -> "Ed25519VerificationKey2020"
            Multicodec.P256_PUB -> "EcdsaSecp256r1VerificationKey2019"
            Multicodec.P384_PUB -> "EcdsaSecp384VerificationKey2019"
            else -> throw IllegalArgumentException("Unsupported multicodec: 0x${codec.toString(16)}")
        }

        val keyId = "$did#$methodSpecificId"

        val vm = VerificationMethod(
            id = keyId,
            type = vmType,
            controller = did,
            publicKeyMultibase = methodSpecificId
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
