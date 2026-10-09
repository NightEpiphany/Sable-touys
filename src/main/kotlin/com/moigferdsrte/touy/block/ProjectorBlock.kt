package com.moigferdsrte.touy.block

import com.moigferdsrte.touy.item.ProjectableItem
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.DirectionalBlock
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.redstone.Orientation
import net.minecraft.world.phys.BlockHitResult

class ProjectorBlock(properties: Properties) : DirectionalBlock(properties), EntityBlock {
    companion object {
        val POWERED: BooleanProperty = BooleanProperty.create("powered")
        val TAPE: BooleanProperty = BooleanProperty.create("tape")
    }

    init {
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP)
            .setValue(POWERED, false).setValue(TAPE, false))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING, POWERED, TAPE)
    }

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState = defaultBlockState()
        .setValue(FACING, ctx.nearestLookingDirection.opposite)
        .setValue(POWERED, ctx.level.hasNeighborSignal(ctx.clickedPos))

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = ProjectorBlockEntity(pos, state)

    override fun neighborChanged(state: BlockState, level: Level, pos: BlockPos, block: Block,
                                 orientation: Orientation?, movedByPiston: Boolean) {
        if (level !is ServerLevel) return
        val powered = level.hasNeighborSignal(pos)
        if (powered == state.getValue(POWERED)) return
        val next = state.setValue(POWERED, powered)
        level.setBlock(pos, next, Block.UPDATE_CLIENTS)
        if (powered) (level.getBlockEntity(pos) as? ProjectorBlockEntity)?.project(level, next)
    }

    override fun useItemOn(stack: ItemStack, state: BlockState, level: Level, pos: BlockPos,
                           player: Player, hand: InteractionHand, hit: BlockHitResult): InteractionResult {
        if (stack.item !is ProjectableItem) return InteractionResult.PASS
        if (level.isClientSide) return InteractionResult.SUCCESS
        val entity = level.getBlockEntity(pos) as? ProjectorBlockEntity ?: return InteractionResult.PASS
        val inserted = stack.copyWithCount(1)
        val previous = entity.removeTheItem()
        if (!player.isCreative) stack.shrink(1)
        entity.setTheItem(inserted)
        if (!previous.isEmpty) {
            if (stack.isEmpty) player.setItemInHand(hand, previous)
            else giveOrDrop(player, previous)
        }
        return InteractionResult.SUCCESS
    }

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player,
                                hit: BlockHitResult): InteractionResult {
        val entity = level.getBlockEntity(pos) as? ProjectorBlockEntity ?: return InteractionResult.PASS
        if (entity.isEmpty) return InteractionResult.PASS
        if (!level.isClientSide) giveOrDrop(player, entity.removeTheItem())
        return InteractionResult.SUCCESS
    }

    private fun giveOrDrop(player: Player, stack: ItemStack) {
        if (!player.addItem(stack) && !stack.isEmpty) {
            val level = player.level() as? ServerLevel ?: return
            player.spawnAtLocation(level, stack)
        }
    }
}
