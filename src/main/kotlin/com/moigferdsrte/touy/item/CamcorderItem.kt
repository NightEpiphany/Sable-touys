package com.moigferdsrte.touy.item

import com.moigferdsrte.touy.core.TouyComponents
import com.moigferdsrte.touy.util.SubLevelTargeting
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import java.util.UUID

class CamcorderItem(properties: Properties, descriptionLines: Int) : TouyItem(properties, descriptionLines) {
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        if (level !is ServerLevel) return InteractionResult.SUCCESS
        val stack = player.getItemInHand(hand)
        val tracked = stack.get(TouyComponents.TRACKED_SUB_LEVEL)
        val metadata = stack.get(TouyComponents.RECORDING)
        if (tracked != null) {
            val token = metadata?.let(::token)
            val frames = if (token == null || metadata == null) null else RecordingService.stop(
                level, tracked, player.uuid, token, metadata.getStringOr("session_dimension", RecordingService.dimension(level)), !player.isShiftKeyDown,
            )
            stack.remove(TouyComponents.TRACKED_SUB_LEVEL)
            stack.remove(TouyComponents.RECORDING)
            stack.remove(TouyComponents.RECORDING_TICKS)
            val key = when {
                player.isShiftKeyDown -> "touys.message.recording_discarded"
                frames == null -> "touys.message.recording_unavailable"
                else -> {
                    give(level, player, TapeItem.create(frames))
                    "touys.message.recording_saved"
                }
            }
            player.sendOverlayMessage(Component.translatable(key))
            return InteractionResult.SUCCESS_SERVER
        }
        if (player.isShiftKeyDown) return InteractionResult.PASS
        val target = SubLevelTargeting.target(level, player) as? ServerSubLevel ?: run {
            player.sendOverlayMessage(Component.translatable("touys.message.no_target"))
            return InteractionResult.FAIL
        }
        val token = RecordingService.start(level, target, player.uuid) ?: run {
            player.sendOverlayMessage(Component.translatable("touys.message.recording_busy"))
            return InteractionResult.FAIL
        }
        stack.set(TouyComponents.TRACKED_SUB_LEVEL, target.uniqueId)
        stack.set(TouyComponents.RECORDING_TICKS, 1)
        stack.set(TouyComponents.RECORDING, CompoundTag().apply {
            putString("session_dimension", RecordingService.dimension(level))
            putLong("session_most", token.mostSignificantBits)
            putLong("session_least", token.leastSignificantBits)
        })
        player.sendOverlayMessage(Component.translatable("touys.message.recording_started"))
        return InteractionResult.SUCCESS_SERVER
    }

    override fun onDestroyed(entity: ItemEntity) {
        val level = entity.level() as? ServerLevel
        val stack = entity.item
        val tracked = stack.get(TouyComponents.TRACKED_SUB_LEVEL)
        val metadata = stack.get(TouyComponents.RECORDING)
        val token = metadata?.let(::token)
        if (level != null && tracked != null && token != null && metadata != null) {
            RecordingService.stop(level, tracked, null, token, metadata.getStringOr("session_dimension", RecordingService.dimension(level)), false)
        }
        super.onDestroyed(entity)
    }

    override fun inventoryTick(stack: ItemStack, level: ServerLevel, entity: Entity, slot: EquipmentSlot?) {
        super.inventoryTick(stack, level, entity, slot)
        if (entity.tickCount % 20 != 0) return
        val tracked = stack.get(TouyComponents.TRACKED_SUB_LEVEL) ?: return
        val metadata = stack.get(TouyComponents.RECORDING)
        val token = metadata?.let(::token)
        if (token == null || metadata == null || !RecordingService.touch(level, tracked, token, metadata.getStringOr("session_dimension", RecordingService.dimension(level)))) {
            stack.remove(TouyComponents.TRACKED_SUB_LEVEL)
            stack.remove(TouyComponents.RECORDING)
            stack.remove(TouyComponents.RECORDING_TICKS)
        } else {
            val ticks = RecordingService.recordingTicks(level, tracked, token, metadata.getStringOr("session_dimension", RecordingService.dimension(level)))
            if (ticks != null) stack.set(TouyComponents.RECORDING_TICKS, ticks)
        }
    }

    override fun isFoil(stack: ItemStack): Boolean = stack.has(TouyComponents.TRACKED_SUB_LEVEL)

    companion object {
        private fun token(tag: CompoundTag): UUID? {
            val most = tag.getLong("session_most").orElse(null) ?: return null
            val least = tag.getLong("session_least").orElse(null) ?: return null
            return UUID(most, least)
        }

        internal fun give(level: ServerLevel, player: Player, stack: ItemStack) {
            if (!player.addItem(stack) && !stack.isEmpty) player.spawnAtLocation(level, stack)
        }
    }
}
