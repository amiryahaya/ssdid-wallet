package my.ssdid.wallet.domain.notify

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import my.ssdid.wallet.domain.logging.NoOpLogger
import my.ssdid.wallet.domain.logging.SsdidLogger
import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.domain.transport.NotifyApi
import my.ssdid.wallet.domain.transport.dto.CreateMailboxRequest
import my.ssdid.wallet.domain.transport.dto.NotifyDevice
import my.ssdid.wallet.domain.transport.dto.PendingNotification
import my.ssdid.wallet.domain.notify.LocalNotificationStore
import my.ssdid.wallet.domain.transport.dto.RegisterInboxRequest
import my.ssdid.wallet.domain.transport.dto.UpdateDevicesRequest
import java.security.MessageDigest
import java.util.Base64
import kotlin.jvm.Volatile

/**
 * Orchestrates all interactions with the SSDID Notify service.
 *
 * Responsibilities:
 * - Register an inbox on first launch and persist credentials via [NotifyStorage].
 * - Create/delete per-identity mailboxes (correlation-free notifications).
 * - Fetch and acknowledge pending notifications after a reconnect.
 *
 * The mailbox_id is deterministic: "mbx_" + first 16 chars of Base64url(SHA-256(did.utf8)).
 * This allows recreating it without stored state.
 */
