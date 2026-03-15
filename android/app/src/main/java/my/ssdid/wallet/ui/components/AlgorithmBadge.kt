package my.ssdid.wallet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.ssdid.wallet.ui.theme.*

@Composable
fun AlgorithmBadge(algorithmName: String, isPostQuantum: Boolean) {
    val color = if (isPostQuantum) Pqc else Classical
    val bgColor = if (isPostQuantum) PqcDim else ClassicalDim

    Box(
        Modifier.clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            algorithmName.replace("_", "-"),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}
