package my.ssdid.sdk.samples.credentialflow

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
                CredentialFlowScreen(sdk)
            }
        }
    }
}

@Composable
fun CredentialFlowScreen(sdk: SsdidSdk) {
    val scope = rememberCoroutineScope()
    var output by remember { mutableStateOf("Ready — follow the steps in order") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("SSDID SDK — Credential Flow", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        // Step 1: Create identity
        Text("Step 1: Create Identity", style = MaterialTheme.typography.titleSmall)
        Button(onClick = {
            scope.launch {
                output = "Creating identity..."
                val result = sdk.identity.create("Credential User", Algorithm.ED25519)
                result.onSuccess { identity ->
                    output = "Identity created!\nDID: ${identity.did}\nKey ID: ${identity.keyId}"
                }.onFailure { error ->
                    output = "Error: ${error.message}"
                }
            }
        }) { Text("Create Identity") }

        Spacer(Modifier.height(16.dp))

        // Step 2: Process OID4VCI credential offer (simulated)
        Text("Step 2: Receive Credential (OID4VCI)", style = MaterialTheme.typography.titleSmall)
        Button(onClick = {
            scope.launch {
                // Simulated OID4VCI credential offer URI
                val offerUri = "openid-credential-offer://?credential_offer_uri=https://issuer.example.com/offers/abc123"
                output = "Processing OID4VCI offer...\nURI: $offerUri\n\n"
                val result = sdk.issuance.processOffer(offerUri)
                result.onSuccess { review ->
                    output += "Offer parsed successfully!\n" +
                        "Issuer: ${review.metadata.credentialIssuer}\n" +
                        "Available credentials: ${review.metadata.credentialConfigurationsSupported.keys.joinToString()}\n\n" +
                        "In a real app, you would now call sdk.issuance.acceptOffer(...) " +
                        "with the selected credential configuration."
                }.onFailure { error ->
                    output += "Expected error (simulated URI): ${error.message}\n\n" +
                        "In a real app, the offer URI would come from scanning a QR code " +
                        "or clicking a deep link from the issuer."
                }
            }
        }) { Text("Process Credential Offer") }

        Spacer(Modifier.height(16.dp))

        // Step 3: List stored credentials
        Text("Step 3: List Credentials", style = MaterialTheme.typography.titleSmall)
        Button(onClick = {
            scope.launch {
                val credentials = sdk.credentials.list()
                output = if (credentials.isEmpty()) {
                    "No credentials stored yet.\n\n" +
                        "Credentials are stored after successfully completing " +
                        "an OID4VCI issuance flow with a real issuer."
                } else {
                    "Credentials (${credentials.size}):\n" +
                        credentials.joinToString("\n") { vc ->
                            "- ${vc.id}: ${vc.type.joinToString()}"
                        }
                }
            }
        }) { Text("List Credentials") }

        Spacer(Modifier.height(16.dp))

        // Step 4: Process OID4VP presentation request (simulated)
        Text("Step 4: Present Credential (OID4VP)", style = MaterialTheme.typography.titleSmall)
        Button(onClick = {
            scope.launch {
                // Simulated OID4VP authorization request URI
                val requestUri = "openid4vp://?request_uri=https://verifier.example.com/requests/xyz789"
                output = "Processing OID4VP request...\nURI: $requestUri\n\n"
                val result = sdk.presentation.processRequest(requestUri)
                result.onSuccess { review ->
                    output += "Request parsed!\n" +
                        "Verifier: ${review.authRequest.clientId}\n" +
                        "Matching credential groups: ${review.matches.size}\n\n" +
                        "In a real app, you would now call sdk.presentation.submitPresentation(...) " +
                        "with the selected claims."
                }.onFailure { error ->
                    output += "Expected error (simulated URI): ${error.message}\n\n" +
                        "In a real app, the request URI would come from scanning " +
                        "a QR code from the verifier."
                }
            }
        }) { Text("Process Presentation Request") }

        Spacer(Modifier.height(16.dp))
        Divider()
        Spacer(Modifier.height(8.dp))
        Text(output, style = MaterialTheme.typography.bodyMedium)
    }
}
