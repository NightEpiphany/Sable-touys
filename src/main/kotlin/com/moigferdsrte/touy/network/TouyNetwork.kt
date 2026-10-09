package com.moigferdsrte.touy.network

import com.moigferdsrte.touy.item.CameraItem
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking

/** Common registration deliberately has no references to client classes. */
object TouyNetwork {
    fun register() {
        PayloadTypeRegistry.serverboundPlay().register(SnapshotPayload.TYPE, SnapshotPayload.CODEC)
        ServerPlayNetworking.registerGlobalReceiver(SnapshotPayload.TYPE) { payload, context ->
            val player = context.player()
            CameraItem.capture(player.level(), player, payload.ids)
        }
    }
}
