package app.pane.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pane.android.R
import app.pane.android.ui.components.PeekImage
import app.pane.android.ui.components.PaneLockup
import app.pane.android.ui.components.PaneMark
import app.pane.android.ui.model.HomeUiState
import app.pane.android.ui.model.RecentLinkUiModel
import app.pane.android.ui.theme.Geist
import app.pane.android.ui.theme.GeistMono
import app.pane.android.ui.theme.Inter
import app.pane.android.ui.theme.PaneAccent
import app.pane.android.ui.theme.PaneBorder
import app.pane.android.ui.theme.PaneChip
import app.pane.android.ui.theme.PaneFill
import app.pane.android.ui.theme.PaneGround
import app.pane.android.ui.theme.PaneInk
import app.pane.android.ui.theme.PaneMuted
import app.pane.android.ui.theme.PaneOnFill
import app.pane.android.ui.theme.PaneSecondary
import app.pane.android.ui.theme.PaneTile
import app.pane.android.ui.navigation.BackBehavior
import app.pane.android.ui.theme.ThemeMode

@Composable
fun HomeView(
    uiState: HomeUiState,
    onPasteClick: () -> Unit,
    onRecentLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    versionLabel: String = "",
    onOpenLinkSettings: () -> Unit = {},
    themeMode: ThemeMode = ThemeMode.System,
    onThemeMode: (ThemeMode) -> Unit = {},
    backBehavior: BackBehavior = BackBehavior.ClosePeek,
    onBackBehavior: (BackBehavior) -> Unit = {},
    showFirstLaunchHint: Boolean = false,
    onDismissFirstLaunchHint: () -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize().background(PaneGround), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 390.dp)
                .fillMaxWidth()
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            HomeHeader(onOpenLinkSettings, themeMode, onThemeMode, backBehavior, onBackBehavior)
            if (versionLabel.isNotBlank()) {
                Text(
                    text = versionLabel,
                    color = PaneMuted,
                    style = TextStyle(fontFamily = GeistMono, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.empty_home_hint),
                color = PaneSecondary,
                style = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 18.sp),
            )
            Spacer(Modifier.height(20.dp))
            ClipboardEntry(onPasteClick)
            if (showFirstLaunchHint) {
                Spacer(Modifier.height(12.dp))
                FirstLaunchHint(onDismissFirstLaunchHint)
            }
            Spacer(Modifier.height(20.dp))
            when (uiState) {
                HomeUiState.Loading -> LoadingRecents()
                HomeUiState.Empty -> EmptyRecents()
                is HomeUiState.Content -> RecentLinks(uiState.recentLinks, onRecentLink)
            }
        }
    }
}

@Composable
private fun HomeHeader(
    onOpenLinkSettings: () -> Unit,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    backBehavior: BackBehavior,
    onBackBehavior: (BackBehavior) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val optionsDescription = stringResource(R.string.more_options)
    Row(
        modifier = Modifier.fillMaxWidth().height(34.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        PaneLockup()
        Box(contentAlignment = Alignment.CenterEnd) {
            Box(
                modifier = Modifier
                    .requiredSize(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(role = Role.Button) { menuOpen = true }
                    .semantics { contentDescription = optionsDescription },
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Rounded.Tune, contentDescription = null, tint = PaneSecondary, modifier = Modifier.size(20.dp))
            }
            OptionsMenu(
                expanded = menuOpen,
                themeMode = themeMode,
                backBehavior = backBehavior,
                onDismiss = { menuOpen = false },
                onOpenLinkSettings = {
                    menuOpen = false
                    onOpenLinkSettings()
                },
                onThemeMode = { mode ->
                    menuOpen = false
                    onThemeMode(mode)
                },
                onBackBehavior = { behavior ->
                    menuOpen = false
                    onBackBehavior(behavior)
                },
            )
        }
    }
}

@Composable
private fun OptionsMenu(
    expanded: Boolean,
    themeMode: ThemeMode,
    backBehavior: BackBehavior,
    onDismiss: () -> Unit,
    onOpenLinkSettings: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onBackBehavior: (BackBehavior) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = PaneChip,
    ) {
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.open_link_settings),
                    color = PaneInk,
                    style = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                )
            },
            onClick = onOpenLinkSettings,
        )
        HorizontalDivider(color = PaneBorder)
        Text(
            text = stringResource(R.string.back_behavior),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = PaneMuted,
            style = TextStyle(fontFamily = GeistMono, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
        )
        BackBehavior.entries.forEach { behavior ->
            val label = when (behavior) {
                BackBehavior.ClosePeek -> R.string.back_closes_peek
                BackBehavior.GoHome -> R.string.back_goes_home
            }
            val help = when (behavior) {
                BackBehavior.ClosePeek -> R.string.back_closes_peek_help
                BackBehavior.GoHome -> R.string.back_goes_home_help
            }
            DropdownMenuItem(
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(label),
                            color = PaneInk,
                            style = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                        )
                        Text(
                            text = stringResource(help),
                            color = PaneMuted,
                            style = TextStyle(fontFamily = Inter, fontSize = 11.sp, lineHeight = 14.sp),
                        )
                    }
                },
                onClick = { onBackBehavior(behavior) },
                trailingIcon = if (backBehavior == behavior) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, tint = PaneAccent) }
                } else {
                    null
                },
            )
        }
        HorizontalDivider(color = PaneBorder)
        Text(
            text = stringResource(R.string.appearance),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = PaneMuted,
            style = TextStyle(fontFamily = GeistMono, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
        )
        ThemeMode.entries.forEach { mode ->
            val label = when (mode) {
                ThemeMode.Light -> R.string.theme_light
                ThemeMode.Dark -> R.string.theme_dark
                ThemeMode.System -> R.string.theme_system
            }
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(label),
                        color = PaneInk,
                        style = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    )
                },
                onClick = { onThemeMode(mode) },
                trailingIcon = if (themeMode == mode) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, tint = PaneAccent) }
                } else {
                    null
                },
            )
        }
        HorizontalDivider(color = PaneBorder)
        Text(
            text = stringResource(R.string.settings_tagline),
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp),
            color = PaneInk,
            style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
        Text(
            text = stringResource(R.string.settings_supported_sources),
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp).widthIn(max = 240.dp),
            color = PaneMuted,
            style = TextStyle(fontFamily = Inter, fontSize = 11.sp, lineHeight = 14.sp),
        )
    }
}

