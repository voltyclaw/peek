package app.pane.android.ui.history

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pane.android.R
import app.pane.android.domain.model.HistoryEmptyKind
import app.pane.android.domain.model.HistoryRowAction
import app.pane.android.domain.model.HistoryRowActions
import app.pane.android.domain.model.SourceApp
import app.pane.android.domain.tiktok.TikTokVisibleRow
import app.pane.android.ui.actions.sharePostIntent
import app.pane.android.ui.components.LedgerMenuLine
import app.pane.android.ui.components.LedgerMenuSheet
import app.pane.android.ui.components.LedgerMenuStyle
import app.pane.android.ui.components.LedgerSwipeRow
import app.pane.android.ui.components.PaneSnackbarHost
import app.pane.android.ui.components.showForFiveSeconds
import app.pane.android.ui.model.LedgerRowUi
import app.pane.android.ui.theme.Inter
import app.pane.android.ui.theme.PaneBorder
import app.pane.android.ui.theme.PaneDisplay
import app.pane.android.ui.theme.PaneGround
import app.pane.android.ui.theme.PaneInk
import app.pane.android.ui.theme.PaneMuted
import kotlinx.coroutines.launch

@Composable
fun HistoryRoute(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val starredMessage = stringResource(R.string.starred_snack)
    val unstarredMessage = stringResource(R.string.unstarred_snack)
    val removedMessage = stringResource(R.string.history_removed_snack)
    val undo = stringResource(R.string.undo)
    var menu by remember { mutableStateOf<LedgerRowUi?>(null) }
    var appMenu by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.notice.collect { notice ->
            when (notice) {
                HistoryNotice.Starred -> snackbar.showForFiveSeconds(starredMessage)
                is HistoryNotice.Unstarred -> snackbar.showForFiveSeconds(unstarredMessage, undo) {
                    viewModel.undo(notice.undo)
                }
                is HistoryNotice.Removed -> snackbar.showForFiveSeconds(removedMessage, undo) {
                    viewModel.undo(notice.undo)
                }
            }
        }
    }

    fun share(row: LedgerRowUi) {
        context.startActivity(Intent.createChooser(sharePostIntent(row.url, row.title), null))
    }

    fun copy(url: String) {
        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Link", url))) }
    }

    Box(modifier.fillMaxSize().background(PaneGround)) {
        HistoryView(
            ui = ui,
            appMenu = appMenu,
            onBack = onBack,
            onOpen = onOpen,
            onShowAll = viewModel::showAll,
            onShowStarred = viewModel::showStarred,
            onToggleAppMenu = { appMenu = !appMenu },
            onDismissAppMenu = { appMenu = false },
            onToggleApp = viewModel::toggleApp,
            onClearApps = viewModel::clearApps,
            onSwipe = viewModel::swipe,
            onRemove = { viewModel.menu(it, HistoryRowAction.Remove) },
            onLongPress = { menu = it },
            onVisibleTikTok = viewModel::noteVisibleTikTok,
            modifier = Modifier.fillMaxSize(),
        )
        menu?.let { row ->
            LedgerMenuSheet(
                row = row,
                lines = historyMenuLines(row) { action ->
                    menu = null
                    when (action) {
                        HistoryRowAction.Share -> share(row)
                        HistoryRowAction.CopyLink -> copy(row.url)
                        HistoryRowAction.NoteAndTags -> Unit
                        else -> viewModel.menu(row.url, action)
                    }
                },
                onDismiss = { menu = null },
            )
        }
        PaneSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
    }
}

@Composable
private fun historyMenuLines(row: LedgerRowUi, onAction: (HistoryRowAction) -> Unit): List<LedgerMenuLine> =
    HistoryRowActions.menu(row.starred).map { action ->
        LedgerMenuLine(
            label = when (action) {
                HistoryRowAction.Star -> stringResource(R.string.star)
                HistoryRowAction.RemoveStar -> stringResource(R.string.remove_star)
                HistoryRowAction.Remove -> stringResource(R.string.remove_from_history)
                HistoryRowAction.Share -> stringResource(R.string.share)
                HistoryRowAction.CopyLink -> stringResource(R.string.copy_link)
                HistoryRowAction.NoteAndTags -> stringResource(R.string.note_and_tags)
                HistoryRowAction.Open -> action.label
            },
            style = when (action) {
                HistoryRowAction.Star -> LedgerMenuStyle.StarOutline
                HistoryRowAction.RemoveStar -> LedgerMenuStyle.StarFilled
                HistoryRowAction.Remove -> LedgerMenuStyle.Remove
                HistoryRowAction.Share -> LedgerMenuStyle.Share
                HistoryRowAction.CopyLink -> LedgerMenuStyle.Copy
                HistoryRowAction.NoteAndTags -> LedgerMenuStyle.Note
                HistoryRowAction.Open -> LedgerMenuStyle.Share
            },
            onClick = { onAction(action) },
        )
    }

