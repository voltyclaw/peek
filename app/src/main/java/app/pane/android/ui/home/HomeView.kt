package app.pane.android.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.pane.android.R
import app.pane.android.ui.components.LedgerMenuLine
import app.pane.android.ui.components.LedgerMenuSheet
import app.pane.android.ui.components.LedgerMenuStyle
import app.pane.android.ui.components.LedgerSwipeRow
import app.pane.android.ui.components.PaneLockup
import app.pane.android.ui.components.PaneMark
import app.pane.android.ui.model.HomeUiState
import app.pane.android.ui.model.RecentLinkUiModel
import app.pane.android.ui.model.asLedgerRow
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
    onOpenHistory: () -> Unit = {},
    onSwipeRecent: (String, Boolean) -> Unit = { url, left -> if (left) onRemoveRecent(url) },
    onShareRecent: (String, String) -> Unit = { _, _ -> },
    onCopyRecent: (String) -> Unit = {},
    linkDraft: String = "",
    onLinkDraft: (String) -> Unit = {},
    linkIsValid: Boolean = false,
    showLinkError: Boolean = false,
    onSubmitLink: () -> Unit = {},
    showDeveloperTools: Boolean = false,
    sampleGroups: List<SamplePickerGroup> = emptyList(),
    showSamplesInRecents: Boolean = false,
    onShowSamplesInRecents: (Boolean) -> Unit = {},
    onOpenSample: (String) -> Unit = {},
    sourceRows: List<SourceRowUi> = emptyList(),
    onSourceShown: (String, Boolean) -> Unit = { _, _ -> },
    onWithdrawSource: (String) -> Unit = {},
    onOpenExternal: (String) -> Unit = {},
) {
    var samplePickerOpen by remember { mutableStateOf(false) }
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
                versionLabel,
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
                showDeveloperTools,
                showSamplesInRecents,
                onShowSamplesInRecents,
                onOpenSamplePicker = { samplePickerOpen = true },
                sourceRows = sourceRows,
                onSourceShown = onSourceShown,
                onWithdrawSource = onWithdrawSource,
                onOpenExternal = onOpenExternal,
            )
            Text(
                text = stringResource(R.string.hub_tagline),
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                color = PaneMuted,
                style = TextStyle(fontFamily = Inter, fontSize = 14.sp),
            )
            PasteField(
                value = linkDraft,
                onValueChange = onLinkDraft,
                valid = linkIsValid,
                showError = showLinkError,
                onSubmit = onSubmitLink,
                onPaste = onPasteClick,
            )
            Spacer(Modifier.height(22.dp))
            when (uiState) {
                HomeUiState.Loading -> LoadingRecents(onOpenHistory)
                HomeUiState.Empty -> EmptyRecents(onOpenHistory)
                is HomeUiState.Content -> RecentLinks(
                    links = uiState.recentLinks,
                    onRecentLink = onRecentLink,
                    onOpenHistory = onOpenHistory,
                    onSwipeRecent = onSwipeRecent,
                    onShareRecent = onShareRecent,
                    onCopyRecent = onCopyRecent,
                )
            }
        }
        if (samplePickerOpen) {
            SamplePickerDialog(
                groups = sampleGroups,
                onDismiss = { samplePickerOpen = false },
                onOpen = { url ->
                    samplePickerOpen = false
                    onOpenSample(url)
                },
            )
        }
    }
}

@Composable
private fun HomeHeader(
    onOpenLinkSettings: () -> Unit,
    versionLabel: String,
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
    showDeveloperTools: Boolean,
    showSamplesInRecents: Boolean,
    onShowSamplesInRecents: (Boolean) -> Unit,
    onOpenSamplePicker: () -> Unit,
    sourceRows: List<SourceRowUi>,
    onSourceShown: (String, Boolean) -> Unit,
    onWithdrawSource: (String) -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val optionsDescription = stringResource(R.string.more_options)
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
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
                Icon(Icons.Rounded.Settings, contentDescription = null, tint = PaneMuted, modifier = Modifier.size(20.dp))
            }
            OptionsMenu(
                expanded = menuOpen,
                versionLabel = versionLabel,
                onDismiss = { menuOpen = false },
                onOpenLinkSettings = {
                    menuOpen = false
                    onOpenLinkSettings()
                },
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
                showDeveloperTools = showDeveloperTools,
                showSamplesInRecents = showSamplesInRecents,
                onShowSamplesInRecents = onShowSamplesInRecents,
                onOpenSamplePicker = {
                    menuOpen = false
                    onOpenSamplePicker()
                },
                sourceRows = sourceRows,
                onSourceShown = onSourceShown,
                onWithdrawSource = onWithdrawSource,
                onOpenExternal = onOpenExternal,
            )
        }
    }
}

