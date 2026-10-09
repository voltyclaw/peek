package app.pane.android.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.pane.android.R
import app.pane.android.ui.theme.PaneDisplay
import app.pane.android.ui.theme.PaneInk

@Composable
fun PaneLockup(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        PaneMark(Modifier.size(22.dp))
        Text(
            text = stringResource(R.string.pane_wordmark),
            color = PaneInk,
            style = TextStyle(fontFamily = PaneDisplay, fontSize = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.025).em),
        )
    }
}

@Composable
fun PaneMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_pane_mark),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(PaneInk.copy(alpha = 0.3f)),
    )
}
