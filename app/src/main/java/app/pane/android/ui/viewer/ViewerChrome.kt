package app.pane.android.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.pane.android.R
import app.pane.android.ui.components.PaneMark
import app.pane.android.ui.theme.Inter
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

@Composable
internal fun SourceChip(host: String, overMedia: Boolean, modifier: Modifier = Modifier) {
    val fill = if (overMedia) PaneGround.copy(alpha = 0.6f) else PaneTile
    Text(
        text = host,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(fill)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = PaneMuted,
        style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
internal fun ViewerBottomBar(
    openLabel: String,
    onShare: () -> Unit,
    onOverflow: () -> Unit,
    onOpen: () -> Unit,
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
            Text(
                text = openLabel,
                color = PaneInk,
                style = TextStyle(fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            )
            Box(
                modifier = Modifier
                    .padding(start = 10.dp, end = 8.dp)
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(PaneAccent)
                    .clickable(role = Role.Button, onClick = onOpen),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = openLabel,
                    tint = PaneGround,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
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
    openLabel: String,
    canDownload: Boolean,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onCopyLink: () -> Unit,
    onOpen: () -> Unit,
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
            SheetRow(Icons.Rounded.Share, stringResource(R.string.share), null, onShare)
            SheetRow(Icons.Rounded.ContentCopy, stringResource(R.string.copy_link), null, onCopyLink)
            SheetRow(Icons.AutoMirrored.Rounded.OpenInNew, openLabel, stringResource(R.string.leaves_pane), onOpen)
            if (canDownload) {
                SheetRow(Icons.Rounded.Download, stringResource(R.string.download), null, onDownload)
            }
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
internal fun UnloadableBody(
    host: String,
    author: String,
    url: String,
    hint: String,
    onRetry: (() -> Unit)?,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(width = 160.dp, height = 120.dp).clip(RoundedCornerShape(28.dp)).background(PaneTile),
            contentAlignment = Alignment.Center,
        ) {
            PaneMark(Modifier.size(72.dp))
        }
        Text(
            text = stringResource(R.string.couldnt_load_this),
            modifier = Modifier.padding(top = 22.dp),
            color = PaneInk,
            style = TextStyle(fontFamily = app.pane.android.ui.theme.PaneDisplay, fontSize = 26.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.025).em),
        )
        Text(
            text = hint,
            modifier = Modifier.padding(top = 8.dp),
            color = PaneMuted,
            style = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp),
        )
        if (onRetry != null) Row(
            modifier = Modifier.padding(top = 18.dp).clip(RoundedCornerShape(20.dp)).background(PaneTile).border(1.dp, PaneBorder, RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClick = onRetry).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = PaneMuted, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.retry), color = PaneMuted, style = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Medium))
        }
        Text(
            text = stringResource(R.string.from_label),
            modifier = Modifier.padding(top = 28.dp).align(Alignment.Start),
            color = PaneMuted,
            style = TextStyle(fontFamily = Inter, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.14.em),
        )
        Column(
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(PaneTile).padding(14.dp),
        ) {
            Text(author.ifBlank { host }, color = PaneInk, style = TextStyle(fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            Text(url, color = PaneMuted, maxLines = 1, style = TextStyle(fontFamily = Inter, fontSize = 13.sp))
        }
        Text(
            text = stringResource(R.string.nothing_else_to_load),
            modifier = Modifier.padding(top = 28.dp),
            color = PaneMuted.copy(alpha = 0.7f),
            style = TextStyle(fontFamily = Inter, fontSize = 13.sp),
        )
    }
}
