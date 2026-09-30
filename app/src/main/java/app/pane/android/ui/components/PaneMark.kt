package app.pane.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pane.android.R
import app.pane.android.ui.theme.Geist
import app.pane.android.ui.theme.PaneAccent
import app.pane.android.ui.theme.PaneInk

@Composable
fun PaneLockup(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        PaneMark(Modifier.size(18.dp))
        Text(
            text = stringResource(R.string.pane_wordmark),
            color = PaneInk,
            style = TextStyle(fontFamily = Geist, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        )
    }
}

@Composable
fun PaneMark(modifier: Modifier = Modifier) {
    val accent = PaneAccent
    Canvas(modifier = modifier) {
        val stroke = 1.6.dp.toPx()
        val outer = stroke / 2f
        drawRoundRect(
            color = accent,
            topLeft = Offset(outer, outer),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(size.minDimension * 0.22f),
            style = Stroke(width = stroke),
        )
        val inset = size.minDimension * 0.28f
        drawRoundRect(
            color = accent,
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2f, size.height - inset * 2f),
            cornerRadius = CornerRadius(size.minDimension * 0.12f),
            style = Stroke(width = stroke * 0.85f),
        )
    }
}
