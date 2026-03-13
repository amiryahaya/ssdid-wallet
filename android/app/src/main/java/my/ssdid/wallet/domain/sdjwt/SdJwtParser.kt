package my.ssdid.wallet.domain.sdjwt

object SdJwtParser {
    fun parse(compact: String): SdJwtVc {
        val parts = compact.split("~")
        require(parts.isNotEmpty()) { "Empty SD-JWT" }

        val issuerJwt = parts[0]
        val disclosureParts = parts.drop(1).filter { it.isNotEmpty() }

        val lastPart = disclosureParts.lastOrNull()
        val isLastKbJwt = lastPart != null && lastPart.count { it == '.' } == 2

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