@Composable
fun HistoryView(
    ui: HistoryListUi,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onShowAll: () -> Unit,
    onShowStarred: () -> Unit,
    onToggleAppMenu: () -> Unit,
    onDismissAppMenu: () -> Unit,
    onToggleApp: (SourceApp) -> Unit,
    onClearApps: () -> Unit,
    onSwipe: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
    onLongPress: (LedgerRowUi) -> Unit,
    modifier: Modifier = Modifier,
    appMenu: Boolean = false,
    onVisibleTikTok: (List<TikTokVisibleRow>) -> Unit = {},
) {
    val visibleTikTok = remember { androidx.compose.runtime.mutableStateMapOf<String, TikTokVisibleRow>() }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    LaunchedEffect(onVisibleTikTok) {
        snapshotFlow { visibleTikTok.values.toList() }.collect { onVisibleTikTok(it) }
    }
    Column(modifier.background(PaneGround)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back), tint = PaneInk, modifier = Modifier.size(20.dp))
            }
            Text(
                text = stringResource(R.string.history),
                color = PaneInk,
                style = TextStyle(fontFamily = PaneDisplay, fontSize = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.025).em),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HistoryChip(stringResource(R.string.history_all), selected = !ui.scopeStarred, onClick = onShowAll)
            HistoryChip(stringResource(R.string.history_starred), selected = ui.scopeStarred, onClick = onShowStarred)
            Box {
                HistoryChip(
                    label = stringResource(R.string.history_app),
                    selected = ui.selectedApps.isNotEmpty(),
                    trailing = true,
                    onClick = onToggleAppMenu,
                )
                DropdownMenu(expanded = appMenu, onDismissRequest = onDismissAppMenu) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.history_all)) }, onClick = { onClearApps(); onDismissAppMenu() })
                    ui.apps.forEach { app ->
                        val selected = app in ui.selectedApps
                        DropdownMenuItem(
                            text = { Text(if (selected) "✓  ${app.name}" else app.name) },
                            onClick = { onToggleApp(app) },
                        )
                    }
                }
            }
        }
        if (ui.empty != HistoryEmptyKind.None) {
            HistoryEmpty(ui.empty, Modifier.weight(1f))
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                ui.sections.forEach { section ->
                    Text(
                        text = section.title,
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp),
                        color = PaneMuted,
                        style = TextStyle(fontFamily = Inter, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.14.em),
                    )
                    section.rows.forEachIndexed { index, row ->
                        if (row.tiktokId.isNotBlank()) {
                            DisposableEffect(row.url) {
                                onDispose { visibleTikTok.remove(row.url) }
                            }
                        }
                        Box(
                            Modifier.onGloballyPositioned { coords ->
                                if (row.tiktokId.isBlank()) return@onGloballyPositioned
                                val top = coords.positionInWindow().y
                                val bottom = top + coords.size.height
                                val screen = with(density) { configuration.screenHeightDp.dp.toPx() }
                                val shown = bottom > 0f && top < screen
                                if (shown) {
                                    visibleTikTok[row.url] = TikTokVisibleRow(row.tiktokId, row.url, row.tiktokThumbUrl)
                                } else {
                                    visibleTikTok.remove(row.url)
                                }
                            },
                        ) {
                            LedgerSwipeRow(
                                row = row,
                                onOpen = { onOpen(row.url) },
                                onSwipe = { left -> onSwipe(row.url, left) },
                                onLongPress = { onLongPress(row) },
                                onActionStar = { onSwipe(row.url, false) },
                                onActionRemove = { onRemove(row.url) },
                                showDivider = index != section.rows.lastIndex,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Search miss copy. The search field itself is P2 and is not on screen.
 * An app-filter miss uses this same block, because a filter is something to clear.
 */
@Composable
fun HistoryEmpty(kind: HistoryEmptyKind, modifier: Modifier = Modifier) {
    val title = when (kind) {
        HistoryEmptyKind.Starred -> stringResource(R.string.starred_empty_title)
        HistoryEmptyKind.Search -> stringResource(R.string.search_empty_title)
        HistoryEmptyKind.History, HistoryEmptyKind.None -> stringResource(R.string.history_empty_title)
    }
    val body = when (kind) {
        HistoryEmptyKind.Starred -> stringResource(R.string.starred_empty_body)
        HistoryEmptyKind.Search -> stringResource(R.string.search_empty_body)
        HistoryEmptyKind.History, HistoryEmptyKind.None -> stringResource(R.string.history_empty_body)
    }
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            color = PaneInk,
            style = TextStyle(fontFamily = PaneDisplay, fontSize = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.025).em),
        )
        Text(
            text = body,
            modifier = Modifier.padding(top = 8.dp),
            color = PaneMuted,
            style = TextStyle(fontFamily = Inter, fontSize = 14.5.sp),
        )
    }
}

@Composable
private fun HistoryChip(label: String, selected: Boolean, onClick: () -> Unit, trailing: Boolean = false) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(PaneInk) else Modifier.border(1.dp, PaneBorder, shape))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = PaneGround, modifier = Modifier.size(14.dp))
        Text(
            text = if (trailing) "$label ▾" else label,
            color = if (selected) PaneGround else PaneInk,
            style = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Medium),
        )
    }
}
