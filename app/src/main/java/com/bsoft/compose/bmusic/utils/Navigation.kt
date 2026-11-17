package com.bsoft.compose.bmusic.utils

import androidx.navigation3.runtime.NavKey
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.ui.pages.PlaylistsOptions
import kotlinx.serialization.Serializable

enum class HomeDestination(val route: String, val labelRes: Int, val icon: Int, val selectedIcon: Int) {
    PLAYLISTS("playlists", R.string.nav_playlists, R.drawable.ms_queue_music, R.drawable.ms_queue_music_fill1),
    SONGS("songs", R.string.nav_songs, R.drawable.ms_music_note, R.drawable.ms_music_note_fill1),
    ALBUM("album", R.string.nav_albums, R.drawable.ms_album, R.drawable.ms_album_fill1),
    Artists("artists", R.string.nav_artists, R.drawable.ms_person, R.drawable.ms_person_fill1),
    FOLDERS("folders", R.string.nav_folders, R.drawable.ms_folder, R.drawable.ms_folder_fill1),
}

@Serializable
sealed interface Route: NavKey {
    @Serializable
    data object Home : Route

    @Serializable
    data object Settings: Route

    @Serializable
    data class Playlist(val playlistsOptions: PlaylistsOptions?, val playlist: Long?): Route

    @Serializable
    data class AddSongs(val playlist: Long, val title: String): Route

    @Serializable
    data class Album(val id: Long): Route

    @Serializable
    data class Artist(val id: Long): Route

    @Serializable
    data object Playing: Route

    @Serializable
    data object Search: Route

    @Serializable
    data object Stats: Route

    @Serializable
    data class TagEditor(val songId: Long): Route
}