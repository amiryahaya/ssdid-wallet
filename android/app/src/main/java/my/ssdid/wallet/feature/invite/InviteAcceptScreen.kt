package my.ssdid.wallet.feature.invite

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import my.ssdid.wallet.ui.theme.*

@Composable
fun InviteAcceptScreen(
    viewModel: InviteAcceptViewModel = hiltViewModel()
) {
    val uiState by viewModel.state.collectAsState()
    val context = LocalContext.current

    // Auto-launch callback on success
    LaunchedEffect(uiState.acceptSuccess, uiState.callbackUri) {
        if (uiState.acceptSuccess && uiState.callbackUri != null) {
            val intent = Intent(Intent.ACTION_VIEW, uiState.callbackUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    // Auto-launch callback on decline (non-success callbackUri without acceptSuccess)
    LaunchedEffect(uiState.callbackUri) {
        val uri = uiState.callbackUri ?: return@LaunchedEffect
        if (!uiState.acceptSuccess && uri.getQueryParameter("status") == "cancelled") {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .statusBarsPadding().navigationBarsPadding()
    ) {
        // Header
        Row(
            Modifier.padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(12.dp))
            Text(
                "Invitation",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        }

        when {
            uiState.isLoading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = Accent,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Loading invitation...",
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            uiState.acceptSuccess -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            "Invitation Accepted",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Success
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "You have joined ${uiState.invitation?.tenantName ?: "the organization"}.",
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "Returning to SSDID Drive...",
                            fontSize = 13.sp,
                            color = TextTertiary
                        )
                        Spacer(Modifier.height(16.dp))
                        if (uiState.callbackUri != null) {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, uiState.callbackUri).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    "Return to SSDID Drive",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Accent
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Spacer(Modifier.height(4.dp))

                    // Error card
                    if (uiState.error != null && uiState.invitation == null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DangerDim)
                        ) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Error", modifier = Modifier.size(16.dp), tint = Danger)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    uiState.error!!,
                                    fontSize = 13.sp,
                                    color = Danger
                                )
                            }
                        }
                    }

                    // Invitation details card
                    val invitation = uiState.invitation
                    if (invitation != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = BgCard)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    invitation.tenantName,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(Modifier.height(12.dp))

                                if (!invitation.inviterName.isNullOrBlank()) {
                                    InvitationDetailRow("Invited by", invitation.inviterName!!)
                                }
                                InvitationDetailRow("Role", invitation.role.replaceFirstChar { it.uppercase() })
                                InvitationDetailRow("Email", invitation.email)

                                if (!invitation.message.isNullOrBlank()) {
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        "Message",
                                        fontSize = 12.sp,
                                        color = TextTertiary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        invitation.message!!,
                                        fontSize = 14.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }

                        // Email match status
                        if (uiState.emailMatch) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = SuccessDim)
                            ) {
                                Row(
                                    Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Verified", modifier = Modifier.size(16.dp), tint = Success)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "Email verified: ${uiState.walletEmail}",
                                        fontSize = 13.sp,
                                        color = Success
                                    )
                                }
                            }
                        } else if (uiState.error != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = WarningDim)
                            ) {
                                Row(
                                    Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "!",
                                        fontSize = 16.sp,
                                        color = Warning,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        uiState.error!!,
                                        fontSize = 13.sp,
                                        color = Warning
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                }

                // Footer buttons
                if (uiState.invitation != null) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Button(
                            onClick = { viewModel.accept() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = uiState.emailMatch && !uiState.isAccepting,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent)
                        ) {
                            if (uiState.isAccepting) {
                                CircularProgressIndicator(
                                    Modifier.size(20.dp),
                                    color = BgPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Accepting...",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Text(
                                    "Accept Invitation",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.decline() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isAccepting,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "Decline",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InvitationDetailRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = TextTertiary,
            fontWeight = FontWeight.Medium
        )
        Text(
            value,
            fontSize = 13.sp,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
