package com.bsoft.compose.bmusic.data.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the sleep timer is doing. */
sealed interface SleepTimerState {
    data object Off : SleepTimerState

    /** Stops playback when the clock reaches [endsAtMs] ([clock] time base). */
    data class Counting(val endsAtMs: Long, val totalMs: Long) : SleepTimerState

    /** Stops playback when the current song ends. */
    data object EndOfTrack : SleepTimerState
}

/** Length of the volume fade before the timer stops playback. */
const val SLEEP_FADE_MS = 10_000L

/**
 * The sleep timer. The screen sets it; the playback service watches it and stops playback, so
 * it works with the app closed. [clock] is a monotonic millisecond clock
 * (SystemClock.elapsedRealtime on a device), injectable for tests.
 */
class SleepTimer(private val clock: () -> Long) {
    private val _state = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    fun start(minutes: Int) {
        val total = minutes.coerceAtLeast(1) * 60_000L
        _state.value = SleepTimerState.Counting(endsAtMs = clock() + total, totalMs = total)
    }

    /** Adds time to a running countdown, or starts one. */
    fun extend(minutes: Int) {
        val current = _state.value
        if (current is SleepTimerState.Counting) {
            val extra = minutes * 60_000L
            _state.value = current.copy(endsAtMs = current.endsAtMs + extra, totalMs = current.totalMs + extra)
        } else {
            start(minutes)
        }
    }

    fun stopAfterCurrentTrack() {
        _state.value = SleepTimerState.EndOfTrack
    }

    fun cancel() {
        _state.value = SleepTimerState.Off
    }

    /** Time left on a countdown, never negative; null when not counting. */
    fun remainingMs(): Long? = (_state.value as? SleepTimerState.Counting)?.let { (it.endsAtMs - clock()).coerceAtLeast(0L) }

    /** True when a countdown has run out. */
    fun isExpired(): Boolean = remainingMs() == 0L

    /**
     * Volume multiplier for the last [SLEEP_FADE_MS] of a countdown: 1 until then, falling to 0.
     */
    fun volumeFactor(): Float {
        val remaining = remainingMs() ?: return 1f
        return (remaining.toFloat() / SLEEP_FADE_MS).coerceIn(0f, 1f)
    }
}
