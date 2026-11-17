package com.bsoft.compose.bmusic.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.crash.CrashReporter
import com.bsoft.compose.bmusic.data.serializables.AppSettingsData
import com.bsoft.compose.bmusic.data.tags.ReplayGainMode
import com.bsoft.compose.bmusic.viewmodels.BackupViewModel
import com.bsoft.compose.bmusic.viewmodels.LanguageViewModel
import com.bsoft.compose.bmusic.viewmodels.SettingsViewModel
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.roundToInt
import com.bsoft.compose.bmusic.data.playback.TRANSITION_SECONDS_MAX
import com.bsoft.compose.bmusic.data.playback.TRANSITION_SECONDS_MIN
import com.bsoft.compose.bmusic.data.playback.TransitionMode
import androidx.compose.ui.semantics.heading

/** The accent colours on offer, besides "match wallpaper" on Android 12+. */
private val ACCENT_COLORS = listOf(
    Color(0xFF2196F3), // Blue
    Color(0xFF9C27B0), // Purple
    Color(0xFFFF9800), // Orange
    Color(0xFF4CAF50), // Green
    Color(0xFFE91E63), // Pink
)

/** Settings, grouped into cards: appearance, general, library, playback, backup and about. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") windowSizeClass: WindowSizeClass,
    viewModel: SettingsViewModel = hiltViewModel(),
    languageViewModel: LanguageViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel(),
    back: () -> Unit
) {
    val settingsState by viewModel.settings.collectAsState()
    val settings = settingsState ?: AppSettingsData()
    val selectedLanguage by languageViewModel.selectedTag.collectAsStateWithLifecycle()
    var showLanguages by rememberSaveable { mutableStateOf(false) }
    var showShortcuts by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        topBar = {
            LargeTopAppBar(
                title = { Text(text = stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = back) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.material_symbols__arrow_back_ios_new_rounded),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    // Comfortable line length on tablets and in landscape.
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                SettingsGroup(stringResource(R.string.appearance)) {
                    val dynamic = settings.accentColor == AppSettingsData.ACCENT_DYNAMIC
                    SettingsRow(
                        icon = R.drawable.ms_palette,
                        title = stringResource(R.string.accent_color),
                        subtitle = stringResource(if (dynamic) R.string.accent_dynamic else R.string.accent_custom)
                    )
                    AccentSwatches(
                        selectedColor = Color(settings.accentColor),
                        dynamicSelected = dynamic,
                        onColorSelected = { viewModel.setAccentColor((it.value shr 32).toInt()) },
                        onDynamicSelected = { viewModel.setAccentColor(AppSettingsData.ACCENT_DYNAMIC) }
                    )
                }

                SettingsGroup(stringResource(R.string.general)) {
                    SettingsRow(
                        icon = R.drawable.ms_translate,
                        title = stringResource(R.string.language),
                        subtitle = languageViewModel.supportedLanguages.find { it.first == selectedLanguage }?.second,
                        onClick = { showLanguages = true },
                        trailing = { Chevron() }
                    )
                    GroupDivider()
                    SwitchRow(
                        icon = R.drawable.ms_tab,
                        title = stringResource(R.string.pref_remember_last_tab),
                        subtitle = stringResource(R.string.pref_remember_last_tab_summary),
                        checked = settings.rememberLastTab,
                        onCheckedChange = { viewModel.setRememberLastTab(it) }
                    )
                }

                SettingsGroup(stringResource(R.string.settings_section_library)) {
                    SwitchRow(
                        icon = R.drawable.ms_library_music,
                        title = stringResource(R.string.pref_hide_short_songs),
                        subtitle = stringResource(R.string.pref_hide_short_songs_summary),
                        checked = settings.hideShortSongs,
                        onCheckedChange = { viewModel.setHideShortSongs(it) }
                    )
                }

                SettingsGroup(stringResource(R.string.settings_section_playback)) {
                    val transition = TransitionMode.resolve(settings.transitionMode, settings.soundFade)
                    SettingsRow(
                        icon = R.drawable.ms_swap_horiz,
                        title = stringResource(R.string.pref_transitions),
                        subtitle = stringResource(
                            when (transition) {
                                TransitionMode.OFF -> R.string.pref_transitions_off_summary
                                TransitionMode.FADE -> R.string.pref_transitions_fade_summary
                                TransitionMode.CROSSFADE -> R.string.pref_transitions_crossfade_summary
                            }
                        )
                    )
                    TransitionChoice(selected = transition, onSelected = { viewModel.setTransitionMode(it) })
                    if (transition != TransitionMode.OFF) {
                        TransitionLength(seconds = settings.transitionSeconds, onChange = { viewModel.setTransitionSeconds(it) })
                    }
                    if (transition == TransitionMode.CROSSFADE) {
                        GroupDivider()
                        SwitchRow(
                            icon = R.drawable.ms_album,
                            title = stringResource(R.string.pref_gapless_albums),
                            subtitle = stringResource(R.string.pref_gapless_albums_summary),
                            checked = settings.gaplessAlbums,
                            onCheckedChange = { viewModel.setGaplessAlbums(it) }
                        )
                    }
                    GroupDivider()
                    SwitchRow(
                        icon = R.drawable.ms_vibration,
                        title = stringResource(R.string.pref_shake_to_change),
                        subtitle = stringResource(R.string.pref_shake_to_change_summary),
                        checked = settings.shakeToChange,
                        onCheckedChange = { viewModel.setShakeToChange(it) }
                    )
                    GroupDivider()
                    SettingsRow(
                        icon = R.drawable.ms_graphic_eq,
                        title = stringResource(R.string.pref_replay_gain),
                        subtitle = stringResource(R.string.pref_replay_gain_summary)
                    )
                    ReplayGainChoice(
                        selected = ReplayGainMode.fromName(settings.replayGainMode),
                        onSelected = { viewModel.setReplayGainMode(it) }
                    )
                }

                BackupGroup(backupViewModel)

                SettingsGroup(stringResource(R.string.settings_section_about)) {
                    val context = LocalContext.current
                    val version = remember {
                        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
                    }
                    SettingsRow(
                        icon = R.drawable.ms_info,
                        title = stringResource(R.string.app_name),
                        subtitle = stringResource(R.string.about_version, version)
                    )
                    GroupDivider()
                    SettingsRow(
                        icon = R.drawable.ms_keyboard,
                        title = stringResource(R.string.shortcuts_title),
                        subtitle = stringResource(R.string.shortcuts_summary),
                        onClick = { showShortcuts = true },
                        trailing = { Chevron() }
                    )
                    CrashReportRow()
                }
            }
        }
    }

    if (showShortcuts) {
        ShortcutsDialog(onDismiss = { showShortcuts = false })
    }

    if (showLanguages) {
        LanguageDialog(
            languages = languageViewModel.supportedLanguages,
            selected = selectedLanguage,
            onSelect = { languageViewModel.changeLanguage(it); showLanguages = false },
            onDismiss = { showLanguages = false }
        )
    }
}

/**
 * Shown only after BMusic has crashed: share the saved report (the user picks the app and
 * recipient), or dismiss it. Nothing is sent automatically.
 */
