package my.ssdid.sdk.samples.basicidentity

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

        val sdk = SsdidSdk.builder(applicationContext)
            .registryUrl("https://registry.ssdid.my")
            .build()

        setContent {
            MaterialTheme {
                BasicIdentityScreen(sdk)
            }
        }
    }
}

@Composable
fun BasicIdentityScreen(sdk: SsdidSdk) {
    val scope = rememberCoroutineScope()
    var output by remember { mutableStateOf("Ready") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("SSDID SDK — Basic Identity", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        Button(onClick = {
            scope.launch {
                output = "Creating identity..."
                val result = sdk.identity.create("Alice", Algorithm.ED25519)
                result.onSuccess { identity ->
                    output = "Created!\nDID: ${identity.did}\nKey ID: ${identity.keyId}\nAlgorithm: ${identity.algorithm}"
                }.onFailure { error ->
                    output = "Error: ${error.message}"
                }
            }
        }) { Text("Create Identity (Ed25519)") }

        Spacer(Modifier.height(8.dp))

        Button(onClick = {
            scope.launch {
                val identities = sdk.identity.list()
                output = "Identities (${identities.size}):\n" +
                    identities.joinToString("\n") { "- ${it.name}: ${it.did}" }
            }
        }) { Text("List Identities") }

        Spacer(Modifier.height(8.dp))

        Button(onClick = {
            scope.launch {
                val identities = sdk.identity.list()
                if (identities.isEmpty()) {
                    output = "No identities. Create one first."
                    return@launch
                }
                val identity = identities.first()
                val data = "Hello, SSDID!".toByteArray()
                output = "Signing with ${identity.keyId}..."
                val signResult = sdk.vault.sign(identity.keyId, data)
                signResult.onSuccess { sig ->
                    val hex = sig.take(16).joinToString("") { "%02x".format(it) }
                    output = "Signed!\nSignature (first 16 bytes): $hex..."
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
                    output = "No identities. Create one first."
                    return@launch
                }
                val identity = identities.first()
                output = "Building DID Document for ${identity.did}..."
                val docResult = sdk.identity.buildDidDocument(identity.keyId)
                docResult.onSuccess { doc ->
                    output = "DID Document:\nID: ${doc.id}\nVerification Methods: ${doc.verificationMethod.size}"
                }.onFailure { error ->
                    output = "Error: ${error.message}"
                }
            }
        }) { Text("Build DID Document") }

        Spacer(Modifier.height(16.dp))

        Text(output, style = MaterialTheme.typography.bodyMedium)
    }
}
