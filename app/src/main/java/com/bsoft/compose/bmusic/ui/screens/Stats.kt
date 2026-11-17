package com.bsoft.compose.bmusic.ui.screens

import android.content.ContentUris
import android.provider.MediaStore
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.data.entities.ArtistPlays
import com.bsoft.compose.bmusic.data.entities.SongPlays
import com.bsoft.compose.bmusic.data.models.ActivityBucket
import com.bsoft.compose.bmusic.data.models.ListeningActivity
import com.bsoft.compose.bmusic.data.repositories.ListeningStats
import com.bsoft.compose.bmusic.data.repositories.StatsPeriod
import com.bsoft.compose.bmusic.ui.components.ArtworkThumb
import com.bsoft.compose.bmusic.ui.components.EmptyState
import com.bsoft.compose.bmusic.ui.components.InitialAvatar
import com.bsoft.compose.bmusic.viewmodels.StatsViewModel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as DateTextStyle

private fun StatsPeriod.labelRes(): Int = when (this) {
    StatsPeriod.WEEK -> R.string.stats_period_week
    StatsPeriod.MONTH -> R.string.stats_period_month
    StatsPeriod.ALL_TIME -> R.string.stats_period_all
}

/** "2h 05m", or "45 min" under an hour. */
@Composable
private fun durationText(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes < 60) stringResource(R.string.stats_minutes, minutes.toInt())
    else stringResource(R.string.stats_hours_minutes, (minutes / 60).toInt(), (minutes % 60).toInt())
}

/**
 * Listening stats for a period: listening time up front, plays and songs, an activity chart,
 * then the top songs and artists with bars showing how they compare.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel(), back: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stats = state.stats
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
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
            LazyColumn(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(key = "periods") { PeriodSelector(state.period) { viewModel.load(it) } }
                when {
                    state.loading -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                    stats.isEmpty -> item(key = "empty") {
                        EmptyState(
                            icon = R.drawable.ms_bar_chart,
                            title = stringResource(R.string.stats_empty_title),
                            message = stringResource(R.string.stats_empty)
                        )
                    }
                    else -> {
                        item(key = "hero") { ListeningTimeHero(stats) }
                        item(key = "tiles") {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatTile(Modifier.weight(1f), R.drawable.ms_headphones, stringResource(R.string.stats_plays), stats.plays.toString())
                                StatTile(Modifier.weight(1f), R.drawable.ms_music_note, stringResource(R.string.stats_songs), stats.distinctSongs.toString())
                            }
                        }
                        if (stats.activity.isNotEmpty()) item(key = "activity") { ActivityCard(stats.activity) }
                        if (stats.topSongs.isNotEmpty()) item(key = "songs") { TopSongsCard(stats.topSongs) }
                        if (stats.topArtists.isNotEmpty()) item(key = "artists") { TopArtistsCard(stats.topArtists) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSelector(selected: StatsPeriod, onSelect: (StatsPeriod) -> Unit) {
    val periods = StatsPeriod.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        periods.forEachIndexed { index, period ->
            SegmentedButton(
                selected = period == selected,
                onClick = { onSelect(period) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = periods.size)
            ) { Text(stringResource(period.labelRes()), maxLines = 1) }
        }
    }
}

/** The headline number: total listening time, with plays and the daily average under it. */
@Composable
private fun ListeningTimeHero(stats: ListeningStats) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(scheme.primaryContainer, scheme.tertiaryContainer)))
            .padding(horizontal = 24.dp, vertical = 22.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.stats_time),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Text(
                text = durationText(stats.listenedMs),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onPrimaryContainer
            )
            val plays = pluralStringResource(R.plurals.stats_play_count, stats.plays, stats.plays)
            Text(
                text = if (stats.days > 0) stringResource(R.string.stats_hero_caption, plays, durationText(stats.dailyAverageMs)) else plays,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun StatTile(modifier: Modifier, @DrawableRes icon: Int, label: String, value: String) {
    Surface(modifier = modifier, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modifier = Modifier.size(22.dp),
                    imageVector = ImageVector.vectorResource(icon),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    contentDescription = null
                )
            }
            Column {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** A card with a title, used for the chart and both rankings. */
@Composable
private fun StatsCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

/**
 * Plays per day (or per month for all time) as bars. Tap a bar to read its value in the
 * subtitle; otherwise the subtitle names the busiest bar.
 */
@Composable
private fun ActivityCard(buckets: List<ActivityBucket>) {
    val locale = LocalConfiguration.current.locales[0]
    val dayFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val monthFormat = remember(locale) { DateTimeFormatter.ofPattern("LLLL yyyy", locale) }
    var selected by remember(buckets) { mutableIntStateOf(-1) }
    val peak = ListeningActivity.peakIndex(buckets)

    fun dateOf(bucket: ActivityBucket): String =
        if (bucket.monthly) monthFormat.format(bucket.start) else dayFormat.format(bucket.start)

    val focus = if (selected in buckets.indices) selected else peak
    val subtitle = when {
        focus < 0 -> stringResource(R.string.stats_activity_none)
        focus == selected -> stringResource(
            R.string.stats_activity_selected, dateOf(buckets[focus]),
            pluralStringResource(R.plurals.stats_play_count, buckets[focus].plays, buckets[focus].plays)
        )
        else -> stringResource(
            R.string.stats_activity_peak, dateOf(buckets[focus]),
            pluralStringResource(R.plurals.stats_play_count, buckets[focus].plays, buckets[focus].plays)
        )
    }
    val summary = stringResource(R.string.stats_activity) + ". " + subtitle

    StatsCard(title = stringResource(R.string.stats_activity), subtitle = subtitle) {
        ActivityChart(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(horizontal = 16.dp)
                .semantics { contentDescription = summary },
            buckets = buckets,
            highlighted = focus,
            labelOf = { index ->
                val bucket = buckets[index]
                when {
                    bucket.monthly -> bucket.start.month.getDisplayName(DateTextStyle.NARROW, locale)
                    buckets.size <= 7 -> bucket.start.dayOfWeek.getDisplayName(DateTextStyle.SHORT, locale)
                    // A month of days: label every 7th day back from today, so today is labelled.
                    (buckets.lastIndex - index) % 7 == 0 -> bucket.start.dayOfMonth.toString()
                    else -> null
                }
            },
            onSelect = { selected = if (selected == it) -1 else it }
        )
    }
}

@Composable
private fun ActivityChart(
    modifier: Modifier,
    buckets: List<ActivityBucket>,
    highlighted: Int,
    labelOf: (Int) -> String?,
    onSelect: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = scheme.onSurfaceVariant)
    val max = (buckets.maxOfOrNull { it.plays } ?: 0).coerceAtLeast(1)
    // Bars grow in when the chart appears or the period changes.
    val growth = remember(buckets) { Animatable(0f) }
    LaunchedEffect(buckets) { growth.animateTo(1f, tween(durationMillis = 500)) }
    val grow = growth.value
    val labels = remember(buckets) { buckets.indices.map(labelOf) }

    Canvas(
        modifier = modifier.pointerInput(buckets) {
            detectTapGestures { offset ->
                val slot = size.width.toFloat() / buckets.size
                onSelect((offset.x / slot).toInt().coerceIn(0, buckets.lastIndex))
            }
        }
    ) {
        val labelHeight = 20.dp.toPx()
        val top = 14.dp.toPx()
        val baseline = size.height - labelHeight
        val plotHeight = baseline - top
        val slot = size.width / buckets.size
        // A 2dp gap between neighbours; bars stay slim on the 30-day chart.
        val gap = 2.dp.toPx()
        val barWidth = (slot - gap).coerceAtMost(28.dp.toPx())
        val radius = 4.dp.toPx().coerceAtMost(barWidth / 2)

        // Recessive guides: the maximum and half of it, labelled at the left.
        listOf(1f, 0.5f).forEach { fraction ->
            val y = baseline - plotHeight * fraction
            drawLine(
                color = scheme.outlineVariant.copy(alpha = 0.6f),
                start = Offset(0f, y), end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            )
            val text = measurer.measure((max * fraction).toInt().toString(), labelStyle)
            drawText(text, topLeft = Offset(0f, y - text.size.height - 2.dp.toPx()))
        }
        drawLine(scheme.outlineVariant, Offset(0f, baseline), Offset(size.width, baseline), strokeWidth = 1.dp.toPx())

        buckets.forEachIndexed { index, bucket ->
            val x = slot * index + (slot - barWidth) / 2
            if (bucket.plays > 0) {
                val height = (plotHeight * bucket.plays / max * grow).coerceAtLeast(radius * 2)
                // Rounded at the data end only; the base sits flat on the baseline.
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = x, top = baseline - height, right = x + barWidth, bottom = baseline,
                            topLeftCornerRadius = CornerRadius(radius), topRightCornerRadius = CornerRadius(radius),
                            bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero
                        )
                    )
                }
                drawPath(path, color = if (highlighted < 0 || index == highlighted) scheme.primary else scheme.primary.copy(alpha = 0.45f))
            }
            labels[index]?.let { label ->
                val text = measurer.measure(label, labelStyle, maxLines = 1, overflow = TextOverflow.Clip)
                drawText(
                    text,
                    topLeft = Offset(
                        (x + barWidth / 2 - text.size.width / 2f).coerceIn(0f, size.width - text.size.width),
                        baseline + 4.dp.toPx()
                    )
                )
            }
        }
    }
}

