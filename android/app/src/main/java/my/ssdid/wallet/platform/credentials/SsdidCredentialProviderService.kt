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

            // Retrieve stored credentials.
            // In a production service these would be loaded from the Vault
            // asynchronously. For the skeleton we load from the companion
            // object which tests can populate, and a real Hilt-injected
            // implementation would replace this.
            val sdJwtVcs = credentialSource?.getSdJwtVcs() ?: emptyList()
            val mdocs = credentialSource?.getMDocs() ?: emptyList()

            val matched = matcher.matchAll(sdJwtVcs, mdocs)

            if (matched.isEmpty()) {
                callback.onResult(BeginGetCredentialResponse())
                return
            }

            val responseBuilder = BeginGetCredentialResponse.Builder()
            val firstOption = options.first()

            for ((index, entry) in matched.withIndex()) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    PENDING_INTENT_BASE_CODE + index,
                    Intent().apply {
                        putExtra(EXTRA_CREDENTIAL_ID, entry.id)
                    },
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
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

    /**
     * Interface for retrieving stored credentials. Allows the service to be
     * tested without a full Vault/DataStore dependency.
     */
    interface CredentialSource {
        fun getSdJwtVcs(): List<StoredSdJwtVc>
        fun getMDocs(): List<StoredMDoc>
    }

    companion object {
        private const val TAG = "SsdidCredentialProvider"
        internal const val EXTRA_CREDENTIAL_ID = "my.ssdid.wallet.CREDENTIAL_ID"
        internal const val PENDING_INTENT_BASE_CODE = 100

        /**
         * Credential source that the service reads from. In production this
         * would be replaced by a Hilt-injected Vault; for tests it can be
         * set directly.
         */
        @Volatile
        var credentialSource: CredentialSource? = null
    }
}
