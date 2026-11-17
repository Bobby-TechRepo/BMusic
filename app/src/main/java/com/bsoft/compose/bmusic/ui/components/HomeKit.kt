package com.bsoft.compose.bmusic.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.utils.HomeDestination
import java.time.LocalTime

/*
 * The top of Home: a greeting with the app name, round shortcuts to Stats and Settings, the
 * search pill, and (on phones) a row of pill-shaped tabs.
 */

/** "Good morning / afternoon / evening" for the hour of day. */
@Composable
private fun greeting(): String {
    val hour = remember { LocalTime.now().hour }
    return stringResource(
        when (hour) {
            in 5..11 -> R.string.greeting_morning
            in 12..17 -> R.string.greeting_afternoon
            else -> R.string.greeting_evening
        }
    )
}

@Composable
fun HomeHeader(
    onStats: () -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = greeting(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    modifier = Modifier.semantics { heading() },
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            HeaderAction(R.drawable.ms_bar_chart, stringResource(R.string.stats_title), onStats)
            HeaderAction(R.drawable.ms_settings, stringResource(R.string.settings), onSettings)
        }
        SearchPill(onClick = onSearch, modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp))
    }
}

/** A round, tinted icon button for the header. */
@Composable
private fun HeaderAction(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = ImageVector.vectorResource(icon), contentDescription = label)
        }
    }
}

/**
 * Pill-shaped tabs that scroll sideways, so every label fits in full (fixed tabs cut
 * "Playlists" short on narrow phones). The selected pill is filled with the accent colour.
 */
@Composable
fun HomeTabChips(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HomeDestination.entries.forEachIndexed { index, destination ->
            HomeTabChip(
                destination = destination,
                selected = index == selected,
                onClick = { onSelect(index) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeTabChip(destination: HomeDestination, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // Swiping to a tab whose pill is off screen scrolls the row to show it.
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) { if (selected) requester.bringIntoView() }
    val container by animateColorAsState(
        if (selected) scheme.primary else scheme.surfaceContainerHigh,
        label = "tabContainer"
    )
    val content by animateColorAsState(
        if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
        label = "tabContent"
    )
    Surface(
        modifier = Modifier
            .testTag(TestTags.TAB_PREFIX + destination.route)
            .bringIntoViewRequester(requester)
            .semantics {
                this.selected = selected
                role = Role.Tab
            },
        shape = CircleShape,
        color = container,
        contentColor = content,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.heightIn(min = 40.dp).padding(start = 14.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                modifier = Modifier.size(18.dp),
                imageVector = ImageVector.vectorResource(if (selected) destination.selectedIcon else destination.icon),
                contentDescription = null
            )
            Text(
                text = stringResource(destination.labelRes),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}
