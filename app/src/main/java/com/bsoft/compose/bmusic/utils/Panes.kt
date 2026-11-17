package com.bsoft.compose.bmusic.utils

/*
 * Two-pane library on large screens (tablets, unfolded foldables, desktop windows): Home stays on
 * the left and an album, artist or playlist opens on the right, with its own small back stack.
 * These functions move screens between the main back stack and that pane when the window is
 * resized, so nothing the user opened is lost. Generic so they can be unit-tested without
 * Android or Navigation types.
 */

/** Result of moving screens between the main stack and the detail pane. */
data class PaneStacks<T>(val root: List<T>, val pane: List<T>)

/**
 * The window became wide: the detail screens directly above Home (and only those) move into the
 * pane. Anything else on top (Settings, the player...) stays full screen, and so does
 * everything below it.
 */
fun <T> moveDetailsIntoPane(root: List<T>, pane: List<T>, home: T, isDetail: (T) -> Boolean): PaneStacks<T> {
    if (root.firstOrNull() != home || root.size < 2) return PaneStacks(root, pane)
    val above = root.drop(1)
    if (!above.all(isDetail)) return PaneStacks(root, pane)
    return PaneStacks(listOf(home), pane + above)
}

/**
 * The window became narrow: the pane's screens go back onto the main stack, right above Home,
 * under anything that was opened full screen on top.
 */
fun <T> movePaneIntoRoot(root: List<T>, pane: List<T>, home: T): PaneStacks<T> {
    if (pane.isEmpty()) return PaneStacks(root, pane)
    val homeIndex = root.indexOf(home)
    if (homeIndex < 0) return PaneStacks(root + pane, emptyList())
    val merged = root.subList(0, homeIndex + 1) + pane + root.subList(homeIndex + 1, root.size)
    return PaneStacks(merged, emptyList())
}

/** Opening a detail from Home replaces what the pane shows (it doesn't pile up). */
fun <T> openFromHome(pane: List<T>, route: T): List<T> = if (pane.lastOrNull() == route) pane else listOf(route)
