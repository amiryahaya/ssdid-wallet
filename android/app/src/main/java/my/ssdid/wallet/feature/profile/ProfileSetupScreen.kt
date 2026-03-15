package my.ssdid.wallet.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import my.ssdid.wallet.ui.theme.*

@Composable
fun ProfileSetupScreen(
    onComplete: (email: String) -> Unit,
    onEmailChanged: ((email: String) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    buttonText: String = "Continue",
    viewModel: ProfileSetupViewModel = hiltViewModel()
) {
    val name by viewModel.name.collectAsState()
    val email by viewModel.email.collectAsState()
    val nameError by viewModel.nameError.collectAsState()
    val emailError by viewModel.emailError.collectAsState()
    val isValid by viewModel.isValid.collectAsState()
    val saved by viewModel.saved.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val error by viewModel.error.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val emailChanged by viewModel.emailChanged.collectAsState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(saved) {
        if (saved) {
            if (emailChanged && onEmailChanged != null) {
                onEmailChanged(email)
            } else {
                onComplete(email)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .background(BgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header
        Row(
            Modifier.padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Spacer(Modifier.width(4.dp))
            } else {
                Spacer(Modifier.width(16.dp))
            }
            Text(
                if (onBack != null) "Edit Profile" else "Set Up Your Profile",
                style = MaterialTheme.typography.titleLarge
            )
        }

        if (loading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    "This information can be shared when you sign in to services using your SSDID wallet.",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Name field
                Text("NAME *", style = MaterialTheme.typography.labelMedium, color = TextTertiary)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { viewModel.updateName(it) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it, color = Danger) } },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) })
                )

                Spacer(Modifier.height(12.dp))

                // Email field
                Text("EMAIL *", style = MaterialTheme.typography.labelMedium, color = TextTertiary)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { viewModel.updateEmail(it) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = emailError != null,
                    supportingText = emailError?.let { { Text(it, color = Danger) } },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )

                Spacer(Modifier.height(8.dp))
                Text("* Required", fontSize = 12.sp, color = TextTertiary)

                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(error!!, fontSize = 13.sp, color = Danger)
                }
            }

            // Footer
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Button(
                    onClick = { viewModel.save() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isValid && !saving,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    Text(buttonText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                if (onBack == null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "You can edit this later in Settings.",
                        fontSize = 12.sp,
                        color = TextTertiary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                }
            }
        }
    }
}
