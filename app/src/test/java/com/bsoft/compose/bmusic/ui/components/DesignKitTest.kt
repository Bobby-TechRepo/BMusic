package com.bsoft.compose.bmusic.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class DesignKitTest {
    @Test
    fun `knob rotation takes the short way round`() {
        assertEquals(10f, rotationDelta(100f, 110f))
        assertEquals(-10f, rotationDelta(110f, 100f))
        // Crossing 3 o'clock (0°/360°) in either direction.
        assertEquals(20f, rotationDelta(350f, 10f))
        assertEquals(-20f, rotationDelta(10f, 350f))
    }

    @Test
    fun `highlight marks the matched part ignoring case`() {
        val text = highlightMatch("Hello World", "world", Color.Red)
        assertEquals("Hello World", text.text)
        assertEquals(1, text.spanStyles.size)
        assertEquals(6, text.spanStyles.single().start)
        assertEquals(11, text.spanStyles.single().end)
    }

    @Test
    fun `no match or empty query leaves the text plain`() {
        assertEquals(0, highlightMatch("Hello", "xyz", Color.Red).spanStyles.size)
        assertEquals(0, highlightMatch("Hello", "  ", Color.Red).spanStyles.size)
    }
}
