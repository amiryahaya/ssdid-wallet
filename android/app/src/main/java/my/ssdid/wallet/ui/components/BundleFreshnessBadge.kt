package my.ssdid.wallet.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun BundleFreshnessBadge(freshnessRatio: Double) {
    if (freshnessRatio < 0.5) return // No indicator when fresh

    val (label, color) = if (freshnessRatio < 1.0) {
        "Bundle aging" to Color(0xFFF9A825)
    } else {
        "Bundle expired" to Color(0xFFC62828)
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
