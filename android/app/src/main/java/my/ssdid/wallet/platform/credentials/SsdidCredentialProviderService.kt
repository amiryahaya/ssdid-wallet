package my.ssdid.wallet.platform.credentials

import android.app.PendingIntent
import android.content.Intent
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.util.Log
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialNoCreateOptionException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.CustomCredentialEntry
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import my.ssdid.wallet.domain.mdoc.StoredMDoc
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc

/**
 * Android CredentialProviderService implementation for SSDID Wallet.
 *
 * Exposes stored SD-JWT VCs and mdoc credentials to the Android
 * Digital Credentials API (CredentialManager) so that verifiers can
 * request credentials through the system selector.
 *
 * The matching logic is delegated to [CredentialMatcher] for testability.
 */
class SsdidCredentialProviderService : CredentialProviderService() {

    internal val matcher = CredentialMatcher()

    /**
     * Interface for retrieving stored credentials. Allows the service to be
     * tested without a full Vault/DataStore dependency.
     */
    interface CredentialSource {
        fun getSdJwtVcs(): List<StoredSdJwtVc>
        fun getMDocs(): List<StoredMDoc>
    }

    /**
     * Provides the credential source for this service instance.
     * Override in Hilt-injected subclass or set via [credentialSourceProvider]
     * for testing.
     */
    private fun getCredentialSource(): CredentialSource? {
        return credentialSourceProvider?.invoke()
    }

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>
    ) {
        try {
            val options = request.beginGetCredentialOptions
            if (options.isEmpty()) {
                callback.onResult(BeginGetCredentialResponse())
                return
            }

            val source = getCredentialSource()
            val sdJwtVcs = source?.getSdJwtVcs() ?: emptyList()
            val mdocs = source?.getMDocs() ?: emptyList()

            val matched = matcher.matchAll(sdJwtVcs, mdocs)

            if (matched.isEmpty()) {
                callback.onResult(BeginGetCredentialResponse())
                return
            }

            val responseBuilder = BeginGetCredentialResponse.Builder()
            val firstOption = options.first()

            for ((index, entry) in matched.withIndex()) {
                // Target the main launcher activity for credential selection UI.
                // The EXTRA_CREDENTIAL_ID extra identifies which credential was chosen.
                val intent = Intent(CREDENTIAL_SELECTION_ACTION).apply {
                    setPackage(packageName)
                    putExtra(EXTRA_CREDENTIAL_ID, entry.id)
                }
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    PENDING_INTENT_BASE_CODE + index,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                val credentialEntry = CustomCredentialEntry(
                    this,
                    entry.title,
                    pendingIntent,
                    firstOption,
                    entry.subtitle
                )

                responseBuilder.addCredentialEntry(credentialEntry)
            }

            callback.onResult(responseBuilder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Error matching credentials", e)
            callback.onError(
                GetCredentialUnsupportedException("Failed to match credentials: ${e.message}")
            )
        }
    }

    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>
    ) {
        callback.onError(CreateCredentialNoCreateOptionException("Credential creation not supported"))
    }

    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>
    ) {
        callback.onResult(null)
    }

    companion object {
        private const val TAG = "SsdidCredentialProvider"
        internal const val EXTRA_CREDENTIAL_ID = "my.ssdid.wallet.CREDENTIAL_ID"
        internal const val CREDENTIAL_SELECTION_ACTION = "my.ssdid.wallet.CREDENTIAL_SELECTION"
        internal const val PENDING_INTENT_BASE_CODE = 100

        /**
         * Provider function for the credential source. In production this
         * would be replaced by a Hilt-injected approach; for tests it can
         * be set to a lambda returning a mock [CredentialSource].
         */
        @Volatile
        var credentialSourceProvider: (() -> CredentialSource?)? = null
    }
}
