package my.ssdid.sdk.domain.sdjwt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

object SdJwtParser {
    fun parse(compact: String): SdJwtVc {
        val parts = compact.split("~")
        require(parts.isNotEmpty()) { "Empty SD-JWT" }

        val issuerJwt = parts[0]
        val disclosureParts = parts.drop(1).filter { it.isNotEmpty() }

        val lastPart = disclosureParts.lastOrNull()
        val isLastKbJwt = lastPart != null && lastPart.count { it == '.' } == 2 &&
            runCatching {
                val headerB64 = lastPart.substringBefore(".")
                val headerJson = String(Base64.getUrlDecoder().decode(headerB64), Charsets.UTF_8)
                Json.parseToJsonElement(headerJson).jsonObject["typ"]?.jsonPrimitive?.content == "kb+jwt"
            }.getOrDefault(false)

        val disclosures: List<Disclosure>
        val kbJwt: String?

        if (isLastKbJwt && disclosureParts.isNotEmpty()) {
            disclosures = disclosureParts.dropLast(1).map { Disclosure.decode(it) }
            kbJwt = lastPart
        } else {
            disclosures = disclosureParts.map { Disclosure.decode(it) }
            kbJwt = null
        }

        return SdJwtVc(
            issuerJwt = issuerJwt,
            disclosures = disclosures,
            keyBindingJwt = kbJwt
        )
    }
}
