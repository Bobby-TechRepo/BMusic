package com.bsoft.compose.bmusic.ui.pages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.data.models.FolderTree
import com.bsoft.compose.bmusic.data.models.MusicFolder
import com.bsoft.compose.bmusic.data.models.Song
import com.bsoft.compose.bmusic.data.models.VolumeKind
import com.bsoft.compose.bmusic.data.states.QueueState
import com.bsoft.compose.bmusic.ui.components.SongView
import com.bsoft.compose.bmusic.ui.components.StatusScreen
import com.bsoft.compose.bmusic.ui.components.TestTags
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.heightIn

/**
 * The Folders tab: browse the library the way it is stored. Shows the sub-folders of the open
 * folder, then its songs. Play and Shuffle take the folder and everything below it; tapping a
 * song plays from there. Back goes up one folder.
 */
@Composable
fun FoldersPage(
    modifier: Modifier = Modifier,
    songs: List<Song>,
    queueState: QueueState,
    playing: Boolean,
    onMenu: (Song) -> Unit,
    /** Plays [songs] (the folder's whole contents) from [index]; shuffle null keeps the mode. */
    play: (folder: MusicFolder, songs: List<Song>, index: Int, shuffle: Boolean?) -> Unit,
) {
    val root = remember(songs) { FolderTree.build(songs) }
    var openPath by rememberSaveable { mutableStateOf(FolderTree.ROOT_PATH) }
    // A folder that disappeared (files moved or deleted) falls back to its nearest parent.
    val trail = remember(root, openPath) { nearestTrail(root, openPath) }
    val folder = trail.last()
    LaunchedEffect(folder.path) { if (folder.path != openPath) openPath = folder.path }

    BackHandler(enabled = trail.size > 1) { openPath = trail[trail.size - 2].path }

    if (root.songCount == 0) {
        StatusScreen(
            modifier = modifier,
            title = stringResource(R.string.library_empty_title),
            body = stringResource(R.string.library_empty_body)
        )
        return
    }

    val listState = rememberLazyListState()
    // Opening another folder starts at its top.
    LaunchedEffect(folder.path) { listState.scrollToItem(0) }
    val folderSongs = remember(folder) { folder.allSongs() }

    Column(modifier = modifier.fillMaxSize()) {
        Breadcrumbs(trail = trail, onOpen = { openPath = it.path })
        FolderHeader(
            title = folderTitle(folder, isRoot = trail.size == 1),
            folder = folder,
            onPlay = { play(folder, folderSongs, 0, false) },
            onShuffle = {
                folderSongs.indices.randomOrNull()?.let { play(folder, folderSongs, it, true) }
            }
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag(TestTags.FOLDERS_LIST),
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(folder.folders, key = { "f:" + it.path }) { child ->
                FolderRow(
                    name = folderTitle(child, isRoot = false),
                    icon = when (child.volume) {
                        VolumeKind.INTERNAL -> R.drawable.ms_smartphone
                        VolumeKind.EXTERNAL -> R.drawable.ms_sd_card
                        else -> R.drawable.ms_folder
                    },
                    songCount = child.songCount,
                    folderCount = child.folders.size,
                    onClick = { openPath = child.path }
                )
            }
            // Direct songs come first in allSongs(), so their index is their queue position.
            itemsIndexed(folder.songs, key = { _, song -> "s:" + song.id }) { index, song ->
                SongView(
                    song = song,
                    currentPlaying = queueState.current?.id == song.id,
                    playing = playing,
                    onMenu = { onMenu(song) }
                ) {
                    play(folder, folderSongs, index, null)
                }
            }
        }
    }
}

/** The trail to [path], or to its closest ancestor that still exists. */
private fun nearestTrail(root: MusicFolder, path: String): List<MusicFolder> {
    var candidate = path
    while (true) {
        val trail = root.trail(candidate)
        if (trail.isNotEmpty()) return trail
        if (candidate.isEmpty() || '/' !in candidate) return listOf(root)
        candidate = candidate.substringBeforeLast('/')
    }
}

@Composable
private fun folderTitle(folder: MusicFolder, isRoot: Boolean): String = when {
    isRoot -> stringResource(R.string.nav_folders)
    folder.volume == VolumeKind.INTERNAL -> stringResource(R.string.folder_internal_storage)
    folder.volume == VolumeKind.EXTERNAL -> stringResource(R.string.folder_sd_card, folder.name)
    else -> folder.name
}

@Composable
private fun Breadcrumbs(trail: List<MusicFolder>, onOpen: (MusicFolder) -> Unit) {
    if (trail.size <= 1) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState(), reverseScrolling = true)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        trail.forEachIndexed { index, folder ->
            val last = index == trail.lastIndex
            if (index > 0) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    imageVector = ImageVector.vectorResource(R.drawable.ms_chevron_right),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = null
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !last, role = Role.Button) { onOpen(folder) }
                    // 48 dp tall: an easy target, as accessibility guidelines ask.
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (index == 0) {
                    Icon(
                        modifier = Modifier.size(18.dp).padding(end = 2.dp),
                        imageVector = ImageVector.vectorResource(R.drawable.ms_home),
                        tint = MaterialTheme.colorScheme.primary,
                        contentDescription = null
                    )
                }
                Text(
                    text = folderTitle(folder, isRoot = index == 0),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun FolderHeader(title: String, folder: MusicFolder, onPlay: () -> Unit, onShuffle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                modifier = Modifier.semantics { heading() },
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = pluralStringResource(R.plurals.search_songs_count, folder.songCount, folder.songCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FilledTonalButton(
            onClick = onShuffle,
            enabled = folder.songCount > 0,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            modifier = Modifier.heightIn(min = 36.dp)
        ) {
            Icon(modifier = Modifier.size(18.dp), imageVector = ImageVector.vectorResource(R.drawable.ms_shuffle), contentDescription = null)
            Text(modifier = Modifier.padding(start = 6.dp), text = stringResource(R.string.shuffle))
        }
        FilledTonalButton(
            onClick = onPlay,
            enabled = folder.songCount > 0,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            modifier = Modifier.heightIn(min = 36.dp)
        ) {
            Icon(modifier = Modifier.size(18.dp), imageVector = ImageVector.vectorResource(R.drawable.ms_play_arrow_fill1), contentDescription = null)
            Text(modifier = Modifier.padding(start = 6.dp), text = stringResource(R.string.play))
        }
    }
}

@Composable
private fun FolderRow(name: String, icon: Int, songCount: Int, folderCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = ImageVector.vectorResource(icon), contentDescription = null)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val songs = pluralStringResource(R.plurals.search_songs_count, songCount, songCount)
            Text(
                text = if (folderCount > 0) {
                    stringResource(R.string.song_row_subtitle, songs, pluralStringResource(R.plurals.folder_count, folderCount, folderCount))
                } else songs,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ms_chevron_right),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            contentDescription = null
        )
    }
}
