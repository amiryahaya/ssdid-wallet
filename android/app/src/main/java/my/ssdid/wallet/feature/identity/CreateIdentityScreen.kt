package my.ssdid.wallet.feature.identity

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import my.ssdid.wallet.domain.SsdidClient
import my.ssdid.wallet.domain.model.Algorithm
import my.ssdid.wallet.domain.transport.ConfirmCodeRequest
import my.ssdid.wallet.domain.transport.EmailVerifyApi
import my.ssdid.wallet.domain.transport.SendCodeRequest
import my.ssdid.wallet.domain.vault.Vault
import my.ssdid.wallet.domain.vault.VaultStorage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.platform.LocalView
import my.ssdid.wallet.ui.components.HapticManager
import my.ssdid.wallet.ui.theme.*
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CreateIdentityViewModel @Inject constructor(
    private val client: SsdidClient,
    private val vault: Vault,
    private val storage: VaultStorage,
    private val emailVerifyApi: EmailVerifyApi,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val acceptedAlgorithms: List<Algorithm> = run {
        val raw = savedStateHandle.get<String>("acceptedAlgorithms") ?: ""
        if (raw.isBlank()) Algorithm.entries.toList()
        else {
            val names = try {
                Json.decodeFromString<List<String>>(raw)
            } catch (_: Exception) { emptyList() }
            if (names.isEmpty()) Algorithm.entries.toList()
            else Algorithm.entries.filter { it.name in names }
        }
    }

    private val _isCreating = MutableStateFlow(false)
    val isCreating = _isCreating.asStateFlow()

    private val _isSendingCode = MutableStateFlow(false)
    val isSendingCode = _isSendingCode.asStateFlow()

    private val _isVerifying = MutableStateFlow(false)
    val isVerifying = _isVerifying.asStateFlow()

    private val _emailVerified = MutableStateFlow(false)
    val emailVerified = _emailVerified.asStateFlow()

    private val _cooldown = MutableStateFlow(0)
    val cooldown = _cooldown.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private var cooldownJob: Job? = null
    private var resendCount = 0

    @SuppressLint("HardwareIds")
    private val deviceId: String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: UUID.randomUUID().toString()

    fun sendVerificationCode(email: String, onSuccess: () -> Unit) {
        if (_isSendingCode.value || _cooldown.value > 0) return
        viewModelScope.launch {
            _isSendingCode.value = true
            _error.value = null
            try {
                emailVerifyApi.sendCode(SendCodeRequest(email, deviceId))
                startCooldown()
                onSuccess()
            } catch (e: retrofit2.HttpException) {
                _error.value = if (e.code() == 429) {
                    "Too many requests. Please wait before trying again."
                } else {
                    "Failed to send verification code. Please try again."
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to send verification code"
            }
            _isSendingCode.value = false
        }
    }

    fun verifyCode(email: String, code: String, onSuccess: () -> Unit) {
        if (code.length != 6 || _isVerifying.value) return
        viewModelScope.launch {
            _isVerifying.value = true
            _error.value = null
            try {
                val response = emailVerifyApi.confirmCode(ConfirmCodeRequest(email, code, deviceId))
                if (response.verified) {
                    _emailVerified.value = true
                    onSuccess()
                } else {
                    _error.value = "Invalid verification code. Please try again."
                }
            } catch (e: retrofit2.HttpException) {
                _error.value = when (e.code()) {
                    429 -> "Too many failed attempts. Try again in 15 minutes."
                    400 -> "Invalid code. Please check and try again."
                    else -> "Verification failed."
                }
            } catch (e: Exception) {
                _error.value = "Invalid verification code. Please try again."
            }
            _isVerifying.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun createIdentity(
        name: String,
        algorithm: Algorithm,
        profileName: String?,
        email: String?,
        emailVerified: Boolean,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isCreating.value = true
            _error.value = null
            client.initIdentity(name, algorithm)
                .onSuccess { identity ->
                    if (!profileName.isNullOrBlank() || !email.isNullOrBlank()) {
                        vault.updateIdentityProfile(
                            identity.keyId,
                            profileName = profileName?.takeIf { it.isNotBlank() },
                            email = email?.takeIf { it.isNotBlank() },
                            emailVerified = if (emailVerified) true else null
                        ).onFailure { e ->
                            io.sentry.Sentry.captureException(e)
                        }
                    }
                    storage.setOnboardingCompleted()
                    onSuccess()
                }
                .onFailure {
                    io.sentry.Sentry.captureException(it)
                    _error.value = it.message ?: "Failed to create identity"
                }
            _isCreating.value = false
        }
    }

    private fun startCooldown() {
        resendCount++
        val seconds = when {
            resendCount <= 1 -> 60
            resendCount == 2 -> 120
            else -> 300
        }
        _cooldown.value = seconds
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            while (_cooldown.value > 0) {
                delay(1000)
                _cooldown.value = _cooldown.value - 1
            }
        }
    }
}

@Composable
fun CreateIdentityScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: CreateIdentityViewModel = hiltViewModel()
) {
    val view = LocalView.current
    var currentStep by remember { mutableIntStateOf(1) }

    // Step 1 state
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    // Step 2 state
    var verificationCode by remember { mutableStateOf("") }

    // Step 3 state
    var identityName by remember { mutableStateOf("") }
    var selectedAlgo by remember {
        val preferred = viewModel.acceptedAlgorithms.find { it == Algorithm.KAZ_SIGN_192 }
        mutableStateOf(preferred ?: viewModel.acceptedAlgorithms.first())
    }

    val isCreating by viewModel.isCreating.collectAsState()
    val isSendingCode by viewModel.isSendingCode.collectAsState()
    val isVerifying by viewModel.isVerifying.collectAsState()
    val emailVerified by viewModel.emailVerified.collectAsState()
    val cooldown by viewModel.cooldown.collectAsState()
    val error by viewModel.error.collectAsState()

    val isStep1Valid = displayName.isNotBlank() &&
            email.contains("@") && email.substringAfter("@").contains(".")

    // Haptic feedback on error
    LaunchedEffect(error) {
        if (error != null) HapticManager.error(view)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header with step indicator
        Row(
            Modifier.padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                viewModel.clearError()
                if (currentStep > 1) currentStep-- else onBack()
            }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Spacer(Modifier.width(4.dp))
            Text("Create Identity", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Text("Step $currentStep of 3", fontSize = 13.sp, color = TextTertiary)
        }

        // Step indicator bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (index < currentStep) Accent else Border)
                )
            }
        }

        // Error
        error?.let { msg ->
            Text(
                msg,
                color = Danger,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }

        // Step content
        when (currentStep) {
            1 -> StepProfile(
                displayName = displayName,
                onDisplayNameChange = { displayName = it },
                email = email,
                onEmailChange = { email = it },
                isValid = isStep1Valid,
                isSendingCode = isSendingCode,
                onNext = {
                    viewModel.sendVerificationCode(email) {
                        currentStep = 2
                    }
                }
            )
            2 -> StepVerifyEmail(
                email = email,
                verificationCode = verificationCode,
                onCodeChange = { value ->
                    if (value.length <= 6 && value.all { it.isDigit() }) {
                        verificationCode = value
                        viewModel.clearError()
                    }
                },
                isVerifying = isVerifying,
                isSendingCode = isSendingCode,
                cooldown = cooldown,
                onVerify = {
                    viewModel.verifyCode(email, verificationCode) {
                        currentStep = 3
                    }
                },
                onResend = {
                    viewModel.sendVerificationCode(email) {}
                }
            )
            3 -> StepCreateIdentity(
                identityName = identityName,
                onIdentityNameChange = { identityName = it },
                selectedAlgo = selectedAlgo,
                onAlgoSelected = { selectedAlgo = it },
                acceptedAlgorithms = viewModel.acceptedAlgorithms,
                isCreating = isCreating,
                onCreate = {
                    if (identityName.isNotBlank()) {
                        viewModel.createIdentity(
                            name = identityName,
                            algorithm = selectedAlgo,
                            profileName = displayName.ifBlank { null },
                            email = email.ifBlank { null },
                            emailVerified = emailVerified
                        ) {
                            HapticManager.success(view)
                            onCreated()
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ColumnScope.StepProfile(
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    isValid: Boolean,
    isSendingCode: Boolean,
    onNext: () -> Unit
) {
    LazyColumn(
        Modifier
            .weight(1f)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("DISPLAY NAME", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = displayName,
                onValueChange = onDisplayNameChange,
                placeholder = { Text("Your full name", color = TextTertiary) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Border,
                    cursorColor = Accent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                singleLine = true
            )
        }

        item {
            Text("EMAIL", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                placeholder = { Text("your@email.com", color = TextTertiary) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Border,
                    cursorColor = Accent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
        }
    }

    Button(
        onClick = onNext,
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        enabled = isValid && !isSendingCode,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Accent)
    ) {
        if (isSendingCode) {
            CircularProgressIndicator(
                Modifier.size(20.dp),
                color = BgPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Text("Verify Email", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ColumnScope.StepVerifyEmail(
    email: String,
    verificationCode: String,
    onCodeChange: (String) -> Unit,
    isVerifying: Boolean,
    isSendingCode: Boolean,
    cooldown: Int,
    onVerify: () -> Unit,
    onResend: () -> Unit
) {
    val focusManager = LocalFocusManager.current

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
            email,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        // Code input
        OutlinedTextField(
            value = verificationCode,
            onValueChange = onCodeChange,
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
                onVerify()
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
                onClick = onResend,
                enabled = !isSendingCode
            ) {
                Text(
                    if (isSendingCode) "Sending..." else "Resend Code",
                    fontSize = 13.sp,
                    color = if (isSendingCode) TextTertiary else Accent
                )
            }
        }
    }

    Button(
        onClick = onVerify,
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        enabled = verificationCode.length == 6 && !isVerifying,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Accent)
    ) {
        if (isVerifying) {
            CircularProgressIndicator(
                Modifier.size(20.dp),
                color = BgPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Text("Verify", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ColumnScope.StepCreateIdentity(
    identityName: String,
    onIdentityNameChange: (String) -> Unit,
    selectedAlgo: Algorithm,
    onAlgoSelected: (Algorithm) -> Unit,
    acceptedAlgorithms: List<Algorithm>,
    isCreating: Boolean,
    onCreate: () -> Unit
) {
    LazyColumn(
        Modifier
            .weight(1f)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("IDENTITY NAME", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = identityName,
                onValueChange = onIdentityNameChange,
                placeholder = { Text("e.g. Personal, Work", color = TextTertiary) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Border,
                    cursorColor = Accent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                singleLine = true
            )
        }

        item {
            Text("SIGNATURE ALGORITHM", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                acceptedAlgorithms.forEach { algo ->
                    val isSelected = selectedAlgo == algo
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) AccentDim else BgCard
                        ),
                        onClick = { onAlgoSelected(algo) }
                    ) {
                        Row(Modifier.padding(14.dp)) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onAlgoSelected(algo) },
                                colors = RadioButtonDefaults.colors(selectedColor = Accent)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    algo.name.replace("_", " "),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(algo.w3cType, fontSize = 11.sp, color = TextTertiary)
                            }
                        }
                    }
                }
            }
        }
    }

    Button(
        onClick = onCreate,
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        enabled = identityName.isNotBlank() && !isCreating,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Accent)
    ) {
        if (isCreating) {
            CircularProgressIndicator(
                Modifier.size(20.dp),
                color = BgPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Text("Create Identity", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
