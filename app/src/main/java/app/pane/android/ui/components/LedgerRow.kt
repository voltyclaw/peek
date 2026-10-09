package app.pane.android.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pane.android.R
import app.pane.android.domain.model.HistoryGesture
import app.pane.android.domain.model.HistoryLedger
import app.pane.android.ui.model.LedgerRowUi
import app.pane.android.ui.model.UiImage
import app.pane.android.ui.theme.Inter
import app.pane.android.ui.theme.PaneBorder
import app.pane.android.ui.theme.PaneGround
import app.pane.android.ui.theme.PaneInk
import app.pane.android.ui.theme.PaneMuted
import app.pane.android.ui.theme.PaneTile
import kotlin.math.abs

enum class LedgerMenuStyle { StarFilled, StarOutline, Remove, Share, Copy, Note }

data class LedgerMenuLine(
    val label: String,
    val style: LedgerMenuStyle,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LedgerSwipeRow(
    row: LedgerRowUi,
    onOpen: () -> Unit,
    onSwipe: (left: Boolean) -> Unit,
    onLongPress: () -> Unit,
    onActionStar: () -> Unit,
    onActionRemove: () -> Unit,
    modifier: Modifier = Modifier,
    held: Boolean = false,
    recents: Boolean = false,
    showDivider: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    var armed by remember(row.url) { mutableStateOf(true) }
    var pastCommit by remember(row.url) { mutableStateOf(false) }
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled && armed) {
                armed = false
                onSwipe(value == SwipeToDismissBoxValue.EndToStart)
            }
            false
        },
        positionalThreshold = { distance -> distance * 0.4f },
    )
    val offset = runCatching { state.requireOffset() }.getOrDefault(0f)
    val dragging = abs(offset) > 1f
    LaunchedEffect(dragging) {
        if (!dragging) armed = true
    }
    val direction = state.dismissDirection
    val left = direction == SwipeToDismissBoxValue.EndToStart
    val progress = runCatching { state.progress }.getOrDefault(0f)
    val past = dragging && progress >= 0.4f && state.targetValue != SwipeToDismissBoxValue.Settled
    LaunchedEffect(past) {
        if (past && !pastCommit) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        pastCommit = past
    }
    val kind = if (recents) HistoryLedger.hubGesture(left, row.starred) else HistoryLedger.gesture(left, row.starred)
    val removeFull = lerp(PaneTile, PaneMuted, 0.30f)
    val starFull = lerp(PaneTile, PaneInk, 0.11f)
    val background = when {
        !past -> PaneTile
        left -> removeFull
        else -> starFull
    }
    val starLabel = if (row.starred) stringResource(R.string.remove_star) else stringResource(R.string.star)
    val removeLabel = if (recents) stringResource(R.string.remove_from_recents) else stringResource(R.string.remove_from_history)
    val description = listOf(row.title, row.identity, row.timeLabel)
        .filter { it.isNotBlank() }
        .joinToString(", ")
        .let { base ->
            when {
                row.video -> "$base, video"
                row.thumb != null -> "$base, photo"
                else -> base
            }
        }
    Column(modifier.fillMaxWidth()) {
        SwipeToDismissBox(
            state = state,
            backgroundContent = {
                SwipeBackground(kind = kind, past = past, left = left, color = background)
            },
            enableDismissFromStartToEnd = true,
            enableDismissFromEndToStart = true,
        ) {
            LedgerBody(
                row = row,
                held = held,
                description = description,
                starLabel = starLabel,
                removeLabel = removeLabel,
                onOpen = onOpen,
                onLongPress = onLongPress,
                onActionStar = onActionStar,
                onActionRemove = onActionRemove,
            )
        }
        if (showDivider && !held && !dragging) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 84.dp, end = 20.dp),
                thickness = 1.dp,
                color = PaneBorder,
            )
        }
    }
}