@Composable
private fun OptionsMenu(
    expanded: Boolean,
    versionLabel: String,
    onDismiss: () -> Unit,
    onOpenLinkSettings: () -> Unit,
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
    showDeveloperTools: Boolean,
    showSamplesInRecents: Boolean,
    onShowSamplesInRecents: (Boolean) -> Unit,
    onOpenSamplePicker: () -> Unit,
    sourceRows: List<SourceRowUi>,
    onSourceShown: (String, Boolean) -> Unit,
    onWithdrawSource: (String) -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    if (!expanded) return
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
                SettingsHairline()
                SettingsHairline()
                SettingsSection(stringResource(R.string.settings_group_sources))
                SourceSettingsGroup(
                    rows = sourceRows,
                    onShown = onSourceShown,
                    onWithdraw = onWithdrawSource,
                    onOpenExternal = onOpenExternal,
                )
                Text(
                    text = stringResource(R.string.settings_sources_footer),
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    color = PaneMuted,
                    style = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp),
                )
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
                            checkedTrackColor = PaneInk,
                            uncheckedTrackColor = PaneBorder,
                            checkedThumbColor = PaneGround,
                            uncheckedThumbColor = PaneInk,
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
                        .clip(RoundedCornerShape(16.dp))
                        .background(PaneTile)
                        .border(1.dp, PaneBorder, RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button, onClick = onSetDefaultBrowser)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.set_default_browser),
                            color = PaneInk,
                            style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            text = stringResource(R.string.set_default_browser_help),
                            color = PaneMuted,
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
                if (showDeveloperTools) {
                    SettingsSection(stringResource(R.string.section_developer))
                    Text(
                        text = stringResource(R.string.open_sample_post),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable(role = Role.Button, onClick = onOpenSamplePicker)
                            .padding(vertical = 16.dp),
                        color = PaneInk,
                        style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.show_samples_in_recents),
                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                            color = PaneInk,
                            style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                        )
                        Switch(
                            checked = showSamplesInRecents,
                            onCheckedChange = onShowSamplesInRecents,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = PaneInk,
                                uncheckedTrackColor = PaneBorder,
                                checkedThumbColor = PaneGround,
                                uncheckedThumbColor = PaneInk,
                                checkedBorderColor = Color.Transparent,
                                uncheckedBorderColor = Color.Transparent,
                            ),
                        )
                    }
                }
                SettingsHairline()
                Text(
                    text = stringResource(R.string.clear_recent_posts),
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button, onClick = onClearRecents)
                        .padding(vertical = 16.dp),
                    color = PaneInk,
                    style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                )
                if (versionLabel.isNotBlank()) {
                    Text(
                        text = versionLabel,
                        modifier = Modifier.padding(top = 20.dp),
                        color = PaneMuted,
                        style = TextStyle(fontFamily = Inter, fontSize = 13.sp),
                    )
                }
                Text(
                    text = stringResource(R.string.licenses_note),
                    modifier = Modifier.padding(top = 8.dp),
                    color = PaneMuted,
                    style = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp),
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
                    color = PaneInk,
                    style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}

@Composable
private fun SamplePickerDialog(
    groups: List<SamplePickerGroup>,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(Modifier.fillMaxSize().background(PaneGround)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp)
                    .heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.open_sample_post),
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
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            ) {
                groups.forEachIndexed { index, group ->
                    SettingsSection(group.title, first = index == 0)
                    group.samples.forEach { sample ->
                        Text(
                            text = sample.label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clickable(role = Role.Button) { onOpen(sample.url) }
                                .padding(vertical = 14.dp),
                            color = PaneInk,
                            style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                        )
                    }
                }
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
private fun PasteField(
    value: String,
    onValueChange: (String) -> Unit,
    valid: Boolean,
    showError: Boolean,
    onSubmit: () -> Unit,
    onPaste: () -> Unit,
) {
    val focus = androidx.compose.ui.focus.FocusRequester()
    var focused by remember { mutableStateOf(false) }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    BackHandler(enabled = focused) {
        focus.freeFocus()
        keyboard?.hide()
    }
    val border = if (focused) androidx.compose.ui.graphics.lerp(PaneBorder, PaneAccent, 0.55f) else PaneBorder
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PaneTile)
                .border(1.dp, border, RoundedCornerShape(16.dp))
                .padding(start = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Link, contentDescription = null, tint = PaneMuted, modifier = Modifier.size(18.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp).focusRequester(focus).onFocusChanged { focused = it.isFocused },
                textStyle = TextStyle(fontFamily = Inter, fontSize = 15.sp, color = PaneInk),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(PaneInk),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(stringResource(R.string.paste_placeholder), color = PaneMuted, style = TextStyle(fontFamily = Inter, fontSize = 15.sp))
                        }
                        inner()
                    }
                },
            )
            if (valid) {
                val chip = androidx.compose.ui.graphics.lerp(PaneTile, PaneAccent, 0.17f)
                Text(
                    text = stringResource(R.string.open_chip),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(chip).clickable(role = Role.Button, onClick = onSubmit).padding(horizontal = 12.dp, vertical = 8.dp),
                    color = PaneAccent,
                    style = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                )
            } else {
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onPaste).padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = null, tint = PaneMuted, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.paste_chip), color = PaneMuted, style = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Medium))
                }
            }
        }
        if (focused) {
            Text(
                text = stringResource(R.string.paste_focus_helper),
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                color = PaneMuted,
                style = TextStyle(fontFamily = Inter, fontSize = 12.sp),
            )
        }
        if (showError) {
            Text(
                text = stringResource(R.string.link_invalid),
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                color = PaneMuted,
                style = TextStyle(fontFamily = Inter, fontSize = 13.sp),
            )
        }
    }
}

