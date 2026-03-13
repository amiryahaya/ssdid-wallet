package my.ssdid.wallet.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import my.ssdid.wallet.ui.theme.*

@Composable
fun EmailVerificationScreen(
    onVerified: () -> Unit,
    onBack: () -> Unit,
    viewModel: EmailVerificationViewModel = hiltViewModel()
) {
    val code by viewModel.code.collectAsState()
    val sending by viewModel.sending.collectAsState()
    val verifying by viewModel.verifying.collectAsState()
    val verified by viewModel.verified.collectAsState()
    val error by viewModel.error.collectAsState()
    val cooldown by viewModel.cooldown.collectAsState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(verified) {
        if (verified) onVerified()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .background(BgPrimary)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            Modifier.padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text("Verify Email", style = MaterialTheme.typography.titleLarge)
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                "We sent a verification code to",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                viewModel.email,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // Code input
            OutlinedTextField(
                value = code,
                onValueChange = { viewModel.updateCode(it) },
                modifier = Modifier.width(200.dp),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 24.sp,
                    letterSpacing = 8.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                ),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    viewModel.verify()
                }),
                placeholder = {
                    Text(
                        "000000",
                        fontSize = 24.sp,
                        letterSpacing = 8.sp,
                        textAlign = TextAlign.Center,
                        color = TextTertiary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )

            Spacer(Modifier.height(8.dp))
            Text(
                "Enter 6-digit code",
                fontSize = 12.sp,
                color = TextTertiary
            )

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(error!!, fontSize = 13.sp, color = Danger, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(24.dp))

            // Resend
            if (cooldown > 0) {
                Text(
                    "Resend code in ${cooldown}s",
                    fontSize = 13.sp,
                    color = TextTertiary
                )
            } else {
                TextButton(
                    onClick = { viewModel.sendCode() },
                    enabled = !sending
                ) {
                    Text(
                        if (sending) "Sending..." else "Resend Code",
                        fontSize = 13.sp,
                        color = if (sending) TextTertiary else Accent
                    )
                }
            }
        }

        // Verify button
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Button(
                onClick = { viewModel.verify() },
                modifier = Modifier.fillMaxWidth(),
                enabled = code.length == 6 && !verifying,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                if (verifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Verify", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
