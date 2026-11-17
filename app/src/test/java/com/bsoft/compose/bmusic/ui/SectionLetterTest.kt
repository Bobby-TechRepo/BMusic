package com.bsoft.compose.bmusic.ui

import com.bsoft.compose.bmusic.ui.components.sectionLetter
import org.junit.Assert.assertEquals
import org.junit.Test

class SectionLetterTest {
    @Test
    fun `latin letters map to upper case`() {
        assertEquals('A', sectionLetter("apple"))
        assertEquals('Z', sectionLetter("Zebra"))
    }

    @Test
    fun `accents are stripped`() {
        assertEquals('E', sectionLetter("Étoile"))
    }

    @Test
    fun `empty, blank and null titles do not crash`() {
        // Regression: `title.first()` threw NoSuchElementException.
        assertEquals('#', sectionLetter(""))
        assertEquals('#', sectionLetter("   "))
        assertEquals('#', sectionLetter(null))
    }

    @Test
    fun `digits, symbols and other scripts group under hash`() {
        assertEquals('#', sectionLetter("2 Become 1"))
        assertEquals('#', sectionLetter("(Intro)"))
        assertEquals('#', sectionLetter("夜に駆ける"))
    }
}
