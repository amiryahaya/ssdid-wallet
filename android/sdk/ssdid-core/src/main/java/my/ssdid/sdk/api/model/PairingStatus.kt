package my.ssdid.sdk.api.model

/**
 * Represents the current status of a device-pairing request.
 *
 * @property status pairing state (e.g. "pending", "approved", "rejected")
 * @property deviceName name of the device requesting pairing, if available
 * @property publicKey public key of the paired device, if available
 */
data class PairingStatus(
    val status: String,
    val deviceName: String? = null,
    val publicKey: String? = null
)
