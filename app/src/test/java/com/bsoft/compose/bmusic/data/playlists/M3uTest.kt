package com.bsoft.compose.bmusic.data.playlists

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uTest {
    @Test
    fun `parses extended m3u`() {
        val text = "\uFEFF#EXTM3U\r\n#EXTINF:215,Daft Punk - One More Time\r\n/storage/emulated/0/Music/one.mp3\r\n\r\n#EXTINF:-1,Just A Title\r\nrelative/two.flac\r\n"
        assertEquals(
            listOf(
                M3uEntry("/storage/emulated/0/Music/one.mp3", title = "One More Time", artist = "Daft Punk", durationMs = 215_000),
                M3uEntry("relative/two.flac", title = "Just A Title", artist = null, durationMs = null),
            ),
            M3u.parse(text)
        )
    }

    @Test
    fun `plain m3u, windows paths, file uris and streams`() {
        val text = "C:\\Music\\Album\\three.mp3\nfile:///sdcard/Music/My%20Song.mp3\nhttp://radio.example/stream\n#EXTINF:10,Ignored stream\nhttps://x.example/a.mp3\nfour.ogg"
        val entries = M3u.parse(text)
        assertEquals(listOf("C:\\Music\\Album\\three.mp3", "/sdcard/Music/My Song.mp3", "four.ogg"), entries.map { it.location })
        assertEquals("three.mp3", entries[0].fileName)
        assertNull(entries[2].title) // the #EXTINF belonged to a skipped stream
    }

    @Test
    fun `extinf attributes before the comma are tolerated`() {
        val entry = M3u.parse("#EXTINF:180.5 tvg-id=\"x\",A - B\nb.mp3").single()
        assertEquals(180_500L, entry.durationMs)
        assertEquals("A", entry.artist)
        assertEquals("B", entry.title)
    }

    @Test
    fun `format round trips`() {
        val entries = listOf(
            M3uEntry("/Music/a.mp3", title = "Song A", artist = "Artist", durationMs = 200_400),
            M3uEntry("b.mp3", title = "Song B", artist = null, durationMs = null),
        )
        val text = M3u.format(entries)
        assertEquals("#EXTM3U\n#EXTINF:200,Artist - Song A\n/Music/a.mp3\n#EXTINF:-1,Song B\nb.mp3\n", text)
        assertEquals(listOf(entries[0].copy(durationMs = 200_000), entries[1]), M3u.parse(text))
    }

    private val library = listOf(
        LibraryFile(1, "/storage/emulated/0/Music/Album A/song.mp3", "song.mp3", "Song", "Artist", 200_000),
        LibraryFile(2, "/storage/emulated/0/Music/Album B/song.mp3", "song.mp3", "Song", "Artist", 300_000),
        LibraryFile(3, "/storage/emulated/0/Music/unique.flac", "unique.flac", "Unique", "Band", 100_000),
        LibraryFile(4, "/storage/emulated/0/Download/x.mp3", "x.mp3", "Café", "Émile", 150_000),
    )
    private val matcher = M3uMatcher(library)

    @Test
    fun `matches the exact path ignoring case`() {
        assertEquals(2L, matcher.match(M3uEntry("/STORAGE/emulated/0/Music/Album B/song.mp3"))?.id)
    }

    @Test
    fun `matches a unique file name from another device`() {
        assertEquals(3L, matcher.match(M3uEntry("D:\\Rips\\unique.flac"))?.id)
    }

    @Test
    fun `same file name picks the closest folders`() {
        assertEquals(2L, matcher.match(M3uEntry("../Album B/song.mp3"))?.id)
        assertEquals(1L, matcher.match(M3uEntry("/sdcard/Music/Album A/song.mp3"))?.id)
    }

    @Test
    fun `falls back to title and artist`() {
        assertEquals(4L, matcher.match(M3uEntry("gone.mp3", title = "cafe", artist = "EMILE"))?.id)
        // Same title/artist twice: the length decides.
        val twins = M3uMatcher(
            listOf(
                LibraryFile(7, "/a/1.mp3", "1.mp3", "Live", "Band", 200_000),
                LibraryFile(8, "/a/2.mp3", "2.mp3", "Live", "Band", 400_000),
            )
        )
        assertEquals(8L, twins.match(M3uEntry("x.mp3", title = "Live", artist = "Band", durationMs = 401_000))?.id)
    }

    @Test
    fun `unknown songs give null`() {
        assertNull(matcher.match(M3uEntry("nothing.mp3")))
        assertNull(matcher.match(M3uEntry("nothing.mp3", title = "No", artist = "One")))
    }

    @Test
    fun `unique playlist names`() {
        assertEquals("Mix", uniquePlaylistName("Mix", listOf("Other")))
        assertEquals("Mix (2)", uniquePlaylistName("Mix", listOf("mix")))
        assertEquals("Mix (3)", uniquePlaylistName("Mix", listOf("Mix", "Mix (2)")))
    }
}
