package app.pane.android.ui.actions

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import app.pane.android.R
import app.pane.android.domain.model.RemoteMedia
import app.pane.android.domain.model.RemoteMediaKind
import app.pane.android.domain.usecase.DownloadMediaUseCase
import app.pane.android.domain.usecase.PrepareMediaForSharingUseCase
import app.pane.android.ui.model.UiImage
import app.pane.android.ui.model.ViewerMediaItemUiModel
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

data class PostActionCallbacks(
    val onCopyLink: suspend (String) -> Unit,
    val onCopyMedia: suspend (ViewerMediaItemUiModel) -> Unit,
    val onDownload: suspend (List<ViewerMediaItemUiModel>) -> Unit,
    val onShare: suspend (List<ViewerMediaItemUiModel>) -> Unit,
    val onSharePost: suspend (String, String?) -> Unit,
    val onOpenInApp: suspend (String) -> Unit,
)

@Composable
fun rememberPostActionCallbacks(
    prepareMediaForSharing: PrepareMediaForSharingUseCase,
    downloadMedia: DownloadMediaUseCase,
): PostActionCallbacks {
    val context = LocalContext.current
    val resources = LocalResources.current
    val clipboard = LocalClipboard.current
    val linkCopied = stringResource(R.string.link_copied)
    val mediaCopied = stringResource(R.string.media_copied)
    val actionFailed = stringResource(R.string.action_failed)

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    var pendingPermissionContinuation by remember {
        mutableStateOf<CancellableContinuation<Boolean>?>(null)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        pendingPermissionContinuation
            ?.takeIf(CancellableContinuation<Boolean>::isActive)
            ?.resume(granted)
        pendingPermissionContinuation = null
    }

    suspend fun requestLegacyWritePermission(): Boolean =
        suspendCancellableCoroutine { continuation ->
            pendingPermissionContinuation?.cancel()
            pendingPermissionContinuation = continuation
            continuation.invokeOnCancellation {
                if (pendingPermissionContinuation === continuation) {
                    pendingPermissionContinuation = null
                }
            }
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

    return PostActionCallbacks(
        onCopyLink = { url ->
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Link", url)))
            toast(linkCopied)
        },
        onCopyMedia = { item ->
            val media = item.toRemoteMedia()
            val prepared = media
                ?.let { prepareMediaForSharing(listOf(it)).getOrNull()?.singleOrNull() }
            val clipData = prepared?.let { mediaClipData(context, it) }
            if (clipData != null) {
                clipboard.setClipEntry(ClipEntry(clipData))
                toast(mediaCopied)
            } else {
                toast(actionFailed)
            }
        },
        onDownload = { items ->
            val media = items.toRemoteMedia()
            if (media == null) {
                toast(actionFailed)
            } else {
                val needsLegacyPermission =
                    Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        ) != PackageManager.PERMISSION_GRANTED
                val granted = !needsLegacyPermission || requestLegacyWritePermission()
                if (granted) {
                    val count = downloadMedia(media)
                    val message = if (count > 0) {
                        resources.getQuantityString(
                            R.plurals.media_saved_count,
                            count,
                            count,
                        )
                    } else {
                        actionFailed
                    }
                    toast(message)
                } else {
                    toast(actionFailed)
                }
            }
        },
        onShare = { items ->
            val media = items.toRemoteMedia()
            val prepared = media?.let { prepareMediaForSharing(it).getOrNull() }
            val intent = prepared?.let { shareMediaIntent(context, it) }
            val launched = intent != null && runCatching {
                context.startActivity(Intent.createChooser(intent, null))
            }.isSuccess
            if (!launched) toast(actionFailed)
        },
        onSharePost = { url, title ->
            val launched = url.isNotBlank() && runCatching {
                context.startActivity(
                    Intent.createChooser(
                        sharePostIntent(url, title, context.getString(R.string.share_message_line)),
                        null,
                    ),
                )
            }.isSuccess
            if (!launched) toast(actionFailed)
        },
        onOpenInApp = { url ->
            if (!openPostInApp(context, url)) toast(actionFailed)
        },
    )
}

private fun ViewerMediaItemUiModel.toRemoteMedia(): RemoteMedia? {
    val url = videoUrl?.takeIf { it.isNotBlank() }
        ?: (image as? UiImage.Url)?.value?.takeIf { it.isNotBlank() }
        ?: return null
    return RemoteMedia(
        id = id,
        url = url,
        kind = if (videoUrl == null) RemoteMediaKind.Image else RemoteMediaKind.Video,
    )
}

private fun List<ViewerMediaItemUiModel>.toRemoteMedia(): List<RemoteMedia>? =
    map { it.toRemoteMedia() ?: return null }
