package my.ssdid.wallet.ui.components

/**
 * Truncates a DID for display: `did:ssdid:Ab3x...7kQ9`
 * Shows the method prefix + first 4 chars + ... + last 4 chars of the method-specific ID.
 */
fun String.truncatedDid(): String {
    if (!startsWith("did:")) return this
    val parts = split(":", limit = 3)
    if (parts.size < 3) return this
    val methodSpecificId = parts[2]
    if (methodSpecificId.length <= 12) return this
    return "did:${parts[1]}:${methodSpecificId.take(4)}...${methodSpecificId.takeLast(4)}"
}
