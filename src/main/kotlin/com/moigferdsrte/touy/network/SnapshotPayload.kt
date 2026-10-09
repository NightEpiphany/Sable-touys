package com.moigferdsrte.touy.network

import com.moigferdsrte.touy.SableTouy
import com.moigferdsrte.touy.item.CameraItem
import io.netty.handler.codec.DecoderException
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.util.UUID

/** The client sends identifiers only; the server owns and validates every captured pose. */
class SnapshotPayload(ids: List<UUID>) : CustomPacketPayload {
    val ids: List<UUID> = java.util.List.copyOf(ids)

    init {
        require(ids.size in 1..CameraItem.MAX_CAPTURE_TARGETS)
    }

    override fun type(): CustomPacketPayload.Type<SnapshotPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<SnapshotPayload>(SableTouy.id("to_server/sub_level_snapshot"))
        val CODEC: StreamCodec<FriendlyByteBuf, SnapshotPayload> = object : StreamCodec<FriendlyByteBuf, SnapshotPayload> {
            override fun decode(buffer: FriendlyByteBuf): SnapshotPayload {
                val count = buffer.readVarInt()
                if (count !in 1..CameraItem.MAX_CAPTURE_TARGETS) {
                    throw DecoderException("Invalid Touys snapshot target count: $count")
                }
                val ids = ArrayList<UUID>(count)
                repeat(count) { ids.add(buffer.readUUID()) }
                return SnapshotPayload(ids)
            }

            override fun encode(buffer: FriendlyByteBuf, value: SnapshotPayload) {
                buffer.writeVarInt(value.ids.size)
                value.ids.forEach(buffer::writeUUID)
            }
        }
    }
}
