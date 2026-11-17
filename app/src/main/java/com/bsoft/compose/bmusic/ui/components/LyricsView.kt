package com.bsoft.compose.bmusic.ui.components

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.data.lyrics.Lyrics
import com.bsoft.compose.bmusic.viewmodels.LyricsUiState

/** After the user scrolls the lyrics, wait this long before following the song again. */
private const val FOLLOW_PAUSE_MS = 3_000L

/**
 * Lyrics of the playing song. Synced lyrics highlight and follow the current line; tap a line
 * to jump there. Without lyrics, offers to import a .lrc file.
 */
@Composable
fun LyricsView(
    modifier: Modifier = Modifier,
    state: LyricsUiState,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    onImport: () -> Unit,
    onRemoveImported: () -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val lyrics = state.lyrics
            when {
                state.songId == null -> Message(stringResource(R.string.lyrics_nothing_playing))
                state.loading -> CircularProgressIndicator(color = Color.White)
                lyrics == null -> Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Message(stringResource(R.string.lyrics_none))
                    Text(
                        text = stringResource(R.string.lyrics_none_hint),
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                    OutlinedButton(onClick = onImport) { Text(stringResource(R.string.lyrics_import), color = Color.White) }
                }
                lyrics is Lyrics.Synced -> SyncedLyrics(lyrics, positionMs, onSeek)
                lyrics is Lyrics.Plain -> Text(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    text = lyrics.text,
                    color = Color.White,
                    fontSize = 18.sp,
                    lineHeight = 28.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        if (state.lyrics != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onImport) {
                    Text(stringResource(if (state.imported) R.string.lyrics_replace else R.string.lyrics_import), color = Color.White)
                }
                if (state.imported) {
                    TextButton(onClick = onRemoveImported) {
                        Text(stringResource(R.string.lyrics_remove_imported), color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text = text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
}

@Composable
private fun SyncedLyrics(lyrics: Lyrics.Synced, positionMs: Long, onSeek: (Long) -> Unit) {
    val listState = rememberLazyListState()
    val current = lyrics.indexAt(positionMs)

    // Don't fight the user: stop following while they scroll, and for a moment after.
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var holdUntil by remember { mutableLongStateOf(0L) }
    LaunchedEffect(dragged) {
        if (dragged) holdUntil = Long.MAX_VALUE
        else if (holdUntil == Long.MAX_VALUE) holdUntil = SystemClock.uptimeMillis() + FOLLOW_PAUSE_MS
    }
    LaunchedEffect(lyrics, current) {
        if (current >= 0 && SystemClock.uptimeMillis() > holdUntil) listState.animateScrollToItem(current)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // The padding lets the current line sit a third of the way down, even the first one.
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = maxHeight / 3, bottom = maxHeight / 2),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(lyrics.lines) { index, line ->
                val active = index == current
                val color by animateColorAsState(
                    targetValue = if (active) Color.White else Color.White.copy(alpha = if (index < current) 0.45f else 0.6f),
                    label = "lyric line"
                )
                Text(
                    modifier = Modifier.fillMaxWidth().clickable { onSeek(line.timeMs) },
                    text = line.text.ifBlank { "♪" },
                    color = color,
                    fontSize = if (active) 24.sp else 20.sp,
                    lineHeight = 30.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
