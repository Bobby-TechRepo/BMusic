package com.bsoft.compose.bmusic.utils

/** What a hardware-keyboard shortcut does (tablets with keyboards, Chromebooks, desktop windows). */
enum class ShortcutAction { PLAY_PAUSE, NEXT, PREVIOUS, SEEK_FORWARD, SEEK_BACK, SEARCH }

/** android.view.KeyEvent key codes, repeated here so the mapping is testable on the JVM. */
object KeyCodes {
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val F = 34
    const val J = 38
    const val K = 39
    const val L = 40
    const val N = 42
    const val P = 44
    const val SPACE = 62
    const val SLASH = 76
}

/**
 * The shortcut for a key press, or null. Only reached when no text field has the key (Android
 * gives the focused field first refusal), so plain letters are safe to use.
 *
 * | Keys | Action |
 * | --- | --- |
 * | Space, K | Play / pause |
 * | Ctrl+→, Shift+N | Next song |
 * | Ctrl+←, Shift+P | Previous song |
 * | L / J | Forward / back 10 seconds |
 * | Ctrl+F, / | Search |
 *
 * Plain arrow keys are left alone: they move the focus between buttons.
 */
fun shortcutFor(keyCode: Int, ctrl: Boolean, shift: Boolean, alt: Boolean, meta: Boolean): ShortcutAction? {
    if (alt || meta) return null
    return when {
        ctrl && shift -> null
        ctrl -> when (keyCode) {
            KeyCodes.DPAD_RIGHT -> ShortcutAction.NEXT
            KeyCodes.DPAD_LEFT -> ShortcutAction.PREVIOUS
            KeyCodes.F -> ShortcutAction.SEARCH
            else -> null
        }
        shift -> when (keyCode) {
            KeyCodes.N -> ShortcutAction.NEXT
            KeyCodes.P -> ShortcutAction.PREVIOUS
            else -> null
        }
        else -> when (keyCode) {
            KeyCodes.SPACE, KeyCodes.K -> ShortcutAction.PLAY_PAUSE
            KeyCodes.L -> ShortcutAction.SEEK_FORWARD
            KeyCodes.J -> ShortcutAction.SEEK_BACK
            KeyCodes.SLASH -> ShortcutAction.SEARCH
            else -> null
        }
    }
}
