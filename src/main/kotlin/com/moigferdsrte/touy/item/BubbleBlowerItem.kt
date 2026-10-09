package com.moigferdsrte.touy.item

import com.moigferdsrte.touy.SableTouy
import com.moigferdsrte.touy.core.TouyComponents
import com.moigferdsrte.touy.util.SelectionLimits
import com.moigferdsrte.touy.util.SubLevelTargeting
import com.moigferdsrte.touy.util.TouyConfig
import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.companion.math.BoundingBox3i
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.minecraft.world.phys.BlockHitResult

class BubbleBlowerItem(properties: Properties, descriptionLines: Int) : TouyItem(properties, descriptionLines) {
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        if (level !is ServerLevel) return InteractionResult.SUCCESS
        return select(level, player, player.getItemInHand(hand), SubLevelTargeting.hit(level, player))
    }

    override fun useOn(context: UseOnContext): InteractionResult {
        val player = context.player ?: return InteractionResult.PASS
        val level = context.level
        if (level !is ServerLevel) return InteractionResult.SUCCESS
        // Recompute the target on the server rather than trusting the client's use-on location.
        return select(level, player, context.itemInHand, SubLevelTargeting.hit(level, player))
    }

    private fun select(level: ServerLevel, player: Player, stack: ItemStack, hit: BlockHitResult?): InteractionResult {
        if (player.isShiftKeyDown) {
            clearSelection(stack)
            player.sendOverlayMessage(Component.translatable("touys.message.selection_discarded"))
            sound(level, player, 0.5f)
            return InteractionResult.SUCCESS
        }
        val end = hit?.blockPos ?: return InteractionResult.PASS
        if (!SubLevelTargeting.withinRange(level, player, end) || !SubLevelTargeting.canUse(level, player, end)) {
            return InteractionResult.FAIL
        }
        val container = SubLevelContainer.getContainer(level) ?: return InteractionResult.FAIL
        val endGrid = Sable.HELPER.getContaining(level, end)
        val start = stack.get(TouyComponents.ASSEMBLY_START)
        if (start == null) {
            stack.set(TouyComponents.ASSEMBLY_START, end.immutable())
            stack.set(TouyComponents.ASSEMBLY_DIMENSION, level.dimension().identifier().toString())
            if (endGrid == null) stack.remove(TouyComponents.ASSEMBLY_SUB_LEVEL)
            else stack.set(TouyComponents.ASSEMBLY_SUB_LEVEL, endGrid.uniqueId)
            player.sendOverlayMessage(Component.translatable("touys.message.selection_started"))
            sound(level, player, 0.75f)
            return InteractionResult.SUCCESS
        }

        val dimension = stack.get(TouyComponents.ASSEMBLY_DIMENSION)
        val expectedGrid = stack.get(TouyComponents.ASSEMBLY_SUB_LEVEL)
        val startGrid = Sable.HELPER.getContaining(level, start)
        if (dimension != level.dimension().identifier().toString() ||
            startGrid !== endGrid || startGrid?.uniqueId != expectedGrid ||
            startGrid?.isRemoved == true ||
            (expectedGrid == null && Sable.HELPER.isInPlotGrid(level, start))
        ) return fail(player, "invalid_selection")

        val count = SelectionLimits.volume(start.x, start.y, start.z, end.x, end.y, end.z,
            TouyConfig.assemblyMaxBlocks.toLong())
        if (count < 1) return fail(player, "selection_too_large", TouyConfig.assemblyMaxBlocks)
        if (!SubLevelTargeting.withinRange(level, player, start)) return fail(player, "selection_out_of_range")
        val plotCount = 1 shl (container.logSideLength * 2)
        if (container.occupancy.nextClearBit(0) >= plotCount) return fail(player, "no_plot_space")

        val bounds = BoundingBox.fromCorners(start, end)
        val blocks = ArrayList<BlockPos>(count.toInt())
        val cursor = BlockPos.MutableBlockPos()
        // Validate the entire selection before moving its first block. The cap bounds work and memory.
        for (x in bounds.minX()..bounds.maxX()) {
            for (y in bounds.minY()..bounds.maxY()) {
                for (z in bounds.minZ()..bounds.maxZ()) {
                    cursor.set(x, y, z)
                    if (!level.isLoaded(cursor) || Sable.HELPER.getContaining(level, cursor) !== startGrid ||
                        !SubLevelTargeting.withinRange(level, player, cursor) ||
                        !SubLevelTargeting.canUse(level, player, cursor)
                    ) return fail(player, "invalid_selection")
                    val state = level.getBlockState(cursor)
                    if (!state.isAir) {
                        if (state.getDestroySpeed(level, cursor) < 0 && !player.isCreative) {
                            return fail(player, "protected_block")
                        }
                        blocks.add(cursor.immutable())
                    }
                }
            }
        }
        if (blocks.isEmpty()) return fail(player, "empty_selection")
        try {
            val subLevel = SubLevelAssemblyHelper.assembleBlocks(level, blocks.first(), blocks, BoundingBox3i(bounds))
            clearSelection(stack)
            SubLevelTargeting.invalidate(player)
            player.sendOverlayMessage(Component.translatable("touys.message.assembled", blocks.size))
            sound(level, player, 1.5f)
            SableTouy.LOGGER.debug("Player {} assembled {} blocks into {}", player.uuid, blocks.size, subLevel.uniqueId)
            return InteractionResult.SUCCESS
        } catch (exception: RuntimeException) {
            SableTouy.LOGGER.error("Touys assembly failed: player={}, dimension={}, start={}, end={}, blocks={}",
                player.uuid, level.dimension().identifier(), start, end, blocks.size, exception)
            return fail(player, "assembly_failed")
        }
    }

    private fun fail(player: Player, key: String, vararg args: Any): InteractionResult {
        player.sendOverlayMessage(Component.translatable("touys.message.$key", *args))
        return InteractionResult.FAIL
    }

    private fun sound(level: ServerLevel, player: Player, pitch: Float) {
        level.playSound(null, player.x, player.y, player.z, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP,
            SoundSource.PLAYERS, 1.0f, pitch)
    }

    companion object {
        fun inSameGrid(level: Level, first: BlockPos, second: BlockPos): Boolean =
            Sable.HELPER.getContaining(level, first) === Sable.HELPER.getContaining(level, second)

        fun clearSelection(stack: ItemStack) {
            stack.remove(TouyComponents.ASSEMBLY_START)
            stack.remove(TouyComponents.ASSEMBLY_DIMENSION)
            stack.remove(TouyComponents.ASSEMBLY_SUB_LEVEL)
        }
    }
}
