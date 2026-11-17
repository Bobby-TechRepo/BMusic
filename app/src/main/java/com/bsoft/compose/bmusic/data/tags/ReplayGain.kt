package com.bsoft.compose.bmusic.data.tags

import kotlin.math.min
import kotlin.math.pow

/** Which ReplayGain value levels the volume. Stored by name in the app settings. */
enum class ReplayGainMode {
    OFF,

    /** Every song at the same loudness; best for shuffle. */
    TRACK,

    /** Whole albums at the same loudness, keeping quiet and loud songs of an album as mastered. */
    ALBUM;

    companion object {
        fun fromName(name: String?): ReplayGainMode = entries.firstOrNull { it.name == name } ?: OFF
    }
}

/** ReplayGain values from a file's tags, in dB and linear peak (1.0 = full scale). */
data class ReplayGain(
    val trackGainDb: Float? = null,
    val trackPeak: Float? = null,
    val albumGainDb: Float? = null,
    val albumPeak: Float? = null,
) {
    val isEmpty: Boolean get() = trackGainDb == null && albumGainDb == null

    fun orElse(other: ReplayGain) = ReplayGain(
        trackGainDb ?: other.trackGainDb,
        trackPeak ?: other.trackPeak,
        albumGainDb ?: other.albumGainDb,
        albumPeak ?: other.albumPeak,
    )

    /**
     * Player volume (0..1) for [mode]. A missing album value falls back to the track value and
     * the other way round. The player can only turn volume down, so positive gains (quiet
     * masters) play at full volume; the peak keeps a boosted song from clipping.
     */
    fun volumeFactor(mode: ReplayGainMode): Float {
        val (gain, peak) = when (mode) {
            ReplayGainMode.OFF -> return 1f
            ReplayGainMode.TRACK -> if (trackGainDb != null) trackGainDb to trackPeak else albumGainDb to albumPeak
            ReplayGainMode.ALBUM -> if (albumGainDb != null) albumGainDb to albumPeak else trackGainDb to trackPeak
        }
        if (gain == null) return 1f
        var factor = 10f.pow(gain / 20f)
        if (peak != null && peak > 0f) factor = min(factor, 1f / peak)
        return factor.coerceIn(0f, 1f)
    }

    companion object {
        /** Opus R128 gains are relative to -23 LUFS; ReplayGain 2 uses -18 LUFS. */
        private const val R128_TO_REPLAYGAIN_DB = 5f

        private val number = Regex("""[-+]?\d+(?:[.,]\d+)?""")

        /**
         * Builds the values from tag text, keyed by upper-case tag name: `REPLAYGAIN_TRACK_GAIN`
         * ("-6.48 dB"), `REPLAYGAIN_TRACK_PEAK` ("0.988"), the album pair, and Opus
         * `R128_TRACK_GAIN` / `R128_ALBUM_GAIN` (Q7.8 integers).
         */
        fun fromTags(tags: Map<String, String>): ReplayGain {
            fun value(key: String): Float? =
                tags[key]?.let { number.find(it)?.value?.replace(',', '.')?.toFloatOrNull() }?.takeIf { it.isFinite() }

            fun r128(key: String): Float? =
                tags[key]?.trim()?.toIntOrNull()?.let { it / 256f + R128_TO_REPLAYGAIN_DB }

            return ReplayGain(
                trackGainDb = value("REPLAYGAIN_TRACK_GAIN") ?: r128("R128_TRACK_GAIN"),
                trackPeak = value("REPLAYGAIN_TRACK_PEAK")?.takeIf { it > 0f },
                albumGainDb = value("REPLAYGAIN_ALBUM_GAIN") ?: r128("R128_ALBUM_GAIN"),
                albumPeak = value("REPLAYGAIN_ALBUM_PEAK")?.takeIf { it > 0f },
            )
        }
    }
}
