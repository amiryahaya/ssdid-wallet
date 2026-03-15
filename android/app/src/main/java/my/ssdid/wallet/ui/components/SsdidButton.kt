package my.ssdid.wallet.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.ssdid.wallet.ui.theme.*

enum class SsdidButtonStyle { Primary, Secondary, Danger }

@Composable
fun SsdidButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: SsdidButtonStyle = SsdidButtonStyle.Primary,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    val containerColor = when (style) {
        SsdidButtonStyle.Primary -> Accent
        SsdidButtonStyle.Secondary -> BgElevated
        SsdidButtonStyle.Danger -> Danger
    }
    val contentColor = when (style) {
        SsdidButtonStyle.Primary -> BgPrimary
        SsdidButtonStyle.Secondary -> TextPrimary
        SsdidButtonStyle.Danger -> BgPrimary
    }

    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = contentColor,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
    }
}
