package com.bsoft.compose.bmusic.data.tags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagEditsTest {

    private val original = TagValues(title = "Song", artist = "Band", album = "LP", year = "1999", track = "3")

    @Test
    fun onlyRealChangesCount() {
        // Surrounding spaces are not a change.
        assertTrue(original.with(TagField.TITLE, "  Song ").changesFrom(original).isEmpty())
        assertEquals(setOf(TagField.ALBUM, TagField.GENRE), original.copy(album = "EP", genre = "Rock").changesFrom(original))
    }

    @Test
    fun withAndGetCoverEveryField() {
        var values = TagValues()
        TagField.entries.forEachIndexed { i, field -> values = values.with(field, "v$i") }
        TagField.entries.forEachIndexed { i, field -> assertEquals("v$i", values[field]) }
    }

    @Test
    fun yearsMustBeFourDigitsOrADate() {
        assertNull(tagProblem(TagField.YEAR, "1999"))
        assertNull(tagProblem(TagField.YEAR, "2021-05-14"))
        assertNull(tagProblem(TagField.YEAR, ""))
        assertEquals(TagProblem.NOT_A_YEAR, tagProblem(TagField.YEAR, "99"))
        assertEquals(TagProblem.NOT_A_YEAR, tagProblem(TagField.YEAR, "nineteen"))
    }

    @Test
    fun trackAndDiscArePositiveNumbersWithAnOptionalTotal() {
        assertNull(tagProblem(TagField.TRACK, "3"))
        assertNull(tagProblem(TagField.TRACK, "03/12"))
        assertNull(tagProblem(TagField.DISC, ""))
        assertEquals(TagProblem.NOT_A_NUMBER, tagProblem(TagField.TRACK, "0"))
        assertEquals(TagProblem.NOT_A_NUMBER, tagProblem(TagField.TRACK, "A1"))
        assertEquals(TagProblem.NOT_A_NUMBER, tagProblem(TagField.DISC, "-1"))
    }

    @Test
    fun titleIsRequired() {
        assertEquals(TagProblem.TITLE_REQUIRED, tagProblem(TagField.TITLE, "   "))
        assertEquals(mapOf(TagField.TITLE to TagProblem.TITLE_REQUIRED, TagField.YEAR to TagProblem.NOT_A_YEAR),
            original.copy(title = "", year = "x").problems())
    }

    @Test
    fun numbersAreWrittenWithoutTotalsOrLeadingZeros() {
        assertEquals("3", tagNumber("03/12"))
        assertEquals("12", tagNumber(" 12 "))
        assertEquals("", tagNumber(""))
    }

    @Test
    fun lyricsKeepInnerLineBreaks() {
        val values = TagValues(lyrics = "\n line one\nline two \n\n")
        assertEquals("line one\nline two", values.cleaned().lyrics)
    }

    @Test
    fun extensions() {
        assertEquals("flac", fileExtension("/storage/emulated/0/Music/a.b.FLAC"))
        assertEquals("", fileExtension("/storage/emulated/0/Music/noext"))
        assertTrue("mp3" in EDITABLE_EXTENSIONS)
        assertTrue("opus" !in EDITABLE_EXTENSIONS)
    }
}
