package app.pane.android.ui.history

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.pane.android.R
import app.pane.android.domain.model.HistoryAppCount
import app.pane.android.domain.model.SourceApp
import app.pane.android.ui.PaneTestTags
import app.pane.android.ui.theme.Inter
import app.pane.android.ui.theme.PaneBorder
import app.pane.android.ui.theme.PaneGround
import app.pane.android.ui.theme.PaneInk
import app.pane.android.ui.theme.PaneMuted
import app.pane.android.ui.theme.PaneTile

@Composable
internal fun HistoryFilterBar(
    allSelected: Boolean,
    starred: Boolean,
    app: SourceApp?,
    onAll: () -> Unit,
    onToggleStarred: () -> Unit,
    onOpenFilters: () -> Unit,
    onClearApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterTextChip(
            label = stringResource(R.string.history_all),
            selected = allSelected,
            tag = PaneTestTags.HistoryFilterAll,
            onClick = onAll,
        )
        FilterTextChip(
            label = stringResource(R.string.history_starred),
            selected = starred,
            tag = PaneTestTags.HistoryFilterStarred,
            leading = { tint ->
                Icon(Icons.Rounded.Star, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            },
            onClick = onToggleStarred,
        )
        FilterTextChip(
            label = if (app == null) {
                stringResource(R.string.history_filters_chip)
            } else {
                stringResource(R.string.history_filters_chip_count, 1)
            },
            selected = app != null,
            tag = PaneTestTags.HistoryFilterFilters,
            leading = { tint ->
                Icon(Icons.Rounded.Tune, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            },
            onClick = onOpenFilters,
        )
        if (app != null) {
            FilterAppChip(app = app, onClear = onClearApp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryFilterSheet(
    counts: List<HistoryAppCount>,
    scopeCount: Int,
    selected: SourceApp?,
    onApply: (SourceApp?) -> Unit,
    onDismiss: () -> Unit,
) {
    var staged by remember(selected) { mutableStateOf(selected) }
    val showCount = if (staged == null) scopeCount else counts.firstOrNull { it.app == staged }?.count ?: 0
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PaneTile,
        scrimColor = PaneGround.copy(alpha = 150f / 255f),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        modifier = Modifier.testTag(PaneTestTags.HistoryFilterSheet),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.history_filters_sheet_title),
                    modifier = Modifier.weight(1f),
                    color = PaneInk,
                    style = TextStyle(fontFamily = Inter, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = stringResource(R.string.history_filters_clear),
                    modifier = Modifier
                        .testTag(PaneTestTags.HistoryFilterClear)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClick = { staged = null })
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    color = PaneInk,
                    style = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                )
            }
            Text(
                text = stringResource(R.string.history_filters_section_apps),
                modifier = Modifier.padding(top = 18.dp, bottom = 10.dp),
                color = PaneMuted,
                style = TextStyle(fontFamily = Inter, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.14.em),
            )
            counts.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { tile ->
                        FilterAppTile(
                            tile = tile,
                            picked = staged == tile.app,
                            onClick = {
                                if (tile.count == 0) return@FilterAppTile
                                staged = if (staged == tile.app) null else tile.app
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
            val showLabel = pluralStringResource(R.plurals.history_filters_show_posts, showCount, showCount)
            Box(
                modifier = Modifier
                    .padding(top = 22.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag(PaneTestTags.HistoryFilterShow)
                    .clip(RoundedCornerShape(24.dp))
                    .background(PaneInk)
                    .clickable(role = Role.Button, onClick = { onApply(staged) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = showLabel,
                    color = PaneGround,
                    style = TextStyle(fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

@Composable
private fun FilterTextChip(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
    leading: @Composable ((Color) -> Unit)? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    val tint = if (selected) PaneGround else PaneInk
    Row(
        modifier = Modifier
            .height(36.dp)
            .testTag(tag)
            .semantics { this.selected = selected }
            .clip(shape)
            .then(if (selected) Modifier.background(PaneInk) else Modifier.border(1.dp, PaneBorder, shape))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leading?.invoke(tint)
        Text(
            text = label,
            color = tint,
            style = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun FilterAppChip(app: SourceApp, onClear: () -> Unit) {
    val name = sourceLabel(app)
    val description = stringResource(R.string.a11y_filter_chip_remove, name)
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .width(60.dp)
            .height(36.dp)
            .testTag(PaneTestTags.HistoryFilterAppChip)
            .semantics { contentDescription = description }
            .clip(shape)
            .background(PaneInk)
            .clickable(role = Role.Button, onClick = onClear),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        FilterMark(app = app, tint = PaneGround, modifier = Modifier.size(18.dp))
        Icon(
            Icons.Rounded.Close,
            contentDescription = null,
            tint = PaneGround,
            modifier = Modifier.padding(start = 8.dp).size(10.dp),
        )
    }
}

@Composable
private fun FilterAppTile(
    tile: HistoryAppCount,
    picked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = tile.count > 0
    val name = sourceLabel(tile.app)
    val description = if (enabled) name else stringResource(R.string.a11y_filter_app_none, name)
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .padding(bottom = 8.dp)
            .height(72.dp)
            .testTag(PaneTestTags.historyFilterTile(tile.app.name))
            .semantics {
                contentDescription = description
                selected = picked
            }
            .alpha(if (enabled) 1f else 0.38f)
            .clip(shape)
            .then(
                if (picked) Modifier.background(PaneInk) else Modifier.border(1.dp, PaneBorder, shape),
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FilterMark(app = tile.app, tint = if (picked) PaneGround else PaneInk, modifier = Modifier.size(18.dp))
        Text(
            text = name,
            modifier = Modifier.padding(top = 4.dp),
            color = if (picked) PaneGround else PaneInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = Inter, fontSize = 10.sp, fontWeight = FontWeight.Medium),
        )
        Text(
            text = tile.count.toString(),
            color = if (picked) PaneGround else PaneMuted,
            style = TextStyle(fontFamily = Inter, fontSize = 10.sp),
        )
    }
}

@Composable
private fun FilterMark(app: SourceApp, tint: Color, modifier: Modifier = Modifier) {
    val glyph = filterGlyph(app)
    if (glyph != null) {
        Icon(painterResource(glyph), contentDescription = null, tint = tint, modifier = modifier)
    } else {
        Box(modifier.clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Text(
                text = filterInitial(app),
                color = tint,
                style = TextStyle(fontFamily = Inter, fontSize = 11.sp, fontWeight = FontWeight(650)),
            )
        }
    }
}

@Composable
private fun sourceLabel(app: SourceApp): String = stringResource(
    when (app) {
        SourceApp.X -> R.string.source_x
        SourceApp.Reddit -> R.string.source_reddit
        SourceApp.Facebook -> R.string.source_facebook
        SourceApp.Instagram -> R.string.source_instagram
        SourceApp.YouTube -> R.string.source_youtube
        SourceApp.TikTok -> R.string.source_tiktok
        SourceApp.Bluesky -> R.string.source_bluesky
        SourceApp.Threads -> R.string.source_threads
        SourceApp.Other -> R.string.source_other
    },
)

/** Logo chips stay glyphs. Name-only apps use a letter disc. */
@DrawableRes
private fun filterGlyph(app: SourceApp): Int? = when (app) {
    SourceApp.X -> R.drawable.ic_source_x
    SourceApp.Facebook -> R.drawable.ic_source_facebook
    SourceApp.Instagram -> R.drawable.ic_source_instagram
    SourceApp.YouTube -> R.drawable.ic_source_youtube
    else -> null
}

private fun filterInitial(app: SourceApp): String = when (app) {
    SourceApp.Reddit -> "R"
    SourceApp.TikTok -> "T"
    SourceApp.Bluesky -> "B"
    SourceApp.Other -> "O"
    else -> app.name.take(1)
}
