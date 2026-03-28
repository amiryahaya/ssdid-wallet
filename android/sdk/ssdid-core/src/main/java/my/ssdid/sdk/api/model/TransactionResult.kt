package my.ssdid.sdk.api.model

/**
 * Represents the outcome of a signed transaction submission.
 *
 * @property transactionId server-assigned identifier for the transaction
 * @property status current status of the transaction (e.g. "accepted", "pending")
 */
data class TransactionResult(
    val transactionId: String,
    val status: String
)
