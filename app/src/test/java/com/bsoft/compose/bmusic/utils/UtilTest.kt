package com.bsoft.compose.bmusic.utils

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Size
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * JVM tests run with Build.VERSION.SDK_INT = 0, so these cover the Android 8/9 path: album art
 * decoded scaled down, and song art read from the file's embedded picture.
 */
@ExperimentalCoroutinesApi
class UtilTest {

    private val context: Context = mockk()
    private val contentResolver: ContentResolver = mockk()
    private val size: Size = mockk()

    @Before
    fun setUp() {
        mockkStatic(BitmapFactory::class)
        every { context.contentResolver } returns contentResolver
        every { size.width } returns 160
        every { size.height } returns 160
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /** Makes the bounds-only decode report [width] x [height], and the real decode return [bitmap]. */
    private fun stubImageDecode(width: Int, height: Int, bitmap: Bitmap?, sampleSizes: MutableList<Int>) {
        val options = slot<BitmapFactory.Options>()
        every { BitmapFactory.decodeStream(any(), any(), capture(options)) } answers {
            val o = options.captured
            if (o.inJustDecodeBounds) {
                o.outWidth = width
                o.outHeight = height
                null
            } else {
                sampleSizes += o.inSampleSize
                bitmap
            }
        }
    }

    @Test
    fun `loadArtwork should return null if uri is null`() = runTest {
        val result = Util.loadArtwork(context, null, size)
        assertNull(result)
    }

    @Test
    fun `album art is decoded scaled down to the target size`() = runTest {
        val uri: Uri = mockk()
        val bitmap: Bitmap = mockk()
        val sampleSizes = mutableListOf<Int>()
        every { contentResolver.openInputStream(any()) } answers { mockk(relaxed = true) }
        stubImageDecode(width = 3000, height = 3000, bitmap = bitmap, sampleSizes = sampleSizes)

        val result = Util.loadArtwork(context, uri, size)

        assertEquals(bitmap, result)
        assertEquals(listOf(16), sampleSizes)
    }

    @Test
    fun `song art comes from the embedded picture when the uri is not an image`() = runTest {
        val uri: Uri = mockk()
        val bitmap: Bitmap = mockk()
        val picture = byteArrayOf(1, 2, 3)
        every { contentResolver.openInputStream(any()) } answers { mockk(relaxed = true) }
        // Bounds decode finds no image (an audio file).
        stubImageDecode(width = 0, height = 0, bitmap = null, sampleSizes = mutableListOf())
        mockkConstructor(MediaMetadataRetriever::class)
        every { anyConstructed<MediaMetadataRetriever>().setDataSource(any<Context>(), any<Uri>()) } just Runs
        every { anyConstructed<MediaMetadataRetriever>().embeddedPicture } returns picture
        every { anyConstructed<MediaMetadataRetriever>().release() } just Runs
        val options = slot<BitmapFactory.Options>()
        every { BitmapFactory.decodeByteArray(picture, 0, picture.size, capture(options)) } answers {
            if (options.captured.inJustDecodeBounds) {
                options.captured.outWidth = 640
                options.captured.outHeight = 640
                null
            } else {
                assertEquals(4, options.captured.inSampleSize)
                bitmap
            }
        }

        val result = Util.loadArtwork(context, uri, size)

        assertEquals(bitmap, result)
    }

    @Test
    fun `loadArtwork should return null on IOException`() = runTest {
        val uri: Uri = mockk()
        every { contentResolver.loadThumbnail(any(), any(), any()) } throws IOException()
        every { contentResolver.openInputStream(any()) } throws IOException()

        val result = Util.loadArtwork(context, uri, size)
        assertNull(result)
    }

    @Test
    fun `an unreadable file counts as no artwork`() = runTest {
        val uri: Uri = mockk()
        every { contentResolver.openInputStream(any()) } throws SecurityException("no access")

        val result = Util.loadArtwork(context, uri, size)
        assertNull(result)
    }
}
