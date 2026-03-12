package my.ssdid.wallet.domain.notify

import my.ssdid.wallet.domain.transport.dto.PendingNotification

/**
 * Callback interface for dispatching demultiplexed notifications.
 * Each notification has been resolved to its owning identity (if any).
 */
fun interface NotifyDispatcher {
    /**
     * Called for each pending notification with its resolved identity name.
     * @param identityName the display name of the identity that owns this mailbox, or null if unknown
     * @param notification the raw pending notification from the server
     */
    suspend fun dispatch(identityName: String?, notification: PendingNotification)
}
