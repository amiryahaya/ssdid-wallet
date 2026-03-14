package my.ssdid.wallet.domain.mdoc

object MDocPresenter {

    fun present(
        issuerSigned: IssuerSigned,
        requestedElements: Map<String, List<String>>
    ): IssuerSigned {
        val filteredNameSpaces = mutableMapOf<String, List<IssuerSignedItem>>()

        for ((namespace, requestedIds) in requestedElements) {
            val items = issuerSigned.nameSpaces[namespace] ?: continue
            val filtered = items.filter { it.elementIdentifier in requestedIds }
            filteredNameSpaces[namespace] = filtered
        }

        return IssuerSigned(
            nameSpaces = filteredNameSpaces,
            issuerAuth = issuerSigned.issuerAuth
        )
    }
}
