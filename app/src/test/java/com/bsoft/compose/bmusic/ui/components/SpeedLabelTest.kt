package com.bsoft.compose.bmusic.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedLabelTest {
    @Test
    fun `whole speeds have no decimals`() {
        assertEquals("1×", speedLabel(1f))
        assertEquals("2×", speedLabel(2f))
    }

    @Test
    fun `fractional speeds drop trailing zeros`() {
        assertEquals("0.5×", speedLabel(0.5f))
        assertEquals("1.25×", speedLabel(1.25f))
        assertEquals("1.5×", speedLabel(1.5f))
    }

    @Test
    fun `speeds are rounded to two decimals`() {
        assertEquals("1.33×", speedLabel(1.3333f))
    }
}
