package com.moigferdsrte.touy.item

import com.moigferdsrte.touy.SableTouy
import com.moigferdsrte.touy.core.TouyComponents
import com.moigferdsrte.touy.util.SubLevelTargeting
import com.moigferdsrte.touy.util.SubLevelTemplate
import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.component.TooltipDisplay
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import java.util.function.Consumer

class CloneGunItem(properties: Properties, descriptionLines: Int) : TouyItem(properties, descriptionLines) {
    override fun useOn(context: UseOnContext): InteractionResult {
        val player = context.player ?: return InteractionResult.PASS
        return use(context.level, player, context.hand)
    }

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        if (level !is ServerLevel) return InteractionResult.SUCCESS
        val stack = player.getItemInHand(hand)
        val hit = SubLevelTargeting.hit(level, player)
        if (player.isShiftKeyDown) {
            val target = hit?.let { Sable.HELPER.getContaining(level, it.blockPos) } as? ServerSubLevel
            if (target == null || hit == null) {
                stack.remove(TouyComponents.COPIED_PLOT)
                player.sendOverlayMessage(Component.translatable("touys.message.cleared"))
                return InteractionResult.SUCCESS
            }
            if (!SubLevelTargeting.canUse(level, player, hit.blockPos)) return InteractionResult.FAIL
            try {
                stack.set(TouyComponents.COPIED_PLOT, SubLevelTemplate.of(target, player))
                player.sendOverlayMessage(Component.translatable("touys.message.copy", target.name ?: "sub-level"))
                SableTouy.LOGGER.debug("Player {} copied sub-level {}", player.uuid, target.uniqueId)
            } catch (exception: IllegalArgumentException) {
                player.sendOverlayMessage(Component.translatable("touys.message.clone_too_large"))
                SableTouy.LOGGER.debug("Rejected clone of {}: {}", target.uniqueId, exception.message)
                return InteractionResult.FAIL
            } catch (exception: RuntimeException) {
                SableTouy.LOGGER.error("Unable to copy sub-level {} for player {}", target.uniqueId, player.uuid, exception)
                player.sendOverlayMessage(Component.translatable("touys.message.clone_failed"))
                return InteractionResult.FAIL
            }
        } else {
            val copied = stack.get(TouyComponents.COPIED_PLOT) ?: return InteractionResult.PASS
            if (hit == null || !SubLevelTargeting.canUse(level, player, hit.blockPos)) return InteractionResult.FAIL
            try {
                val pose = SubLevelTemplate.placementPose(level, player, hit, copied)
                val clone = SubLevelTemplate.create(level, pose, copied, player)
                SubLevelTargeting.invalidate(player)
                level.playSound(null, player.x, player.y, player.z, SoundEvents.ITEM_PICKUP,
                    SoundSource.PLAYERS, 1.0f, 1.0f)
                SableTouy.LOGGER.debug("Player {} placed clone {}", player.uuid, clone.uniqueId)
            } catch (exception: RuntimeException) {
                SableTouy.LOGGER.error("Unable to paste clone: player={}, dimension={}",
                    player.uuid, level.dimension().identifier(), exception)
                player.sendOverlayMessage(Component.translatable("touys.message.clone_failed"))
                return InteractionResult.FAIL
            }
        }
        return InteractionResult.SUCCESS
    }

    override fun appendHoverText(stack: ItemStack, context: TooltipContext, display: TooltipDisplay,
        lines: Consumer<Component>, flag: TooltipFlag
    ) {
        super.appendHoverText(stack, context, display, lines, flag)
        val copied = stack.get(TouyComponents.COPIED_PLOT) ?: return
        val name = copied.getStringOr("name", "").ifEmpty { "Sub-level" }
        lines.accept(Component.translatable("touys.tooltip.contains", Component.literal(name).withStyle(ChatFormatting.GREEN)))
    }
}