@Composable
private fun CrashReportRow() {
    val context = LocalContext.current
    var report by remember { mutableStateOf(CrashReporter.lastReport(context)) }
    val text = report ?: return
    GroupDivider()
    SettingsRow(
        icon = R.drawable.ms_bug_report,
        title = stringResource(R.string.crash_report_title),
        subtitle = stringResource(R.string.crash_report_summary),
        onClick = { context.startActivity(CrashReporter.shareIntent(context, text)) },
        trailing = {
            IconButton(onClick = { CrashReporter.clear(context); report = null }) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ms_close),
                    contentDescription = stringResource(R.string.crash_report_dismiss)
                )
            }
        }
    )
}

// ---- Building blocks --------------------------------------------------------------------

/** A titled card holding related rows. */
@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            modifier = Modifier.padding(start = 16.dp).semantics { heading() },
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) { content() }
        }
    }
}

/** Divider between rows, starting after the icon so the icons read as one column. */
@Composable
private fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    )
}

/** Icon in a tinted rounded square, title, optional summary and trailing control. */
@Composable
private fun SettingsRow(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                modifier = Modifier.size(22.dp),
                imageVector = ImageVector.vectorResource(icon),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                contentDescription = null
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (!subtitle.isNullOrBlank()) {
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

/** A row that is one big switch: tapping anywhere toggles it. */
@Composable
private fun SwitchRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsRow(
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        icon = icon,
        title = title,
        subtitle = subtitle,
        trailing = { Switch(checked = checked, onCheckedChange = null) }
    )
}

@Composable
private fun Chevron() {
    Icon(
        modifier = Modifier.size(22.dp),
        imageVector = ImageVector.vectorResource(R.drawable.ms_chevron_right),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        contentDescription = null
    )
}

/** Colour circles; the chosen one gets a ring and a check mark. */
@Composable
private fun AccentSwatches(
    selectedColor: Color,
    dynamicSelected: Boolean,
    onColorSelected: (Color) -> Unit,
    onDynamicSelected: () -> Unit,
) {
    // Material You (wallpaper) colours exist on Android 12+ only.
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 72.dp, end = 16.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (dynamicAvailable) {
            Swatch(
                fill = Brush.sweepGradient(ACCENT_COLORS + ACCENT_COLORS.first()),
                checkColor = Color.White,
                selected = dynamicSelected,
                label = stringResource(R.string.accent_dynamic),
                onClick = onDynamicSelected
            )
        }
        ACCENT_COLORS.forEach { color ->
            Swatch(
                fill = androidx.compose.ui.graphics.SolidColor(color),
                checkColor = if (color.luminance() > 0.5f) Color.Black else Color.White,
                selected = !dynamicSelected && selectedColor == color,
                label = null,
                onClick = { onColorSelected(color) }
            )
        }
    }
}

