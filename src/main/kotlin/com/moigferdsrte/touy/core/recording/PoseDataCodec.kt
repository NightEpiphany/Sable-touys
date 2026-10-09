package com.moigferdsrte.touy.core.recording

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import java.util.UUID

/** Explicit versioning and strict bounds keep saved/networked components safe to replay. */
object PoseDataCodec {
    private const val VERSION = 1
    const val MAX_SNAPSHOT_TARGETS = 128

    fun encodeRecording(recording: PoseRecording): CompoundTag = CompoundTag().apply {
        putInt("version", VERSION)
        putInt("frames", recording.frameCount)
        putLongArray("poses", recording.toBits())
    }

    fun decodeRecording(tag: CompoundTag): PoseRecording? {
        if (tag.getIntOr("version", 0) != VERSION) return null
        val count = tag.getIntOr("frames", 0)
        if (count !in 1..PoseRecording.MAX_FRAMES) return null
        val bits = tag.getLongArray("poses").orElse(null) ?: return null
        if (bits.size != count * PoseRecording.COMPONENTS_PER_POSE) return null
        return PoseRecording.fromBits(bits)
    }

    fun encodeSnapshot(dimension: String, entries: Map<UUID, PoseRecording>): CompoundTag {
        require(entries.size in 1..MAX_SNAPSHOT_TARGETS && entries.values.all { it.frameCount == 1 })
        val list = ListTag()
        entries.forEach { (id, pose) -> list.add(CompoundTag().apply {
            putLong("uuid_most", id.mostSignificantBits)
            putLong("uuid_least", id.leastSignificantBits)
            putLongArray("pose", pose.toBits())
        }) }
        return CompoundTag().apply {
            putInt("version", VERSION)
            putString("dimension", dimension)
            put("entries", list)
        }
    }

    fun decodeSnapshot(tag: CompoundTag): Map<UUID, PoseRecording>? {
        if (tag.getIntOr("version", 0) != VERSION) return null
        val list = tag.getList("entries").orElse(null) ?: return null
        if (list.size !in 1..MAX_SNAPSHOT_TARGETS) return null
        val result = LinkedHashMap<UUID, PoseRecording>(list.size)
        for (raw in list) {
            val entry = raw as? CompoundTag ?: return null
            val most = entry.getLong("uuid_most").orElse(null) ?: return null
            val least = entry.getLong("uuid_least").orElse(null) ?: return null
            val pose = PoseRecording.fromBits(entry.getLongArray("pose").orElse(null) ?: return null) ?: return null
            if (pose.frameCount != 1 || result.put(UUID(most, least), pose) != null) return null
        }
        return result
    }

    fun snapshotSize(tag: CompoundTag): Int = tag.getList("entries").map { it.size }.orElse(0).coerceAtMost(MAX_SNAPSHOT_TARGETS)
}
