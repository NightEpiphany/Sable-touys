package com.moigferdsrte.touy.recording

import com.moigferdsrte.touy.core.recording.PoseDataCodec
import com.moigferdsrte.touy.core.recording.PoseRecording
import com.moigferdsrte.touy.core.recording.PrimitivePoseBuffer
import io.netty.buffer.Unpooled
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.codec.ByteBufCodecs
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class PoseDataCodecTest {
    private fun pose(x: Double) = PrimitivePoseBuffer(1).apply { append(x, 2.0, 3.0, 0.0, 0.0, 0.0, 1.0) }.snapshot()!!

    @Test
    fun `NBT recording roundtrips all frames and checks declared count`() {
        val original = pose(10.25)
        val tag = PoseDataCodec.encodeRecording(original)
        assertArrayEquals(original.toBits(), PoseDataCodec.decodeRecording(tag)!!.toBits())
        tag.putInt("frames", 2)
        assertNull(PoseDataCodec.decodeRecording(tag))
        tag.putInt("version", 100)
        assertNull(PoseDataCodec.decodeRecording(tag))
    }

    @Test
    fun `maximum tape fits and roundtrips the actual bounded component network codec`() {
        val poses = PrimitivePoseBuffer(PoseRecording.MAX_FRAMES)
        repeat(PoseRecording.MAX_FRAMES) { frame ->
            check(poses.append(frame.toDouble(), 2.0, 3.0, 0.0, 0.0, 0.0, 1.0))
        }
        val original = requireNotNull(poses.snapshot())
        val bytes = Unpooled.buffer()
        try {
            ByteBufCodecs.COMPOUND_TAG.encode(bytes, PoseDataCodec.encodeRecording(original))
            assertTrue(bytes.readableBytes() < 2 * 1024 * 1024)
            val decoded = PoseDataCodec.decodeRecording(ByteBufCodecs.COMPOUND_TAG.decode(bytes))
            assertNotNull(decoded)
            assertEquals(PoseRecording.MAX_FRAMES, decoded!!.frameCount)
            assertArrayEquals(original.toBits(), decoded.toBits())
            assertFalse(bytes.isReadable)
        } finally {
            bytes.release()
        }
    }

    @Test
    fun `snapshot preserves multiple UUIDs and their own poses`() {
        val a = UUID(1L, 2L)
        val b = UUID(3L, 4L)
        val tag = PoseDataCodec.encodeSnapshot("minecraft:overworld", linkedMapOf(a to pose(10.0), b to pose(20.0)))
        val snapshot = PoseDataCodec.decodeSnapshot(tag)!!
        assertEquals("minecraft:overworld", tag.getStringOr("dimension", ""))
        assertEquals(2, snapshot.size)
        assertEquals(10.0, snapshot[a]!!.component(0, 0))
        assertEquals(20.0, snapshot[b]!!.component(0, 0))
    }

    @Test
    fun `duplicate and malformed snapshots are rejected atomically`() {
        val tag = PoseDataCodec.encodeSnapshot("minecraft:overworld", mapOf(UUID(1L, 2L) to pose(10.0)))
        val entries = tag.getListOrEmpty("entries")
        entries.add(entries[0].copy())
        assertNull(PoseDataCodec.decodeSnapshot(tag))
        entries.removeAt(1)
        (entries[0] as CompoundTag).putLongArray("pose", longArrayOf())
        assertNull(PoseDataCodec.decodeSnapshot(tag))
        assertNull(PoseDataCodec.decodeSnapshot(CompoundTag()))
    }
}