@Composable
private fun Swatch(fill: Brush, checkColor: Color, selected: Boolean, label: String?, onClick: () -> Unit) {
    val ring = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .size(40.dp)
            .then(if (selected) Modifier.border(2.dp, ring, CircleShape) else Modifier)
            .padding(if (selected) 4.dp else 0.dp)
            .clip(CircleShape)
            .background(fill)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                modifier = Modifier.size(18.dp),
                imageVector = ImageVector.vectorResource(R.drawable.ms_check),
                tint = checkColor,
                contentDescription = null
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplayGainChoice(selected: ReplayGainMode, onSelected: (ReplayGainMode) -> Unit) {
    val modes = ReplayGainMode.entries
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().padding(start = 72.dp, end = 16.dp, bottom = 14.dp)
    ) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size)
            ) {
                Text(
                    stringResource(
                        when (mode) {
                            ReplayGainMode.OFF -> R.string.replay_gain_off
                            ReplayGainMode.TRACK -> R.string.replay_gain_track
                            ReplayGainMode.ALBUM -> R.string.replay_gain_album
                        }
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

/** The hardware-keyboard shortcuts (see utils/KeyboardShortcuts.kt). */
@Composable
private fun ShortcutsDialog(onDismiss: () -> Unit) {
    val rows = listOf(
        "Space / K" to R.string.shortcut_play_pause,
        "Ctrl + →  ·  Shift + N" to R.string.shortcut_next,
        "Ctrl + ←  ·  Shift + P" to R.string.shortcut_previous,
        "L / J" to R.string.shortcut_seek,
        "Ctrl + F  ·  /" to R.string.shortcut_search,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(imageVector = ImageVector.vectorResource(R.drawable.ms_keyboard), contentDescription = null) },
        title = { Text(stringResource(R.string.shortcuts_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.forEach { (keys, action) ->
                    Row(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = stringResource(action),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                            Text(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                text = keys,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } }
    )
}

@Composable
private fun TransitionChoice(selected: TransitionMode, onSelected: (TransitionMode) -> Unit) {
    val modes = TransitionMode.entries
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().padding(start = 72.dp, end = 16.dp, bottom = 10.dp)
    ) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size)
            ) {
                Text(
                    stringResource(
                        when (mode) {
                            TransitionMode.OFF -> R.string.transition_off
                            TransitionMode.FADE -> R.string.transition_fade
                            TransitionMode.CROSSFADE -> R.string.transition_crossfade
                        }
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

/** The fade or crossfade length, 1 to 12 seconds. Saved when the finger lifts. */
@Composable
private fun TransitionLength(seconds: Int, onChange: (Int) -> Unit) {
    val state = remember {
        SliderState(
            value = seconds.coerceIn(TRANSITION_SECONDS_MIN, TRANSITION_SECONDS_MAX).toFloat(),
            steps = TRANSITION_SECONDS_MAX - TRANSITION_SECONDS_MIN - 1,
            trackRange = TRANSITION_SECONDS_MIN.toFloat()..TRANSITION_SECONDS_MAX.toFloat()
        )
    }
    LaunchedEffect(seconds) {
        if (!state.isDragging) state.value = seconds.coerceIn(TRANSITION_SECONDS_MIN, TRANSITION_SECONDS_MAX).toFloat()
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 72.dp, end = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.pref_transition_length),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            state = state,
            modifier = Modifier.weight(1f),
            onValueChange = { state.value = it },
            onValueChangeFinished = { onChange(state.value.roundToInt()) }
        )
        Text(
            modifier = Modifier.widthIn(min = 36.dp),
            text = stringResource(R.string.transition_seconds, state.value.roundToInt()),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun LanguageDialog(
    languages: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(imageVector = ImageVector.vectorResource(R.drawable.ms_translate), contentDescription = null) },
        title = { Text(stringResource(R.string.language)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                languages.forEach { (tag, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(selected = tag == selected, role = Role.RadioButton) { onSelect(tag) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = tag == selected, onClick = null)
                        Spacer(modifier = Modifier.size(12.dp))
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** Back up to / restore from a JSON file the user picks with the system file picker. */
@Composable
private fun BackupGroup(backupViewModel: BackupViewModel) {
    val state by backupViewModel.state.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { backupViewModel.export(it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { backupViewModel.restore(it) }
    }
    SettingsGroup(stringResource(R.string.backup_title)) {
        SettingsRow(
            icon = R.drawable.ms_cloud_upload,
            title = stringResource(R.string.backup_export),
            subtitle = stringResource(R.string.backup_description),
            onClick = if (state.working) null else ({ exportLauncher.launch(backupViewModel.suggestedFileName()) }),
            trailing = {
                if (state.working) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp) else Chevron()
            }
        )
        GroupDivider()
        SettingsRow(
            icon = R.drawable.ms_settings_backup_restore,
            title = stringResource(R.string.backup_import),
            subtitle = stringResource(R.string.backup_import_summary),
            onClick = if (state.working) null else ({
                importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }),
            trailing = { Chevron() }
        )
    }

    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = { backupViewModel.dismissMessage() },
            icon = { Icon(imageVector = ImageVector.vectorResource(R.drawable.ms_settings_backup_restore), contentDescription = null) },
            title = { Text(stringResource(R.string.backup_title)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { backupViewModel.dismissMessage() }) { Text(stringResource(R.string.dismiss)) } }
        )
    }
}
