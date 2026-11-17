package com.bsoft.compose.bmusic.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class PanesTest {

    private val detail: (String) -> Boolean = { it.startsWith("album") || it.startsWith("artist") || it.startsWith("playlist") }

    @Test
    fun detailsAboveHomeMoveIntoThePane() {
        val result = moveDetailsIntoPane(listOf("home", "artist1", "album2"), emptyList(), "home", detail)
        assertEquals(PaneStacks(listOf("home"), listOf("artist1", "album2")), result)
    }

    @Test
    fun fullScreenTopKeepsEverythingWhereItIs() {
        val root = listOf("home", "album2", "settings")
        assertEquals(PaneStacks(root, emptyList<String>()), moveDetailsIntoPane(root, emptyList(), "home", detail))
    }

    @Test
    fun onlyHomeMeansNothingToMove() {
        assertEquals(PaneStacks(listOf("home"), listOf("album1")), moveDetailsIntoPane(listOf("home"), listOf("album1"), "home", detail))
    }

    @Test
    fun paneGoesBackRightAboveHome() {
        val result = movePaneIntoRoot(listOf("home", "settings"), listOf("artist1", "album2"), "home")
        assertEquals(PaneStacks(listOf("home", "artist1", "album2", "settings"), emptyList<String>()), result)
    }

    @Test
    fun emptyPaneChangesNothing() {
        val root = listOf("home", "player")
        assertEquals(PaneStacks(root, emptyList<String>()), movePaneIntoRoot(root, emptyList(), "home"))
    }

    @Test
    fun roundTripKeepsTheSameScreens() {
        val wide = moveDetailsIntoPane(listOf("home", "playlist3", "album4"), emptyList(), "home", detail)
        val narrow = movePaneIntoRoot(wide.root, wide.pane, "home")
        assertEquals(listOf("home", "playlist3", "album4"), narrow.root)
    }

    @Test
    fun openingFromHomeReplacesThePane() {
        assertEquals(listOf("album9"), openFromHome(listOf("artist1", "album2"), "album9"))
        // Tapping the open one again keeps its history.
        assertEquals(listOf("artist1", "album2"), openFromHome(listOf("artist1", "album2"), "album2"))
    }
}
