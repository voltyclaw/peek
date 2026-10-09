package app.pane.android.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.window.Dialog
import app.pane.android.R
import app.pane.android.ui.actions.RecoveryBody
import app.pane.android.ui.actions.RecoveryHeadline
import app.pane.android.ui.actions.RecoveryReason
import app.pane.android.ui.actions.OpenAffordance
import app.pane.android.ui.actions.openAffordance
import app.pane.android.ui.actions.packageInstalled
import app.pane.android.ui.actions.recoveryPresentation
import app.pane.android.ui.theme.Inter
import app.pane.android.ui.theme.PaneDisplay
import app.pane.android.ui.theme.PaneAccent
import app.pane.android.ui.theme.PaneBorder
import app.pane.android.ui.theme.PaneGround
import app.pane.android.ui.theme.PaneInk
import app.pane.android.ui.theme.PaneMuted
import app.pane.android.ui.theme.PaneTile

@Composable
internal fun DonePill(onDone: () -> Unit, overMedia: Boolean, modifier: Modifier = Modifier) {
    val fill = if (overMedia) PaneGround.copy(alpha = 0.6f) else PaneTile
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(fill)
            .clickable(role = Role.Button, onClick = onDone)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.done), tint = PaneMuted, modifier = Modifier.size(16.dp))
        Text(
            text = stringResource(R.string.done),
            color = PaneMuted,
            style = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Medium),
        )
    }
}

/** Top bar of the post viewer. Done on the left. Star and refresh share a capsule on the right. */
internal enum class ViewerChromeControl { Done, Star, Refresh }

internal fun viewerTopChrome(): List<ViewerChromeControl> =
    listOf(ViewerChromeControl.Done, ViewerChromeControl.Star, ViewerChromeControl.Refresh)

@Composable
internal fun ViewerTopBar(
    onDone: () -> Unit,
    onRefresh: () -> Unit,
    overMedia: Boolean,
    modifier: Modifier = Modifier,
    starred: Boolean = false,
    onStar: () -> Unit = {},
    showRefresh: Boolean = true,
) {
    val controls = viewerTopChrome()
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = if (overMedia) 10.dp else 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ViewerChromeControl.Done in controls) DonePill(onDone, overMedia = overMedia)
        if (ViewerChromeControl.Star in controls || ViewerChromeControl.Refresh in controls) {
            StarRefreshCapsule(
                showStar = ViewerChromeControl.Star in controls,
                showRefresh = showRefresh && ViewerChromeControl.Refresh in controls,
                starred = starred,
                overMedia = overMedia,
                onStar = onStar,
                onRefresh = onRefresh,
            )
        }
    }
}

