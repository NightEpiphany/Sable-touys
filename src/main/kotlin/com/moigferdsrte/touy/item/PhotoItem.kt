package com.moigferdsrte.touy.item

import com.moigferdsrte.touy.core.TouyComponents
import com.moigferdsrte.touy.core.TouyItems
import com.moigferdsrte.touy.core.recording.PoseDataCodec
import com.moigferdsrte.touy.core.recording.PoseRecording
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer
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
import org.joml.Quaterniond
import org.joml.Vector3d
import java.util.UUID
import java.util.function.Consumer

class PhotoItem(properties: Properties, descriptionLines: Int) : TouyItem(properties, descriptionLines), ProjectableItem {
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        if (level !is ServerLevel) return InteractionResult.SUCCESS
        val restored = activate(level, player.getItemInHand(hand))
        if (restored > 0) player.sendOverlayMessage(Component.translatable("touys.message.snapshot_restored", restored))
        return if (restored > 0) InteractionResult.SUCCESS_SERVER else InteractionResult.FAIL
    }

    override fun project(level: ServerLevel, stack: ItemStack, hitPos: BlockPos?) {
        activate(level, stack)
    }

    override fun appendHoverText(stack: ItemStack, context: TooltipContext, display: TooltipDisplay, lines: Consumer<Component>, flag: TooltipFlag) {
        stack.get(TouyComponents.SNAPSHOT)?.let { tag ->
            val count = PoseDataCodec.snapshotSize(tag)
            lines.accept(Component.translatable("touys.tooltip.contains", Component.translatable("touys.tooltip.snapshot", count, if (count == 1) "" else "s").withStyle(ChatFormatting.GREEN)))
        }
        super.appendHoverText(stack, context, display, lines, flag)
    }

    companion object {
        fun create(dimension: String, snapshot: Map<UUID, PoseRecording>): ItemStack = TouyItems.PHOTO.defaultInstance.apply {
            set(TouyComponents.SNAPSHOT, PoseDataCodec.encodeSnapshot(dimension, snapshot))
        }

        fun activate(level: ServerLevel, stack: ItemStack): Int {
            val tag = stack.get(TouyComponents.SNAPSHOT) ?: return 0
            if (tag.getStringOr("dimension", "") != RecordingService.dimension(level)) return 0
            val snapshots = PoseDataCodec.decodeSnapshot(tag) ?: return 0
            val container = SubLevelContainer.getContainer(level) as? ServerSubLevelContainer ?: return 0
            val pipeline = container.physicsSystem().pipeline
            val position = Vector3d()
            val orientation = Quaterniond()
            val linearVelocity = Vector3d()
            val angularVelocity = Vector3d()
            var restored = 0
            for ((id, pose) in snapshots) {
                val target = container.getSubLevel(id) as? ServerSubLevel ?: continue
                if (target.isRemoved) continue
                RecordingService.stopReplay(target)
                position.set(pose.component(0, 0), pose.component(0, 1), pose.component(0, 2))
                orientation.set(pose.component(0, 3), pose.component(0, 4), pose.component(0, 5), pose.component(0, 6))
                pipeline.getLinearVelocity(target, linearVelocity).negate()
                pipeline.getAngularVelocity(target, angularVelocity).negate()
                pipeline.addLinearAndAngularVelocity(target, linearVelocity, angularVelocity)
                pipeline.teleport(target, position, orientation)
                pipeline.wakeUp(target)
                restored++
            }
            return restored
        }
    }
}