@Composable
private fun RecentLinks(
    links: List<RecentLinkUiModel>,
    onRecentLink: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onSwipeRecent: (String, Boolean) -> Unit,
    onShareRecent: (String, String) -> Unit,
    onCopyRecent: (String) -> Unit,
) {
    var menu by remember { mutableStateOf<RecentLinkUiModel?>(null) }
    Column(modifier = Modifier.fillMaxWidth()) {
        RecentHeader(count = links.size, onOpenHistory = onOpenHistory)
        links.forEach { link ->
            LedgerSwipeRow(
                row = link.asLedgerRow(),
                onOpen = { onRecentLink(link.url) },
                onSwipe = { left -> onSwipeRecent(link.url, left) },
                onLongPress = { menu = link },
                onActionStar = { onSwipeRecent(link.url, false) },
                onActionRemove = { onSwipeRecent(link.url, true) },
                recents = true,
            )
        }
        Box(Modifier.padding(top = 18.dp).width(48.dp).height(1.dp).background(PaneBorder))
        Text(
            text = stringResource(R.string.thats_everything),
            modifier = Modifier.padding(top = 16.dp),
            color = PaneMuted,
            style = TextStyle(fontFamily = Inter, fontSize = 13.sp),
        )
    }
    menu?.let { link ->
        val row = link.asLedgerRow()
        LedgerMenuSheet(
            row = row,
            lines = listOf(
                LedgerMenuLine(
                    label = if (row.starred) stringResource(R.string.remove_star) else stringResource(R.string.star),
                    style = if (row.starred) LedgerMenuStyle.StarFilled else LedgerMenuStyle.StarOutline,
                    onClick = { menu = null; onSwipeRecent(link.url, false) },
                ),
                LedgerMenuLine(
                    label = stringResource(R.string.remove_from_recents),
                    style = LedgerMenuStyle.Remove,
                    onClick = { menu = null; onSwipeRecent(link.url, true) },
                ),
                LedgerMenuLine(
                    label = stringResource(R.string.share),
                    style = LedgerMenuStyle.Share,
                    onClick = { menu = null; onShareRecent(link.url, link.title) },
                ),
                LedgerMenuLine(
                    label = stringResource(R.string.copy_link),
                    style = LedgerMenuStyle.Copy,
                    onClick = { menu = null; onCopyRecent(link.url) },
                ),
            ),
            onDismiss = { menu = null },
        )
    }
}

@Composable
private fun RecentHeader(count: Int?, onOpenHistory: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val countLabel = if (count == null) {
            stringResource(R.string.recent_section)
        } else {
            stringResource(R.string.recent_section) + " · " + stringResource(R.string.recent_of, count, 8)
        }
        Text(countLabel, color = PaneMuted, style = TextStyle(fontFamily = Inter, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.14.em))
        Text(
            text = stringResource(R.string.history_door),
            modifier = Modifier.clickable(role = Role.Button, onClick = onOpenHistory),
            color = PaneInk.copy(alpha = 0.75f),
            style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun LoadingRecents(onOpenHistory: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        RecentHeader(count = null, onOpenHistory = onOpenHistory)
        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PaneMuted, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun EmptyRecents(onOpenHistory: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        RecentHeader(count = 0, onOpenHistory = onOpenHistory)
        PaneMark(Modifier.size(96.dp))
        Text(
            text = stringResource(R.string.nothing_recent_yet),
            modifier = Modifier.padding(top = 18.dp),
            color = PaneInk,
            style = TextStyle(fontFamily = Inter, fontSize = 18.sp, fontWeight = FontWeight.Medium),
        )
        Text(
            text = stringResource(R.string.nothing_recent_hint),
            modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp),
            color = PaneMuted,
            style = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp),
        )
        Box(Modifier.padding(top = 28.dp).width(48.dp).height(1.dp).background(PaneBorder))
        Text(
            text = stringResource(R.string.thats_all_for_now),
            modifier = Modifier.padding(top = 16.dp),
            color = PaneMuted,
            style = TextStyle(fontFamily = Inter, fontSize = 13.sp),
        )
    }
}
