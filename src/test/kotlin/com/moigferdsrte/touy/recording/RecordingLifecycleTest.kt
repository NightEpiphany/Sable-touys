package com.moigferdsrte.touy.recording

import com.moigferdsrte.touy.core.recording.RecordingExpiry
import com.moigferdsrte.touy.core.recording.ReplayCursor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RecordingLifecycleTest {
    @Test
    fun `unloaded target expires even if its camcorder remains held`() {
        val expiry = RecordingExpiry(0)
        for (tick in 1L..2L) {
            expiry.touch(tick)
            assertFalse(expiry.expired(false, tick, 3))
        }
        expiry.touch(3)
        assertTrue(expiry.expired(false, 3, 3))
    }

    @Test
    fun `target returning resets inactivity and orphaned camcorder expires`() {
        val expiry = RecordingExpiry(0)
        expiry.touch(1)
        assertFalse(expiry.expired(false, 1, 3))
        assertFalse(expiry.expired(true, 2, 3))
        assertEquals(0, expiry.inactiveTicks)
        assertFalse(expiry.expired(true, 3, 3))
        assertTrue(expiry.expired(true, 4, 3))
    }

    @Test
    fun `disabled inactivity never accumulates or overflows and reload resumes expiry`() {
        val expiry = RecordingExpiry(0)
        assertFalse(expiry.expired(false, 1, 3))
        assertEquals(1, expiry.inactiveTicks)
        // A very large elapsed time must not expire or overflow when -1 disables the limit.
        for (tick in listOf(2L, Int.MAX_VALUE.toLong(), Long.MAX_VALUE - 10)) {
            assertFalse(expiry.expired(false, tick, -1))
            assertEquals(0, expiry.inactiveTicks)
        }
        assertFalse(expiry.expired(false, Long.MAX_VALUE - 9, 3))
        assertFalse(expiry.expired(false, Long.MAX_VALUE - 8, 3))
        assertTrue(expiry.expired(false, Long.MAX_VALUE - 7, 3))
    }

    @Test
    fun `all replay frames are consumed once despite multiple physics substeps`() {
        val cursor = ReplayCursor(3)
        for (frame in 0..2) {
            assertEquals(frame, cursor.frame)
            repeat(4) { cursor.markApplied(); assertEquals(frame, cursor.frame) }
            assertEquals(frame == 2, cursor.advanceAfterPhysics())
        }
        assertTrue(cursor.finished)
        assertEquals(2, cursor.frame)
        assertTrue(cursor.advanceAfterPhysics())
    }

    @Test
    fun `paused physics does not consume frames and one frame releases only after application`() {
        val cursor = ReplayCursor(1)
        repeat(10) { assertFalse(cursor.advanceAfterPhysics()) }
        cursor.markApplied()
        assertTrue(cursor.advanceAfterPhysics())
        assertTrue(cursor.finished)
    }
}
