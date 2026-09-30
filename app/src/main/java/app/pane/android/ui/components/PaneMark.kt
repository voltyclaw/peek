package app.pane.android.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pane.android.R
import app.pane.android.ui.theme.Geist
import app.pane.android.ui.theme.LocalPaneColors
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
    val mark = if (LocalPaneColors.current.night) R.drawable.pane_mark_dark else R.drawable.pane_mark_light
    Image(
        painter = painterResource(mark),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
