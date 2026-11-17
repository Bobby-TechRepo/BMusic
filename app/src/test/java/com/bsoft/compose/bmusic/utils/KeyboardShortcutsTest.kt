package com.bsoft.compose.bmusic.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyboardShortcutsTest {

    private fun key(code: Int, ctrl: Boolean = false, shift: Boolean = false, alt: Boolean = false, meta: Boolean = false) =
        shortcutFor(code, ctrl, shift, alt, meta)

    @Test
    fun keyCodesMatchAndroid() {
        // Values of android.view.KeyEvent.KEYCODE_*; they never change.
        assertEquals(62, KeyCodes.SPACE)
        assertEquals(21, KeyCodes.DPAD_LEFT)
        assertEquals(22, KeyCodes.DPAD_RIGHT)
        assertEquals(34, KeyCodes.F)
        assertEquals(76, KeyCodes.SLASH)
    }

    @Test
    fun playPause() {
        assertEquals(ShortcutAction.PLAY_PAUSE, key(KeyCodes.SPACE))
        assertEquals(ShortcutAction.PLAY_PAUSE, key(KeyCodes.K))
    }

    @Test
    fun nextAndPrevious() {
        assertEquals(ShortcutAction.NEXT, key(KeyCodes.DPAD_RIGHT, ctrl = true))
        assertEquals(ShortcutAction.PREVIOUS, key(KeyCodes.DPAD_LEFT, ctrl = true))
        assertEquals(ShortcutAction.NEXT, key(KeyCodes.N, shift = true))
        assertEquals(ShortcutAction.PREVIOUS, key(KeyCodes.P, shift = true))
    }

    @Test
    fun seekAndSearch() {
        assertEquals(ShortcutAction.SEEK_FORWARD, key(KeyCodes.L))
        assertEquals(ShortcutAction.SEEK_BACK, key(KeyCodes.J))
        assertEquals(ShortcutAction.SEARCH, key(KeyCodes.F, ctrl = true))
        assertEquals(ShortcutAction.SEARCH, key(KeyCodes.SLASH))
    }

    @Test
    fun plainArrowsStayForFocusNavigation() {
        assertNull(key(KeyCodes.DPAD_RIGHT))
        assertNull(key(KeyCodes.DPAD_LEFT))
    }

    @Test
    fun otherModifiersAreIgnored() {
        assertNull(key(KeyCodes.SPACE, alt = true))
        assertNull(key(KeyCodes.F, ctrl = true, shift = true))
        assertNull(key(KeyCodes.K, meta = true))
        assertNull(key(KeyCodes.F))
    }
}
