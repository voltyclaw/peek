package app.pane.android.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.pane.android.ui.PaneTestTags
import app.pane.android.ui.theme.PaneInk
import app.pane.android.ui.theme.PaneTile
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun PaneSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier.testTag(PaneTestTags.SNACKBAR)) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = lerp(PaneTile, PaneInk, 0.09f),
            contentColor = PaneInk,
            actionColor = PaneInk,
            actionContentColor = PaneInk,
            shape = RoundedCornerShape(12.dp),
        )
    }
}

/** Undo stays up for 5 seconds. A message with no action uses the same window. */
suspend fun SnackbarHostState.showForFiveSeconds(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    currentSnackbarData?.dismiss()
    val result = withTimeoutOrNull(5_000) {
        showSnackbar(
            message = message,
            actionLabel = if (onAction != null) actionLabel else null,
            duration = SnackbarDuration.Indefinite,
        )
    }
    if (result == null) {
        currentSnackbarData?.dismiss()
        return
    }
    if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
}
