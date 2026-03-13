package my.ssdid.wallet.domain.sdjwt

data class SdJwtVc(
    val issuerJwt: String,
    val disclosures: List<Disclosure>,
    val keyBindingJwt: String?
) {
    fun present(selectedDisclosures: List<Disclosure>, kbJwt: String? = null): String {
        val parts = mutableListOf(issuerJwt)
        for (d in selectedDisclosures) {
            parts.add(d.encode())
        }
        return if (kbJwt != null) {
            parts.joinToString("~") + "~$kbJwt"
        } else if (selectedDisclosures.isNotEmpty()) {
            parts.joinToString("~") + "~"
        } else {
            issuerJwt
        }
    }
}