@Composable
private fun StarRefreshCapsule(
    showStar: Boolean,
    showRefresh: Boolean,
    starred: Boolean,
    overMedia: Boolean,
    onStar: () -> Unit,
    onRefresh: () -> Unit,
) {
    val fill = if (overMedia) PaneGround.copy(alpha = 0.6f) else PaneTile
    Row(
        modifier = Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(fill),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showStar) StarButton(starred, onStar)
        if (showStar && showRefresh) Box(Modifier.width(1.dp).height(16.dp).background(PaneBorder))
        if (showRefresh) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClick = onRefresh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.refresh), tint = PaneMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun StarButton(starred: Boolean, onStar: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "starScale")
    val label = stringResource(if (starred) R.string.unstar else R.string.star)
    Box(
        modifier = Modifier
            .size(40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onStar),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (starred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            contentDescription = label,
            tint = if (starred) PaneInk else PaneMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
internal fun ViewerBottomBar(
    openLabel: String,
    onShare: () -> Unit,
    onOverflow: () -> Unit,
    onOpen: () -> Unit,
    sourceMark: Int? = null,
    useGlobe: Boolean = false,
    showOpen: Boolean = true,
    openWord: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().background(PaneGround)) {
        HorizontalDivider(color = PaneBorder, thickness = 1.dp)
        Row(
            modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuietIconButton(Icons.Rounded.Share, stringResource(R.string.share), onShare)
            QuietIconButton(Icons.Rounded.MoreHoriz, stringResource(R.string.more_options), onOverflow)
            Box(Modifier.weight(1f))
            if (showOpen && openWord != null) Row(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(PaneAccent)
                    .clickable(role = Role.Button, onClick = onOpen)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = openLabel, tint = PaneGround, modifier = Modifier.size(18.dp))
                Text(openWord, color = PaneGround, style = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Medium))
            } else if (showOpen) Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(PaneAccent)
                    .clickable(role = Role.Button, onClick = onOpen),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    sourceMark != null -> Icon(
                        painter = painterResource(sourceMark),
                        contentDescription = openLabel,
                        tint = PaneGround,
                        modifier = Modifier.size(22.dp),
                    )
                    useGlobe -> Icon(
                        Icons.Rounded.Public,
                        contentDescription = openLabel,
                        tint = PaneGround,
                        modifier = Modifier.size(22.dp),
                    )
                    else -> Icon(
                        Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = openLabel,
                        tint = PaneGround,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun RefreshButton(onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClick = onRefresh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.refresh), tint = PaneMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun QuietIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = PaneMuted, modifier = Modifier.size(22.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OverflowSheet(
    contextLine: String,
    canDownload: Boolean,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onCopyLink: () -> Unit,
    onDownload: () -> Unit,
    onAddNote: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PaneTile,
        scrimColor = PaneGround.copy(alpha = 0.66f),
        dragHandle = {
            Box(
                Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(PaneBorder),
            )
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
            Text(
                text = contextLine,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                color = PaneInk,
                style = TextStyle(fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            )
            overflowActions(canDownload).forEach { action ->
                when (action) {
                    OverflowAction.Share -> SheetRow(Icons.Rounded.Share, stringResource(R.string.share), null, onShare)
                    OverflowAction.CopyLink -> SheetRow(Icons.Rounded.ContentCopy, stringResource(R.string.copy_link), null, onCopyLink)
                    OverflowAction.Download -> SheetRow(Icons.Rounded.Download, stringResource(R.string.download), null, onDownload)
                    OverflowAction.AddNote -> {
                        HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = PaneBorder, thickness = 1.dp)
                        Text(
                            text = stringResource(R.string.advanced),
                            modifier = Modifier.padding(start = 22.dp, top = 16.dp, bottom = 4.dp),
                            color = PaneMuted,
                            style = TextStyle(fontFamily = Inter, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.14.em),
                        )
                        SheetRow(Icons.Rounded.Edit, stringResource(R.string.add_note), stringResource(R.string.note_private), onAddNote, quiet = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    hint: String?,
    onClick: () -> Unit,
    quiet: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = PaneMuted, modifier = Modifier.size(if (quiet) 18.dp else 20.dp))
        Text(
            text = label,
            modifier = Modifier.padding(start = 16.dp).weight(1f),
            color = if (quiet) PaneMuted else PaneInk,
            style = TextStyle(
                fontFamily = Inter,
                fontSize = if (quiet) 14.5.sp else 16.sp,
                fontWeight = if (quiet) FontWeight.Normal else FontWeight.Medium,
            ),
        )
        if (hint != null) {
            Text(hint, color = PaneMuted, style = TextStyle(fontFamily = Inter, fontSize = 13.sp))
        }
    }
}

@Composable
internal fun NoteEditorDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(PaneTile).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.add_note),
                color = PaneInk,
                style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Medium),
            )
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                textStyle = TextStyle(fontFamily = Inter, fontSize = 15.sp, color = PaneInk),
                cursorBrush = SolidColor(PaneInk),
                modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp).clip(RoundedCornerShape(12.dp)).background(PaneGround).padding(12.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    text = stringResource(R.string.save_note),
                    modifier = Modifier.clickable(role = Role.Button) { onSave(draft) }.padding(8.dp),
                    color = PaneInk,
                    style = TextStyle(fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

@Composable
internal fun rememberOpenAffordance(url: String): OpenAffordance {
    val context = LocalContext.current
    return remember(url) { openAffordance(url) { packageInstalled(context, it) } }
}

@Composable
internal fun OpenRecovery(
    url: String,
    reason: OpenFailureKind?,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val recoveryReason = when (reason) {
        OpenFailureKind.Offline -> RecoveryReason.Offline
        OpenFailureKind.Network -> RecoveryReason.Timeout
        null -> RecoveryReason.Unloadable
        else -> RecoveryReason.Other
    }
    val presentation = remember(url, recoveryReason) {
        recoveryPresentation(url, recoveryReason) { packageName ->
            packageInstalled(context, packageName)
        }
    }
    val appName = presentation.appNameRes?.let { stringResource(it) }.orEmpty()
    val privateGroup = reason == OpenFailureKind.PrivateGroup
    val headline = if (privateGroup) {
        stringResource(R.string.private_group_title)
    } else when (presentation.headline) {
        RecoveryHeadline.NotPublic -> if (presentation.appNameRes != null) {
            stringResource(R.string.link_isnt_public_post, appName)
        } else {
            stringResource(R.string.cant_show_link_here)
        }
        RecoveryHeadline.CantShow -> stringResource(R.string.cant_show_link_here)
        RecoveryHeadline.Offline -> stringResource(R.string.youre_offline)
        RecoveryHeadline.CouldntLoad -> stringResource(R.string.couldnt_load_post)
    }
    val body = if (privateGroup) {
        stringResource(R.string.private_group_body, appName.ifBlank { stringResource(R.string.source_facebook) })
    } else when (presentation.body) {
        RecoveryBody.NamedApp -> stringResource(R.string.open_in_app_body, appName)
        RecoveryBody.Browser -> stringResource(R.string.open_it_in_browser_instead)
        RecoveryBody.CheckConnection -> stringResource(R.string.check_connection)
    }
    val openLabel = if (presentation.opensInApp && presentation.appNameRes != null) {
        stringResource(R.string.open_in_named_app, appName)
    } else {
        stringResource(R.string.open_in_browser)
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = headline,
            modifier = Modifier.fillMaxWidth(),
            color = PaneInk,
            textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = PaneDisplay, fontSize = 26.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.025).em),
        )
        Text(
            text = body,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            color = PaneMuted,
            textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = Inter, fontSize = 15.sp, lineHeight = 22.sp),
        )
        if (presentation.retryPrimary && !privateGroup) {
            RecoveryButton(
                label = stringResource(R.string.try_again),
                filled = true,
                mark = null,
                globe = false,
                onClick = onRetry,
                modifier = Modifier.padding(top = 22.dp),
            )
            RecoveryButton(
                label = openLabel,
                filled = false,
                mark = presentation.markRes,
                globe = !presentation.opensInApp,
                onClick = onOpen,
                modifier = Modifier.padding(top = 10.dp),
            )
        } else {
            RecoveryButton(
                label = openLabel,
                filled = true,
                mark = presentation.markRes,
                globe = !presentation.opensInApp,
                onClick = onOpen,
                modifier = Modifier.padding(top = 22.dp),
            )
        }
    }
}

@Composable
private fun RecoveryButton(
    label: String,
    filled: Boolean,
    mark: Int?,
    globe: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val foreground = if (filled) PaneGround else PaneInk
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .then(
                if (filled) Modifier.background(PaneInk) else Modifier.border(1.dp, PaneBorder, shape),
            )
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = label
                this.onClick { onClick(); true }
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        when {
            mark != null -> {
                Icon(painterResource(mark), contentDescription = null, tint = foreground, modifier = Modifier.size(22.dp))
                Box(Modifier.size(10.dp))
            }
            globe -> {
                Icon(Icons.Rounded.Public, contentDescription = null, tint = foreground, modifier = Modifier.size(22.dp))
                Box(Modifier.size(10.dp))
            }
        }
        Text(
            text = label,
            color = foreground,
            style = TextStyle(fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}
