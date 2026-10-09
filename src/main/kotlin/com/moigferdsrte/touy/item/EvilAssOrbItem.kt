package com.moigferdsrte.touy.item

import com.moigferdsrte.touy.SableTouy
import com.moigferdsrte.touy.util.SubLevelTargeting
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemUseAnimation
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level

class EvilAssOrbItem(properties: Properties, descriptionLines: Int) : TouyItem(properties, descriptionLines) {
    override fun getUseDuration(stack: ItemStack, entity: LivingEntity): Int = 72000

    override fun getUseAnimation(stack: ItemStack): ItemUseAnimation = ItemUseAnimation.NONE

    override fun useOn(context: UseOnContext): InteractionResult {
        val player = context.player ?: return InteractionResult.PASS
        return use(context.level, player, context.hand)
    }

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        if (player.isSpectator || !player.mayBuild()) return InteractionResult.FAIL
        player.startUsingItem(hand)
        if (level is ServerLevel) removeTarget(level, player)
        return InteractionResult.CONSUME
    }

    override fun onUseTick(level: Level, entity: LivingEntity, stack: ItemStack, remainingUseDuration: Int) {
        // Rate-limit repeated ray casts and removals while preserving the original hold-to-erase behavior.
        if (level is ServerLevel && entity is Player && remainingUseDuration % 5 == 0) removeTarget(level, entity)
    }

    private fun removeTarget(level: ServerLevel, player: Player) {
        val hit = SubLevelTargeting.hit(level, player) ?: return
        val target = SubLevelTargeting.target(level, player) ?: return
        if (!SubLevelTargeting.canUse(level, player, hit.blockPos)) return
        val container = SubLevelContainer.getContainer(level) ?: return
        val bounds = target.boundingBox()
        val centerX = (bounds.minX() + bounds.maxX()) * 0.5
        val centerY = (bounds.minY() + bounds.maxY()) * 0.5
        val centerZ = (bounds.minZ() + bounds.maxZ()) * 0.5
        level.sendParticles(ParticleTypes.FLAME, centerX, centerY, centerZ, 40,
            (bounds.maxX() - bounds.minX()).coerceAtMost(16.0) * 0.25,
            (bounds.maxY() - bounds.minY()).coerceAtMost(16.0) * 0.25,
            (bounds.maxZ() - bounds.minZ()).coerceAtMost(16.0) * 0.25, 0.0)
        container.removeSubLevel(target, SubLevelRemovalReason.REMOVED)
        SubLevelTargeting.invalidate(player)
        level.playSound(null, centerX, centerY, centerZ, SoundEvents.FIRECHARGE_USE,
            SoundSource.PLAYERS, 0.5f, 1.0f)
        SableTouy.LOGGER.debug("Player {} removed sub-level {} with orb", player.uuid, target.uniqueId)
    }
}