@Composable
private fun FirstLaunchHint(onDismiss: () -> Unit) {
    val dismissDescription = stringResource(R.string.dismiss_hint)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PaneChip)
            .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.first_launch_hint),
            modifier = Modifier.weight(1f),
            color = PaneSecondary,
            style = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 17.sp),
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable(role = Role.Button, onClick = onDismiss)
                .semantics { contentDescription = dismissDescription },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, contentDescription = null, tint = PaneMuted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ClipboardEntry(onPasteClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(222.dp)) {
        Text(
            text = stringResource(R.string.home_statement),
            modifier = Modifier.width(210.dp).offset(y = 6.dp),
            color = PaneInk,
            style = TextStyle(fontFamily = Geist, fontSize = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp, lineHeight = 38.6.sp),
        )
        Box(
            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = 16.dp).size(width = 100.dp, height = 96.dp).rotate(-5f).clip(RoundedCornerShape(22.dp)).background(PaneBorder),
        )
        Box(
            modifier = Modifier.align(Alignment.TopEnd).offset(y = 4.dp).size(width = 100.dp, height = 96.dp).clip(RoundedCornerShape(22.dp)).background(PaneTile),
            contentAlignment = Alignment.Center,
        ) {
            PaneMark(Modifier.size(38.dp))
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(66.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(PaneFill)
                .clickable(role = Role.Button, onClick = onPasteClick)
                .padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(PaneOnFill.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ContentPaste, contentDescription = null, tint = PaneOnFill, modifier = Modifier.size(19.dp))
            }
            Text(
                text = stringResource(R.string.paste_from_clipboard),
                modifier = Modifier.weight(1f),
                color = PaneOnFill,
                style = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            )
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(50)).background(PaneTile), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = PaneInk, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun RecentLinks(links: List<RecentLinkUiModel>, onRecentLink: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.recent_links), color = PaneInk, style = TextStyle(fontFamily = Geist, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp))
            Text(stringResource(R.string.recent_count, links.size), color = PaneMuted, style = TextStyle(fontFamily = GeistMono, fontSize = 8.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp))
        }
        links.forEach { link -> RecentLinkRow(link, onRecentLink) }
    }
}

@Composable
private fun RecentLinkRow(link: RecentLinkUiModel, onRecentLink: (String) -> Unit) {
    val openLinkDescription = stringResource(R.string.open_link, link.title)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(role = Role.Button) { onRecentLink(link.url) }
            .semantics { contentDescription = openLinkDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (link.thumbnail != null) {
            PeekImage(
                image = link.thumbnail,
                contentDescription = link.thumbnailDescription,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(PaneBorder),
            )
        } else {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(PaneBorder))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(link.title, maxLines = 1, color = PaneInk, style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
            if (link.isCached) {
                Text(link.sourceLabel, color = PaneMuted, style = TextStyle(fontFamily = GeistMono, fontSize = 8.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp))
            } else {
                Text(stringResource(R.string.tap_to_load), color = PaneMuted, style = TextStyle(fontFamily = GeistMono, fontSize = 8.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp))
            }
        }
        Text(link.ageLabel, color = PaneSecondary, style = TextStyle(fontFamily = GeistMono, fontSize = 8.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp))
    }
}

@Composable
private fun LoadingRecents() {
    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = PaneAccent, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun EmptyRecents() {
    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.no_recent_links), color = PaneMuted, fontFamily = Inter, fontSize = 13.sp)
    }
}
