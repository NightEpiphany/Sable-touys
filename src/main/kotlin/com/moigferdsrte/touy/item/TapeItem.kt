package com.moigferdsrte.touy.item

import com.moigferdsrte.touy.core.TouyComponents
import com.moigferdsrte.touy.core.TouyItems
import com.moigferdsrte.touy.core.recording.PoseDataCodec
import com.moigferdsrte.touy.core.recording.PoseRecording
import com.moigferdsrte.touy.util.SubLevelTargeting
import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.component.TooltipDisplay
import net.minecraft.world.level.Level
import java.util.function.Consumer

class TapeItem(properties: Properties, descriptionLines: Int) : TouyItem(properties, descriptionLines), ProjectableItem {
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        if (level !is ServerLevel) return InteractionResult.SUCCESS
        val stack = player.getItemInHand(hand)
        if (player.isShiftKeyDown) {
            val target = SubLevelTargeting.target(level, player)
            if (target == null) {
                stack.remove(TouyComponents.TRACKED_SUB_LEVEL)
                player.sendOverlayMessage(Component.translatable("touys.message.tracking_cleared"))
            } else {
                stack.set(TouyComponents.TRACKED_SUB_LEVEL, target.uniqueId)
                player.sendOverlayMessage(Component.translatable("touys.message.tracking_set", target.name ?: target.uniqueId.toString()))
            }
            return InteractionResult.SUCCESS_SERVER
        }
        val data = stack.get(TouyComponents.RECORDING)?.let(PoseDataCodec::decodeRecording) ?: return InteractionResult.FAIL
        val tracked = stack.get(TouyComponents.TRACKED_SUB_LEVEL)
        val target = if (tracked == null) SubLevelTargeting.target(level, player) else SubLevelContainer.getContainer(level)?.getSubLevel(tracked)
        if (target !is ServerSubLevel) {
            player.sendOverlayMessage(Component.translatable("touys.message.no_target"))
            return InteractionResult.FAIL
        }
        val started = RecordingService.startReplay(target, data)
        player.sendOverlayMessage(Component.translatable(if (started) "touys.message.replay_started" else "touys.message.replay_failed"))
        return if (started) InteractionResult.SUCCESS_SERVER else InteractionResult.FAIL
    }

    override fun project(level: ServerLevel, stack: ItemStack, hitPos: BlockPos?) {
        val data = stack.get(TouyComponents.RECORDING)?.let(PoseDataCodec::decodeRecording) ?: return
        val tracked = stack.get(TouyComponents.TRACKED_SUB_LEVEL)
        val target = if (tracked != null) SubLevelContainer.getContainer(level)?.getSubLevel(tracked)
            else if (hitPos != null) Sable.HELPER.getContaining(level, hitPos) else null
        if (target is ServerSubLevel) RecordingService.startReplay(target, data)
    }

    override fun requiresRayCast(stack: ItemStack): Boolean = !stack.has(TouyComponents.TRACKED_SUB_LEVEL)
    override fun isFoil(stack: ItemStack): Boolean = stack.has(TouyComponents.TRACKED_SUB_LEVEL)

    override fun appendHoverText(stack: ItemStack, context: TooltipContext, display: TooltipDisplay, lines: Consumer<Component>, flag: TooltipFlag) {
        val tag = stack.get(TouyComponents.RECORDING)
        if (tag != null) {
            val frames = tag.getIntOr("frames", 0).coerceIn(0, PoseRecording.MAX_FRAMES)
            lines.accept(Component.translatable("touys.tooltip.contains", Component.translatable("touys.tooltip.recording", frames / 20.0f).withStyle(ChatFormatting.GREEN)))
        }
        stack.get(TouyComponents.TRACKED_SUB_LEVEL)?.let { lines.accept(Component.translatable("touys.tooltip.tracking", it.toString()).withStyle(ChatFormatting.GRAY)) }
        super.appendHoverText(stack, context, display, lines, flag)
    }

    companion object {
        fun create(recording: PoseRecording): ItemStack = TouyItems.TAPE.defaultInstance.apply {
            set(TouyComponents.RECORDING, PoseDataCodec.encodeRecording(recording))
        }
    }
}
