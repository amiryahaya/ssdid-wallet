package my.ssdid.sdk.samples.customstorage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import my.ssdid.sdk.SsdidSdk
import my.ssdid.sdk.domain.model.Algorithm

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create a custom in-memory storage
        val customStorage = SimpleVaultStorage()

        // Pass the custom storage to the SDK builder
        val sdk = SsdidSdk.builder(applicationContext)
            .registryUrl("https://registry.ssdid.my")
            .vaultStorage(customStorage)
            .build()

        setContent {
            MaterialTheme {
                CustomStorageScreen(sdk, customStorage)
            }
        }
    }
}

@Composable
fun CustomStorageScreen(sdk: SsdidSdk, storage: SimpleVaultStorage) {
    val scope = rememberCoroutineScope()
    var output by remember { mutableStateOf("Ready — using in-memory custom storage") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("SSDID SDK — Custom Storage", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "This sample uses an in-memory HashMap-based VaultStorage implementation " +
                "instead of the default DataStore-backed storage.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(16.dp))

        Button(onClick = {
            scope.launch {
                output = "Creating identity with custom storage..."
                val result = sdk.identity.create("InMemory User", Algorithm.ED25519)
                result.onSuccess { identity ->
                    output = "Created!\n" +
                        "DID: ${identity.did}\n" +
                        "Key ID: ${identity.keyId}\n\n" +
                        "Storage identity count: ${storage.identityCount()}"
                }.onFailure { error ->
                    output = "Error: ${error.message}"
                }
            }
        }) { Text("Create Identity") }

        Spacer(Modifier.height(8.dp))

        Button(onClick = {
            scope.launch {
                val identities = sdk.identity.list()
                output = "Identities from custom storage (${identities.size}):\n" +
                    identities.joinToString("\n") { "- ${it.name}: ${it.did}" } +
                    "\n\nRaw storage count: ${storage.identityCount()}"
            }
        }) { Text("List Identities") }

        Spacer(Modifier.height(8.dp))

        Button(onClick = {
            scope.launch {
                val identities = sdk.identity.list()
                if (identities.isEmpty()) {
                    output = "No identities to sign with. Create one first."
                    return@launch
                }
                val identity = identities.first()
                val data = "Custom storage signing test".toByteArray()
                val signResult = sdk.vault.sign(identity.keyId, data)
                signResult.onSuccess { sig ->
                    val hex = sig.take(16).joinToString("") { "%02x".format(it) }
                    output = "Signed using key from custom storage!\n" +
                        "Signature (first 16 bytes): $hex...\n\n" +
                        "The private key was retrieved from SimpleVaultStorage (in-memory HashMap)."
                }.onFailure { error ->
                    output = "Sign error: ${error.message}"
                }
            }
        }) { Text("Sign Data") }

        Spacer(Modifier.height(8.dp))

        Button(onClick = {
            scope.launch {
                val identities = sdk.identity.list()
                if (identities.isEmpty()) {
                    output = "No identities to delete."
                    return@launch
                }
                val identity = identities.first()
                val deleteResult = sdk.identity.delete(identity.keyId)
                deleteResult.onSuccess {
                    output = "Deleted ${identity.name}!\n" +
                        "Remaining in storage: ${storage.identityCount()}"
                }.onFailure { error ->
                    output = "Delete error: ${error.message}"
                }
            }
        }) { Text("Delete First Identity") }

        Spacer(Modifier.height(16.dp))
        Text(output, style = MaterialTheme.typography.bodyMedium)
    }
}