class NotifyManager(
    private val notifyApi: NotifyApi,
    private val storage: NotifyStorage,
    private val dispatcher: NotifyDispatcher,
    private val localNotificationStorage: LocalNotificationStore,
    private val logger: SsdidLogger = NoOpLogger()
) {

    private val registrationMutex = Mutex()

    @Volatile
    private var knownIdentities: List<Identity> = emptyList()

    /**
     * Updates the list of known identities used for mailbox -> identity resolution.
     * Call this whenever identities change (create, delete, foreground refresh).
     */
    fun updateKnownIdentities(identities: List<Identity>) {
        knownIdentities = identities
    }

    /**
     * Fetches pending notifications, resolves each to its owning identity via
     * mailbox_id reverse lookup, dispatches via [dispatcher], and acknowledges.
     */
    suspend fun fetchAndDemux() {
        val pending = fetchPending()
        if (pending.isEmpty()) return

        // Build reverse map: mailbox_id -> identity name
        val mailboxToName = knownIdentities.associate { identity ->
            mailboxIdFor(identity) to identity.name
        }

        for (notification in pending) {
            val identityName = mailboxToName[notification.mailboxId]

            // Save locally first
            localNotificationStorage.save(
                LocalNotification(
                    id = notification.notificationId,
                    mailboxId = notification.mailboxId,
                    identityName = identityName,
                    payload = notification.payload,
                    priority = notification.priority,
                    receivedAt = notification.receivedAt ?: "",
                    isRead = false
                )
            )

            try {
                dispatcher.dispatch(identityName, notification)
            } catch (_: Exception) {
                // Dispatch failure is non-fatal
            }
            try {
                ackPending(notification.notificationId)
            } catch (_: Exception) {
                // Ack failure means it'll be re-fetched next time — acceptable
            }
        }
    }

    /**
     * Ensures an inbox exists for this device. Idempotent — safe to call on every launch.
     * If no inbox is registered yet, creates one with no device tokens attached (tokens are
     * added separately once a FCM/UnifiedPush token is available).
     *
     * A Mutex prevents TOCTOU races when called concurrently (e.g. FCM token arrives at
     * the same time as the app-startup call).
     *
     * @return the inbox_id, or null if registration failed.
     */
    suspend fun ensureInboxRegistered(): String? = registrationMutex.withLock {
        val existing = storage.getInboxId()
        if (existing != null) return@withLock existing

        try {
            // Register with an empty devices list; tokens are updated via updateDeviceToken.
            val response = notifyApi.registerInbox(RegisterInboxRequest(devices = emptyList()))
            storage.saveInboxCredentials(response.inboxId, response.inboxSecret)
            logger.info(TAG, "Inbox registered: ${response.inboxId}")
            response.inboxId
        } catch (e: Exception) {
            logger.error(TAG, "Failed to register inbox", e)
            null
        }
    }

    /**
     * Updates the push device token on an existing inbox.
     * Call this whenever a new FCM/UnifiedPush token is received.
     *
     * @param platform e.g. "android"
     * @param token the push token (e.g. FCM token or UnifiedPush endpoint URL)
     */
    suspend fun updateDeviceToken(platform: String, token: String) {
        val (inboxId, bearer) = credentials() ?: return
        try {
            notifyApi.updateDevices(
                bearerSecret = bearer,
                request = UpdateDevicesRequest(
                    devices = listOf(NotifyDevice(platform = platform, token = token))
                )
            )
            logger.info(TAG, "Device token updated for inbox $inboxId")
        } catch (e: Exception) {
            logger.error(TAG, "Failed to update device token", e)
        }
    }

    /**
     * Creates a mailbox for the given identity. The mailbox_id is derived deterministically
     * from the identity's DID so it can always be recreated.
     *
     * Safe to call multiple times — the server is expected to be idempotent on duplicate
     * mailbox_id for the same inbox.
     */
    suspend fun createMailbox(identity: Identity) {
        val (inboxId, bearer) = credentials() ?: return
        val mailboxId = mailboxIdFor(identity)
        try {
            notifyApi.createMailbox(
                bearerSecret = bearer,
                request = CreateMailboxRequest(inboxId = inboxId, mailboxId = mailboxId)
            )
            logger.info(TAG, "Mailbox created: $mailboxId for ${identity.did}")
        } catch (e: Exception) {
            logger.error(TAG, "Failed to create mailbox for ${identity.did}", e)
        }
    }

    /**
     * Deletes the mailbox associated with the given identity (e.g. when it is deactivated).
     */
    suspend fun deleteMailbox(identity: Identity) {
        val (_, bearer) = credentials() ?: return
        val mailboxId = mailboxIdFor(identity)
        try {
            notifyApi.deleteMailbox(bearerSecret = bearer, mailboxId = mailboxId)
            logger.info(TAG, "Mailbox deleted: $mailboxId for ${identity.did}")
        } catch (e: Exception) {
            logger.error(TAG, "Failed to delete mailbox for ${identity.did}", e)
        }
    }

    /**
     * Fetches all pending notifications from the server.
     * Call this on reconnect / app foreground to drain queued messages.
     *
     * @return list of pending notifications, or empty list on failure.
     */
    suspend fun fetchPending(): List<PendingNotification> {
        val (_, bearer) = credentials() ?: return emptyList()
        return try {
            notifyApi.fetchPending(bearerSecret = bearer).notifications
        } catch (e: Exception) {
            logger.error(TAG, "Failed to fetch pending notifications", e)
            emptyList()
        }
    }

    /**
     * Acknowledges a notification after it has been processed, removing it from the server queue.
     */
    suspend fun ackPending(notificationId: String) {
        val (_, bearer) = credentials() ?: return
        try {
            notifyApi.ackPending(bearerSecret = bearer, notificationId = notificationId)
        } catch (e: Exception) {
            logger.error(TAG, "Failed to ack notification $notificationId", e)
        }
    }

    // ---------- Helpers ----------

    /**
     * Returns inbox_id and a "Bearer {inbox_secret}" header value, or null if not registered.
     */
    private suspend fun credentials(): Pair<String, String>? {
        val inboxId = storage.getInboxId() ?: return null
        val secret = storage.getInboxSecret() ?: return null
        return inboxId to "Bearer $secret"
    }

    /**
     * Derives a stable mailbox_id from an identity's DID.
     * Format: "mbx_" + first 16 chars of Base64url-nopad(SHA-256(did.utf8))
     * (yields ~96 bits of entropy, sufficient for correlation resistance).
     */
    private companion object {
        private const val TAG = "NotifyManager"
    }

    internal fun mailboxIdFor(identity: Identity): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identity.did.toByteArray(Charsets.UTF_8))
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        return "mbx_${encoded.take(16)}"
    }

}