@Composable
private fun TopSongsCard(songs: List<SongPlays>) {
    val most = songs.maxOf { it.plays }.coerceAtLeast(1)
    StatsCard(title = stringResource(R.string.stats_top_songs)) {
        Column {
            songs.forEachIndexed { index, song ->
                RankRow(
                    rank = index + 1,
                    title = song.title,
                    subtitle = song.artist,
                    plays = song.plays,
                    fraction = song.plays.toFloat() / most,
                    leading = {
                        ArtworkThumb(
                            uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.song),
                            size = 48.dp
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun TopArtistsCard(artists: List<ArtistPlays>) {
    val most = artists.maxOf { it.plays }.coerceAtLeast(1)
    StatsCard(title = stringResource(R.string.stats_top_artists)) {
        Column {
            artists.forEachIndexed { index, artist ->
                RankRow(
                    rank = index + 1,
                    title = artist.artist,
                    subtitle = null,
                    plays = artist.plays,
                    fraction = artist.plays.toFloat() / most,
                    leading = { InitialAvatar(artist.artist, size = 48.dp) }
                )
            }
        }
    }
}

/** Rank, picture, name, play count, and a thin bar comparing it with the top entry. */
@Composable
private fun RankRow(
    rank: Int,
    title: String,
    subtitle: String?,
    plays: Int,
    fraction: Float,
    leading: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            modifier = Modifier.width(22.dp),
            text = rank.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (rank <= 3) scheme.primary else scheme.onSurfaceVariant
        )
        leading()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    modifier = Modifier.padding(start = 8.dp),
                    text = pluralStringResource(R.plurals.stats_play_count, plays, plays),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant
                )
            }
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(scheme.surfaceVariant)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(scheme.primary)
                )
            }
        }
    }
}
