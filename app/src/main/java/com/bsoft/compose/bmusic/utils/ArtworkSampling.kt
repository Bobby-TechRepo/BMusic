package com.bsoft.compose.bmusic.utils

/**
 * The largest power-of-two `inSampleSize` that still leaves the image at least as big as the
 * target on both sides. A 3000×3000 cover shown at 160×160 decodes at 187×187 (sample 16)
 * instead of 3000×3000: about 36 MB less memory per image.
 */
fun artworkSampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
    if (width <= 0 || height <= 0 || targetWidth <= 0 || targetHeight <= 0) return 1
    var sample = 1
    while (width / (sample * 2) >= targetWidth && height / (sample * 2) >= targetHeight) {
        sample *= 2
    }
    return sample
}