@Composable
private fun SwipeBackground(kind: HistoryGesture, past: Boolean, left: Boolean, color: Color) {
    val label = when (kind) {
        HistoryGesture.Remove -> stringResource(R.string.swipe_remove)
        HistoryGesture.Star -> stringResource(R.string.star)
        HistoryGesture.RemoveStar -> stringResource(R.string.remove_star)
    }
    val filled = when (kind) {
        HistoryGesture.Star -> past
        HistoryGesture.RemoveStar -> !past
        HistoryGesture.Remove -> false
    }
    val iconSize = if (past) 26.dp else 18.dp
    val inset = if (past) 46.dp else 40.dp
    val tint = if (past) PaneInk else PaneMuted
    Box(Modifier.fillMaxSize().background(color)) {
        Row(
            modifier = Modifier
                .align(if (left) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = inset - iconSize / 2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (left && past) SwipeLabel(label)
            if (kind == HistoryGesture.Remove) {
                Icon(Icons.Rounded.Delete, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
            } else {
                Icon(
                    imageVector = if (filled) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(iconSize),
                )
            }
            if (!left && past) SwipeLabel(label)
        }
    }
}

@Composable
private fun SwipeLabel(text: String) {
    Text(
        text = text,
        color = PaneInk,
        style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LedgerBody(
    row: LedgerRowUi,
    held: Boolean,
    description: String,
    starLabel: String,
    removeLabel: String,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onActionStar: () -> Unit,
    onActionRemove: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val wash = lerp(PaneGround, PaneTile, 0.85f)
    val heldShape = RoundedCornerShape(14.dp)
    val ring = when {
        held -> PaneTile
        pressed -> wash
        else -> PaneGround
    }
    val surface = when {
        held -> Modifier
            .padding(horizontal = 8.dp)
            .clip(heldShape)
            .background(PaneTile)
            .border(1.dp, lerp(PaneBorder, PaneInk, 0.18f), heldShape)
        pressed -> Modifier.background(wash)
        else -> Modifier.background(PaneGround)
    }
    Row(
        modifier = surface
            .fillMaxWidth()
            .height(58.dp)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onOpen,
                onLongClick = onLongPress,
            )
            .semantics {
                contentDescription = description
                customActions = listOf(
                    CustomAccessibilityAction(starLabel) { onActionStar(); true },
                    CustomAccessibilityAction(removeLabel) { onActionRemove(); true },
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(42.dp).fillMaxHeight()) {
            if (row.starred) {
                Icon(
                    Icons.Rounded.Star,
                    contentDescription = null,
                    tint = PaneInk,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 20.dp).size(12.dp),
                )
            }
        }
        Poster(row.pfp, row.identity, row.sourceMark, ring)
        Column(
            modifier = Modifier.padding(start = 12.dp).weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = row.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = PaneInk,
                style = TextStyle(fontFamily = Inter, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold),
            )
            val meta = listOf(row.identity, row.timeLabel).filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = PaneMuted,
                    style = TextStyle(fontFamily = Inter, fontSize = 12.sp),
                )
            }
        }
        if (row.thumb != null) {
            Box(Modifier.padding(start = 8.dp).size(42.dp).clip(RoundedCornerShape(8.dp))) {
                PeekImage(row.thumb, contentDescription = null, modifier = Modifier.fillMaxSize())
                if (row.video) {
                    Box(Modifier.align(Alignment.Center).size(16.dp).clip(CircleShape).background(PaneGround.copy(alpha = 0.72f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = PaneInk, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
        Box(Modifier.width(20.dp))
    }
}

@Composable
private fun Poster(pfp: UiImage?, identity: String, sourceMark: Int?, ring: Color) {
    val letter = identity.trim().removePrefix("@").removePrefix("r/").firstOrNull()?.uppercase() ?: "·"
    Box(Modifier.size(30.dp)) {
        if (pfp != null) {
            PeekImage(pfp, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape))
        } else {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(PaneTile), contentAlignment = Alignment.Center) {
                Text(letter, color = PaneMuted, style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Medium))
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(14.dp)
                .clip(CircleShape)
                .background(ring)
                .padding(2.dp),
        ) {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(PaneTile), contentAlignment = Alignment.Center) {
                if (sourceMark != null) {
                    Icon(painterResource(sourceMark), contentDescription = null, tint = PaneMuted, modifier = Modifier.size(8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerMenuSheet(
    row: LedgerRowUi,
    lines: List<LedgerMenuLine>,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PaneTile,
        scrimColor = PaneGround.copy(alpha = 150f / 255f),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(PaneMuted.copy(alpha = 0.35f)),
            )
        },
    ) {
        LedgerBody(
            row = row,
            held = false,
            description = row.title,
            starLabel = "",
            removeLabel = "",
            onOpen = {},
            onLongPress = {},
            onActionStar = {},
            onActionRemove = {},
        )
        HorizontalDivider(color = PaneBorder, thickness = 1.dp)
        lines.forEach { line ->
            if (line.style == LedgerMenuStyle.Note) {
                HorizontalDivider(color = PaneBorder, thickness = 1.dp)
            }
            MenuLine(line)
        }
        Box(Modifier.height(24.dp))
    }
}

@Composable
private fun MenuLine(line: LedgerMenuLine) {
    val quiet = line.style == LedgerMenuStyle.Note
    val tint = if (line.style == LedgerMenuStyle.StarFilled) PaneInk else PaneMuted
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .combinedClickableNoRipple(line.onClick)
            .padding(start = 32.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(menuIcon(line.style), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(
            text = line.label,
            modifier = Modifier.padding(start = 18.dp).weight(1f),
            color = if (quiet) PaneMuted else PaneInk,
            style = TextStyle(
                fontFamily = Inter,
                fontSize = if (quiet) 14.5.sp else 15.5.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        if (quiet) {
            Text(
                text = stringResource(R.string.note_private),
                color = PaneMuted.copy(alpha = 0.3f),
                style = TextStyle(fontFamily = Inter, fontSize = 14.5.sp),
            )
        }
    }
}

private fun menuIcon(style: LedgerMenuStyle): ImageVector = when (style) {
    LedgerMenuStyle.StarFilled -> Icons.Rounded.Star
    LedgerMenuStyle.StarOutline -> Icons.Rounded.StarBorder
    LedgerMenuStyle.Remove -> Icons.Rounded.Delete
    LedgerMenuStyle.Share -> Icons.Rounded.Share
    LedgerMenuStyle.Copy -> Icons.Rounded.ContentCopy
    LedgerMenuStyle.Note -> Icons.Rounded.Sell
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.combinedClickableNoRipple(onClick: () -> Unit): Modifier =
    combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
