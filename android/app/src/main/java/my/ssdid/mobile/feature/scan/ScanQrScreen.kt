package my.ssdid.mobile.feature.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.ssdid.mobile.ui.theme.*

@Composable
fun ScanQrScreen(
    onBack: () -> Unit,
    onScanned: (serverUrl: String, serverDid: String, action: String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .statusBarsPadding()
    ) {
        // Header
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("\u2190", color = TextPrimary, fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Text("Scan QR Code", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.height(16.dp))

        // Camera preview placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(BgSecondary),
            contentAlignment = Alignment.Center
        ) {
            // Scanning frame overlay
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .border(
                        width = 3.dp,
                        color = Accent.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "\uD83D\uDCF7",
                        fontSize = 48.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Camera Preview",
                        color = TextSecondary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "CameraX integration in Task 11",
                        color = TextTertiary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Info section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = BgCard)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("SUPPORTED FORMATS", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                QrFormatRow("SSDID Registration", "ssdid://register?url=...&did=...")
                Spacer(Modifier.height(6.dp))
                QrFormatRow("SSDID Authentication", "ssdid://auth?url=...&did=...")
                Spacer(Modifier.height(6.dp))
                QrFormatRow("SSDID Transaction", "ssdid://tx?url=...&session=...")
            }
        }

        Spacer(Modifier.height(12.dp))

        // Placeholder scan button for testing navigation
        Button(
            onClick = {
                onScanned(
                    "https://demo.ssdid.my",
                    "did:ssdid:server:demo",
                    "register"
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentDim)
        ) {
            Text(
                "Simulate Scan (Demo)",
                color = Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun QrFormatRow(label: String, format: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Accent)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, fontSize = 13.sp, color = TextPrimary)
            Text(format, fontSize = 11.sp, color = TextTertiary, textAlign = TextAlign.Start)
        }
    }
}
