package com.moigferdsrte.touy.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SelectionLimitsTest {
    @Test
    fun `single block and reversed corners are inclusive`() {
        assertEquals(1L, SelectionLimits.volume(7, -4, 2, 7, -4, 2, 1))
        assertEquals(24L, SelectionLimits.volume(4, 3, 2, 1, 1, 1, 24))
        assertEquals(24L, SelectionLimits.volume(1, 1, 1, 4, 3, 2, 24))
    }

    @Test
    fun `rejects over limit before multiplication or allocation`() {
        assertEquals(-1L, SelectionLimits.volume(0, 0, 0, 63, 63, 16, 65536))
        assertEquals(65536L, SelectionLimits.volume(0, 0, 0, 63, 63, 15, 65536))
        assertEquals(-1L, SelectionLimits.volume(Int.MIN_VALUE, Int.MIN_VALUE, Int.MIN_VALUE,
            Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(-1L, SelectionLimits.volume(0, 0, 0, 0, 0, 0, 0))
    }
}
