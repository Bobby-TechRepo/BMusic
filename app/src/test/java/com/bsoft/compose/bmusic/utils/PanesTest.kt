package com.bsoft.compose.bmusic.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class PanesTest {

    private val rightSide: (String) -> Boolean = { it == "playing" || it == "settings" || it == "stats" }

    @Test
    fun libraryScreensStayLeftAndPlayerRests() {
        val result = splitForTablet(listOf("home", "artist1", "album2"), "home", "playing", rightSide)
        assertEquals(TabletStacks(listOf("home", "artist1", "album2"), listOf("playing")), result)
    }

    @Test
    fun settingsOnTopIsSwappedInForThePlayer() {
        val result = splitForTablet(listOf("home", "album2", "playing", "settings"), "home", "playing", rightSide)
        assertEquals(TabletStacks(listOf("home", "album2"), listOf("playing", "settings")), result)
    }

    @Test
    fun onlyTheLatestSwappedScreenIsKept() {
        val result = splitForTablet(listOf("home", "stats", "search", "settings"), "home", "playing", rightSide)
        assertEquals(TabletStacks(listOf("home", "search"), listOf("playing", "settings")), result)
    }

    @Test
    fun homeIsAlwaysAtTheBottomOnTheLeft() {
        val result = splitForTablet(listOf("settings"), "home", "playing", rightSide)
        assertEquals(TabletStacks(listOf("home"), listOf("playing", "settings")), result)
    }

    @Test
    fun phoneGetsTheSwappedScreenOnTop() {
        assertEquals(
            listOf("home", "album2", "settings"),
            mergeForPhone(listOf("home", "album2"), listOf("playing", "settings"), "playing")
        )
    }

    @Test
    fun restingPlayerIsNotPushedOnPhones() {
        assertEquals(listOf("home"), mergeForPhone(listOf("home"), listOf("playing"), "playing"))
    }

    @Test
    fun rightSideScreensNeverPileUp() {
        assertEquals(listOf("playing", "stats"), openOnRight(listOf("playing", "settings"), "stats", "playing"))
        assertEquals(listOf("playing"), openOnRight(listOf("playing", "settings"), "playing", "playing"))
    }

    @Test
    fun roundTripKeepsEverything() {
        val phone = listOf("home", "artist1", "album2", "stats")
        val split = splitForTablet(phone, "home", "playing", rightSide)
        assertEquals(phone, mergeForPhone(split.left, split.right, "playing"))
    }
}
