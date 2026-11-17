package com.bsoft.compose.bmusic.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * In-memory artwork cache. Every list row used to decode its thumbnail again on each scroll;
 * now each image is decoded once and reused, and songs without art are remembered as misses.
 * Uses up to 1/8 of the app's heap.
 */
private object ArtworkCache {
    private val maxKb = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt().coerceAtLeast(4 * 1024)

    val images = object : LruCache<String, Bitmap>(maxKb) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    /** Keys known to have no artwork, so we do not ask MediaStore again. */
    val misses = LruCache<String, Boolean>(1_024)
}

object Util {
    /** Forgets cached artwork (and "no artwork" answers) for these URIs, e.g. after a tag edit. */
    fun invalidateArtwork(vararg uris: Uri?) {
        val prefixes = uris.filterNotNull().map { "$it|" }
        if (prefixes.isEmpty()) return
        ArtworkCache.images.snapshot().keys.filter { key -> prefixes.any(key::startsWith) }.forEach(ArtworkCache.images::remove)
        ArtworkCache.misses.snapshot().keys.filter { key -> prefixes.any(key::startsWith) }.forEach(ArtworkCache.misses::remove)
    }

    suspend fun loadArtwork(context: Context, artworkUri: Uri?, targetSize: Size): Bitmap? {
        val uri = artworkUri ?: return null
        val key = "$uri|$targetSize"
        ArtworkCache.images.get(key)?.let { return it }
        if (ArtworkCache.misses.get(key) == true) return null
        return withContext(Dispatchers.IO) {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(uri, targetSize, null)
                } else {
                    loadArtworkLegacy(context, uri, targetSize)
                }
                if (bitmap != null) ArtworkCache.images.put(key, bitmap) else ArtworkCache.misses.put(key, true)
                bitmap
            } catch (e: IOException) {
                // No embedded art or no album art: expected for many files, so not logged.
                ArtworkCache.misses.put(key, true)
                null
            } catch (e: RuntimeException) {
                // Unreadable or removed file (SecurityException, IllegalArgumentException...).
                ArtworkCache.misses.put(key, true)
                null
            }
        }
    }

    /**
     * Android 8 and 9 have no loadThumbnail. Album-art URIs are image files: decode them scaled
     * down to the target size (they used to be decoded at full size, often 3000 px and 36 MB).
     * Song URIs are audio files: read the embedded picture and scale it the same way.
     */
    private fun loadArtworkLegacy(context: Context, uri: Uri, targetSize: Size): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            val options = BitmapFactory.Options().apply {
                inSampleSize = artworkSampleSize(bounds.outWidth, bounds.outHeight, targetSize.width, targetSize.height)
            }
            return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
        // Not an image: an audio file, so look for a picture in its tags.
        val picture = MediaMetadataRetriever().run {
            try {
                setDataSource(context, uri)
                embeddedPicture
            } finally {
                release()
            }
        } ?: return null
        val pictureBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(picture, 0, picture.size, pictureBounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = artworkSampleSize(pictureBounds.outWidth, pictureBounds.outHeight, targetSize.width, targetSize.height)
        }
        return BitmapFactory.decodeByteArray(picture, 0, picture.size, options)
    }

    /*suspend fun getAudioArtwork(context: Context, trackId: Long, albumId: Long, targetSize: Size): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            loadAlbumArt(context, trackId, targetSize)
        } else {
            withContext(Dispatchers.IO) {
                try {
                    val legacyArtUri = "content://media/external/audio/albumart".toUri()
                    val artworkUri = ContentUris.withAppendedId(legacyArtUri, albumId)
                    context.contentResolver.openInputStream(artworkUri).use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }*/
}