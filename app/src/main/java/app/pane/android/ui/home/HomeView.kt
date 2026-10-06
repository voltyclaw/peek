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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import app.pane.android.ui.media.VideoQuality
import app.pane.android.ui.navigation.BackBehavior
import app.pane.android.ui.navigation.SoundMode
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
    videoQuality: VideoQuality = VideoQuality.Auto,
    onVideoQuality: (VideoQuality) -> Unit = {},
    soundMode: SoundMode = SoundMode.Muted,
    onSoundMode: (SoundMode) -> Unit = {},
    browserTrampoline: Boolean = false,
    onBrowserTrampoline: (Boolean) -> Unit = {},
    handoffBrowsers: List<HandoffBrowserOption> = emptyList(),
    handoffPackage: String = "",
    onHandoffBrowser: (String) -> Unit = {},
    onSetDefaultBrowser: () -> Unit = {},
    showFirstLaunchHint: Boolean = false,
    onDismissFirstLaunchHint: () -> Unit = {},
    onRemoveRecent: (String) -> Unit = {},
    onClearRecents: () -> Unit = {},
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
            HomeHeader(
                onOpenLinkSettings,
                themeMode,
                onThemeMode,
                backBehavior,
                onBackBehavior,
                videoQuality,
                onVideoQuality,
                soundMode,
                onSoundMode,
                browserTrampoline,
                onBrowserTrampoline,
                handoffBrowsers,
                handoffPackage,
                onHandoffBrowser,
                onSetDefaultBrowser,
                onClearRecents,
            )
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
                is HomeUiState.Content -> RecentLinks(uiState.recentLinks, onRecentLink, onRemoveRecent)
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
    videoQuality: VideoQuality,
    onVideoQuality: (VideoQuality) -> Unit,
    soundMode: SoundMode,
    onSoundMode: (SoundMode) -> Unit,
    browserTrampoline: Boolean,
    onBrowserTrampoline: (Boolean) -> Unit,
    handoffBrowsers: List<HandoffBrowserOption>,
    handoffPackage: String,
    onHandoffBrowser: (String) -> Unit,
    onSetDefaultBrowser: () -> Unit,
    onClearRecents: () -> Unit,
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
                onThemeMode = onThemeMode,
                onBackBehavior = onBackBehavior,
                videoQuality = videoQuality,
                onVideoQuality = onVideoQuality,
                soundMode = soundMode,
                onSoundMode = onSoundMode,
                browserTrampoline = browserTrampoline,
                onBrowserTrampoline = onBrowserTrampoline,
                handoffBrowsers = handoffBrowsers,
                handoffPackage = handoffPackage,
                onHandoffBrowser = onHandoffBrowser,
                onSetDefaultBrowser = onSetDefaultBrowser,
                onClearRecents = onClearRecents,
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
    videoQuality: VideoQuality,
    onVideoQuality: (VideoQuality) -> Unit,
    soundMode: SoundMode,
    onSoundMode: (SoundMode) -> Unit,
    browserTrampoline: Boolean,
    onBrowserTrampoline: (Boolean) -> Unit,
    handoffBrowsers: List<HandoffBrowserOption>,
    handoffPackage: String,
    onHandoffBrowser: (String) -> Unit,
    onSetDefaultBrowser: () -> Unit,
    onClearRecents: () -> Unit,
) {
    if (!expanded) return
    val night = app.pane.android.ui.theme.LocalPaneColors.current.night
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PaneGround),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp)
                    .heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings),
                    modifier = Modifier.weight(1f),
                    color = PaneInk,
                    style = TextStyle(fontFamily = Inter, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(role = Role.Button, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cancel), tint = PaneMuted)
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            ) {
                SettingsSection(stringResource(R.string.section_playback), first = true)
                Text(
                    text = stringResource(R.string.mute_videos),
                    color = PaneInk,
                    style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                )
                Text(
                    text = stringResource(R.string.mute_videos_help),
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                    color = PaneMuted,
                    style = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp),
                )
                SoundMode.entries.forEach { mode ->
                    val label = when (mode) {
                        SoundMode.Muted -> R.string.sound_muted
                        SoundMode.On -> R.string.sound_on
                        SoundMode.RememberLast -> R.string.sound_remember
                    }
                    SettingsChoice(
                        title = stringResource(label),
                        selected = soundMode == mode,
                        onClick = { onSoundMode(mode) },
                    )
                }
                SettingsHairline()
                Text(
                    text = stringResource(R.string.video_quality),
                    modifier = Modifier.padding(top = 12.dp),
                    color = PaneInk,
                    style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                )
                Text(
                    text = stringResource(R.string.video_quality_help),
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                    color = PaneMuted,
                    style = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp),
                )
                listOf(VideoQuality.Auto, VideoQuality.High, VideoQuality.Medium, VideoQuality.Low).forEach { quality ->
                    val label = when (quality) {
                        VideoQuality.Auto -> R.string.quality_auto
                        VideoQuality.High -> R.string.quality_high
                        VideoQuality.Medium -> R.string.quality_medium
                        VideoQuality.Low -> R.string.quality_data_saver
                    }
                    SettingsChoice(
                        title = stringResource(label),
                        selected = videoQuality == quality,
                        onClick = { onVideoQuality(quality) },
                    )
                }
                SettingsSection(stringResource(R.string.section_opening_links))
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.browser_trampoline),
                            color = PaneInk,
                            style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                        )
                        Text(
                            text = stringResource(R.string.browser_trampoline_help),
                            color = PaneMuted,
                            style = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp),
                        )
                    }
                    Switch(
                        checked = browserTrampoline,
                        onCheckedChange = onBrowserTrampoline,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Color(0xFF3A8F6A),
                            uncheckedTrackColor = if (night) Color(0xFF2A3530) else Color(0xFFC9D5CC),
                            checkedThumbColor = Color.White,
                            uncheckedThumbColor = Color.White,
                            checkedBorderColor = Color.Transparent,
                            uncheckedBorderColor = Color.Transparent,
                        ),
                    )
                }
                Text(
                    text = stringResource(R.string.opening_links_helper),
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    color = PaneMuted,
                    style = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PaneFill)
                        .clickable(role = Role.Button, onClick = onSetDefaultBrowser)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.set_default_browser),
                            color = PaneOnFill,
                            style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            text = stringResource(R.string.set_default_browser_help),
                            color = PaneOnFill.copy(alpha = 0.82f),
                            style = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.handoff_browser),
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    color = PaneInk,
                    style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                )
                if (handoffBrowsers.isEmpty()) {
                    Text(
                        text = stringResource(R.string.handoff_browser_none),
                        modifier = Modifier.padding(bottom = 8.dp),
                        color = PaneMuted,
                        style = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp),
                    )
                } else {
                    handoffBrowsers.forEach { browser ->
                        SettingsChoice(
                            title = browser.label,
                            selected = browser.packageName == handoffPackage,
                            onClick = { onHandoffBrowser(browser.packageName) },
                        )
                    }
                }
                SettingsHairline()
                Text(
                    text = stringResource(R.string.back_behavior),
                    modifier = Modifier.padding(top = 12.dp),
                    color = PaneInk,
                    style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                )
                BackBehavior.entries.forEach { behavior ->
                    val label = when (behavior) {
                        BackBehavior.ClosePeek -> R.string.back_closes_pane
                        BackBehavior.GoHome -> R.string.back_goes_home
                    }
                    val help = when (behavior) {
                        BackBehavior.ClosePeek -> R.string.back_closes_pane_help
                        BackBehavior.GoHome -> R.string.back_goes_home_help
                    }
                    SettingsChoice(
                        title = stringResource(label),
                        subtitle = stringResource(help),
                        selected = backBehavior == behavior,
                        onClick = { onBackBehavior(behavior) },
                    )
                }
                SettingsSection(stringResource(R.string.appearance))
                ThemeMode.entries.forEach { mode ->
                    val label = when (mode) {
                        ThemeMode.Light -> R.string.theme_light
                        ThemeMode.Dark -> R.string.theme_dark
                        ThemeMode.System -> R.string.theme_system
                    }
                    SettingsChoice(
                        title = stringResource(label),
                        selected = themeMode == mode,
                        onClick = { onThemeMode(mode) },
                    )
                }
                Text(
                    text = stringResource(R.string.clear_recent_posts),
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button, onClick = onClearRecents)
                        .padding(vertical = 16.dp),
                    color = PaneAccent,
                    style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                )
                Text(
                    text = stringResource(R.string.settings_tagline),
                    modifier = Modifier.padding(top = 20.dp),
                    color = PaneInk,
                    style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                )
                Text(
                    text = stringResource(R.string.settings_supported_sources),
                    modifier = Modifier.padding(top = 4.dp),
                    color = PaneMuted,
                    style = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp),
                )
                Text(
                    text = stringResource(R.string.open_link_settings),
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button, onClick = onOpenLinkSettings)
                        .padding(vertical = 16.dp),
                    color = PaneAccent,
                    style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, first: Boolean = false) {
    Text(
        text = title,
        modifier = Modifier.padding(top = if (first) 0.dp else 28.dp, bottom = 8.dp),
        color = PaneMuted,
        style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
private fun SettingsHairline() {
    Box(
        Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(PaneBorder),
    )
}

@Composable
private fun SettingsChoice(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                color = PaneInk,
                style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Medium),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = PaneMuted,
                    style = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp),
                )
            }
        }
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = PaneAccent, modifier = Modifier.size(18.dp))
        }
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
    val pasteDescription = stringResource(R.string.paste_from_clipboard)
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
                .semantics { contentDescription = pasteDescription }
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
private fun RecentLinks(
    links: List<RecentLinkUiModel>,
    onRecentLink: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.recent_links), color = PaneInk, style = TextStyle(fontFamily = Geist, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp))
            Text(stringResource(R.string.recent_count, links.size), color = PaneMuted, style = TextStyle(fontFamily = GeistMono, fontSize = 8.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp))
        }
        links.forEach { link -> RecentLinkRow(link, onRecentLink, onRemoveRecent) }
    }
}

@Composable
private fun RecentLinkRow(
    link: RecentLinkUiModel,
    onRecentLink: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val openLinkDescription = stringResource(R.string.open_link, link.title)
    val removeDescription = stringResource(R.string.remove_recent)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
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
        Box {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) { menuOpen = true }
                    .semantics { contentDescription = removeDescription },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.MoreVert, contentDescription = null, tint = PaneMuted, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.remove_recent)) },
                    onClick = {
                        menuOpen = false
                        onRemoveRecent(link.url)
                    },
                )
            }
        }
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
