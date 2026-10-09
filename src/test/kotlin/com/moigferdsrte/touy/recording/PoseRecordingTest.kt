package com.moigferdsrte.touy.recording

import com.moigferdsrte.touy.core.recording.PoseRecording
import com.moigferdsrte.touy.core.recording.PrimitivePoseBuffer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PoseRecordingTest {
    @Test
    fun `capacity bounds storage and preserves every recorded frame in order`() {
        val buffer = PrimitivePoseBuffer(2)
        assertTrue(buffer.append(1.0, 2.0, 3.0, 0.0, 0.0, 0.0, 1.0))
        assertTrue(buffer.append(4.0, 5.0, 6.0, 0.0, 0.0, 0.0, 1.0))
        assertFalse(buffer.append(7.0, 8.0, 9.0, 0.0, 0.0, 0.0, 1.0))
        assertTrue(buffer.isFull)
        val recording = buffer.snapshot()!!
        assertEquals(2, recording.frameCount)
        assertEquals(1.0, recording.component(0, 0))
        assertEquals(4.0, recording.component(1, 0))
    }

    @Test
    fun `invalid data does not occupy a frame and quaternion is normalized`() {
        val buffer = PrimitivePoseBuffer(1)
        assertFalse(buffer.append(Double.NaN, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0))
        assertFalse(buffer.append(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0))
        assertFalse(buffer.append(30_000_001.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0))
        assertEquals(0, buffer.size)
        assertTrue(buffer.append(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 2.0))
        assertEquals(1.0, buffer.snapshot()!!.component(0, 6))
    }

    @Test
    fun `snapshots are independent of later ticks and exported arrays`() {
        val buffer = PrimitivePoseBuffer(2)
        buffer.append(1.0, 2.0, 3.0, 0.0, 0.0, 0.0, 1.0)
        val first = buffer.snapshot()!!
        buffer.append(4.0, 5.0, 6.0, 0.0, 0.0, 0.0, 1.0)
        val bits = first.toBits()
        bits[0] = 50.0.toRawBits()
        assertEquals(1, first.frameCount)
        assertEquals(1.0, first.component(0, 0))
        assertEquals(2, buffer.snapshot()!!.frameCount)
    }

    @Test
    fun `primitive serialization roundtrips signed zero and noninteger positions`() {
        val buffer = PrimitivePoseBuffer(1)
        buffer.append(-0.0, -2.25, 3.75, 0.0, 0.0, 0.0, 1.0)
        val original = buffer.snapshot()!!
        val restored = PoseRecording.fromBits(original.toBits())!!
        assertArrayEquals(original.toBits(), restored.toBits())
    }

    @Test
    fun `malformed and oversized recordings are rejected before replay`() {
        assertNull(PoseRecording.fromBits(longArrayOf()))
        assertNull(PoseRecording.fromBits(LongArray(8)))
        assertNull(PoseRecording.fromBits(LongArray((PoseRecording.MAX_FRAMES + 1) * 7)))
        assertNull(PoseRecording.fromBits(longArrayOf(0, 0, 0, 0, 0, 0, Double.POSITIVE_INFINITY.toRawBits())))
        assertThrows(IllegalArgumentException::class.java) { PrimitivePoseBuffer(0) }
        assertThrows(IllegalArgumentException::class.java) { PrimitivePoseBuffer(PoseRecording.MAX_FRAMES + 1) }
    }
}
