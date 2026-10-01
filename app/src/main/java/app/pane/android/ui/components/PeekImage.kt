package app.pane.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import app.pane.android.ui.model.UiImage

@Composable
fun PeekImage(
    image: UiImage,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    onIntrinsicSize: ((width: Float, height: Float) -> Unit)? = null,
) {
    val model = when (image) {
        is UiImage.Resource -> image.id
        is UiImage.Url -> image.value
    }
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alignment = alignment,
        onSuccess = { state ->
            val size = state.painter.intrinsicSize
            if (size.width > 0f && size.height > 0f && size.width.isFinite() && size.height.isFinite()) {
                onIntrinsicSize?.invoke(size.width, size.height)
            }
        },
    )
}
