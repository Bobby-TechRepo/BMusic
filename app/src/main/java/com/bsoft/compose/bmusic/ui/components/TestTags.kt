package com.bsoft.compose.bmusic.ui.components

/**
 * Stable test tags (exposed as resource IDs, see MainActivity) used by the UI tests and the
 * Baseline Profile journeys in `:baselineprofile`. That module cannot see app code, so it repeats
 * these strings: change both together.
 */
object TestTags {
    const val HOME_PAGER = "home_pager"
    /** Followed by the destination route: tab_playlists, tab_songs, tab_album, tab_artists. */
    const val TAB_PREFIX = "tab_"
    const val SONGS_LIST = "songs_list"
    const val ALBUMS_GRID = "albums_grid"
    const val ARTISTS_LIST = "artists_list"
    const val PLAYLISTS_GRID = "playlists_grid"
    const val DETAIL_LIST = "detail_list"
    const val SONG_ROW = "song_row"
    const val ALBUM_TILE = "album_tile"
    const val ARTIST_ROW = "artist_row"
    const val MINI_PLAYER = "mini_player"
    const val QUEUE_LIST = "queue_list"
    const val FOLDERS_LIST = "folders_list"
}
