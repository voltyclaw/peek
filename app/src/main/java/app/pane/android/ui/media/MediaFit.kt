package app.pane.android.ui.media

import androidx.compose.ui.unit.dp

/** Landscape video until the player reports a real size. */
internal const val VIDEO_FALLBACK_ASPECT = 16f / 9f

/** Square until a photo reports its intrinsic size. */
internal const val PHOTO_FALLBACK_ASPECT = 1f

/** Portrait media stays letterboxed instead of consuming the whole framed post. */
internal val MAX_FRAMED_MEDIA_HEIGHT = 560.dp

internal data class FittedSize(val widthPx: Float, val heightPx: Float)

/** Width divided by height. Non-positive input uses [fallback]. */
internal fun contentAspectRatio(width: Float, height: Float, fallback: Float): Float {
    if (width <= 1f || height <= 1f) return fallback
    val ratio = width / height
    return if (ratio.isFinite() && ratio > 0f) ratio else fallback
}

/** Largest size that preserves the content aspect inside the box. */
internal fun fittedContentPx(boxW: Float, boxH: Float, contentW: Float, contentH: Float): FittedSize {
    if (boxW <= 1f || boxH <= 1f) return FittedSize(0f, 0f)
    if (contentW <= 1f || contentH <= 1f) return FittedSize(boxW, boxH)
    val scale = minOf(boxW / contentW, boxH / contentH)
    if (!scale.isFinite() || scale <= 0f) return FittedSize(boxW, boxH)
    return FittedSize(contentW * scale, contentH * scale)
}

/** Pixel-aspect-corrected video size. Anamorphic frames are not stretched. */
internal fun displayVideoSize(width: Int, height: Int, pixelWidthHeightRatio: Float): Pair<Float, Float>? {
    if (width <= 0 || height <= 0) return null
    val ratio = if (pixelWidthHeightRatio > 0f && pixelWidthHeightRatio.isFinite()) pixelWidthHeightRatio else 1f
    return (width * ratio) to height.toFloat()
}
