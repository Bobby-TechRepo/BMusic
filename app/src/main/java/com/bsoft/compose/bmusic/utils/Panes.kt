package com.bsoft.compose.bmusic.utils

/*
 * Tablet layout (landscape, >= 840 dp wide): the library lives in a phone-sized section on the
 * left, and the right section shows the player, or Settings / Listening stats swapped in for it.
 * Each side keeps its own back stack. These functions decide which side a screen opens on and
 * move screens between the single phone stack and the two tablet stacks when the window changes
 * size, so nothing the user opened is lost. Generic so they can be unit-tested without Android or
 * Navigation types.
 */

/** The two back stacks of the tablet layout. */
data class TabletStacks<T>(val left: List<T>, val right: List<T>)

/**
 * The window became wide. Library screens stay on the left (with Home at the bottom). The player
 * always sits at the bottom of the right stack, and the most recent Settings or Stats screen, if
 * one was open, is swapped in on top of it.
 */
fun <T> splitForTablet(root: List<T>, home: T, player: T, isRightSide: (T) -> Boolean): TabletStacks<T> {
    val library = root.filterNot(isRightSide)
    val left = if (library.firstOrNull() == home) library else listOf(home) + library.filter { it != home }
    val swapped = root.lastOrNull { isRightSide(it) && it != player }
    return TabletStacks(left, listOf(player) + listOfNotNull(swapped))
}

/**
 * The window became narrow: everything goes back onto one stack, with a swapped-in Settings or
 * Stats screen on top. The resting player is dropped — on a phone it opens from the mini player.
 */
fun <T> mergeForPhone(left: List<T>, right: List<T>, player: T): List<T> = left + right.filter { it != player }

/**
 * Opening a right-side screen: the player resets the side to just the player; Settings or Stats
 * replaces whatever was swapped in (they never pile up).
 */
fun <T> openOnRight(right: List<T>, route: T, player: T): List<T> =
    if (route == player) listOf(player) else listOf(player, route)
