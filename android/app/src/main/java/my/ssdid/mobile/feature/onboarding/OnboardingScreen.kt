package my.ssdid.mobile.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import kotlinx.coroutines.launch
import my.ssdid.mobile.ui.theme.*

private data class OnboardingSlide(
    val icon: String,
    val title: String,
    val description: String,
    val accentColor: androidx.compose.ui.graphics.Color
)

private val slides = listOf(
    OnboardingSlide(
        icon = "\uD83D\uDD10",
        title = "Your Identity, Your Control",
        description = "SSDID is a self-sovereign identity wallet that puts you in full control of your digital identity. No central authority. No data silos. Just you and your cryptographic keys.",
        accentColor = Accent
    ),
    OnboardingSlide(
        icon = "\uD83D\uDEE1\uFE0F",
        title = "Post-Quantum Security",
        description = "Powered by KAZ-Sign, a post-quantum cryptographic algorithm, alongside classical algorithms like Ed25519 and ECDSA. Your identity stays secure against both current and future threats.",
        accentColor = Pqc
    ),
    OnboardingSlide(
        icon = "\uD83D\uDCF1",
        title = "Seamless Verification",
        description = "Authenticate with services instantly using QR codes and deep links. Register, prove your identity, and sign transactions — all from your device with biometric confirmation.",
        accentColor = Success
    )
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .statusBarsPadding()
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            val slide = slides[page]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(slide.accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(slide.icon, fontSize = 42.sp)
                }
                Spacer(Modifier.height(40.dp))
                Text(
                    slide.title,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    slide.description,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = TextSecondary,
                    lineHeight = 24.sp
                )
            }
        }

        // Dot indicators
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(slides.size) { index ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (isSelected) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Accent else TextTertiary)
                )
            }
        }

        // Button
        val isLastPage = pagerState.currentPage == slides.size - 1
        Button(
            onClick = {
                if (isLastPage) {
                    onComplete()
                } else {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent)
        ) {
            Text(
                if (isLastPage) "Get Started" else "Next",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}
