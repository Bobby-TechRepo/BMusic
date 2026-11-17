package com.bsoft.compose.bmusic.data.lyrics

import com.bsoft.compose.bmusic.data.tags.EmbeddedTagReader
import java.io.InputStream

/** Lyrics embedded in an audio file; see [EmbeddedTagReader] for the supported formats. */
object EmbeddedLyricsReader {
    /** Lyrics from [input], or null if there are none or the file is not understood. */
    fun read(input: InputStream): Lyrics? = EmbeddedTagReader.read(input).lyrics
}
