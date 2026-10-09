package com.moigferdsrte.touy.block

import com.moigferdsrte.touy.core.TouyBlockEntities
import com.moigferdsrte.touy.item.ProjectableItem
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.DirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.ticks.ContainerSingleItem

class ProjectorBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(TouyBlockEntities.PROJECTOR, pos, state), ContainerSingleItem.BlockContainerSingleItem {
    private var storedItem: ItemStack = ItemStack.EMPTY

    override fun getContainerBlockEntity(): BlockEntity = this
    override fun getTheItem(): ItemStack = storedItem
    override fun getMaxStackSize(): Int = 1
    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean = slot == 0 && stack.item is ProjectableItem

    override fun setTheItem(stack: ItemStack) {
        storedItem = stack
        val world = level
        if (world != null && !world.isClientSide) {
            val state = world.getBlockState(blockPos)
            if (state.hasProperty(ProjectorBlock.TAPE) && state.getValue(ProjectorBlock.TAPE) == stack.isEmpty) {
                world.setBlock(blockPos, state.setValue(ProjectorBlock.TAPE, !stack.isEmpty), 2)
            }
        }
        setChanged()
    }

    fun project(level: ServerLevel, state: BlockState) {
        val projectable = storedItem.item as? ProjectableItem ?: return
        var hitPos: BlockPos? = null
        if (projectable.requiresRayCast(storedItem)) {
            val facing = state.getValue(DirectionalBlock.FACING)
            val origin = Vec3.atCenterOf(blockPos)
            val start = origin.relative(facing, 0.501)
            val end = origin.relative(facing, 16.0)
            val hit = level.clip(ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()))
            if (hit.type == HitResult.Type.BLOCK) hitPos = hit.blockPos
            val steps = (start.distanceTo(hit.location) * 5).toInt().coerceIn(0, 80)
            val normal = facing.getStep()
            for (i in 0 until steps) {
                val distance = 0.501 + i * 0.2
                level.sendParticles(BEAM, origin.x + normal.x * distance, origin.y + normal.y * distance,
                    origin.z + normal.z * distance, 1, 0.0, 0.0, 0.0, 0.0)
            }
        }
        projectable.project(level, storedItem, hitPos)
        setChanged()
    }

    override fun saveAdditional(output: ValueOutput) {
        super.saveAdditional(output)
        if (!storedItem.isEmpty) output.store("item", ItemStack.CODEC, storedItem)
    }

    override fun loadAdditional(input: ValueInput) {
        super.loadAdditional(input)
        storedItem = input.read("item", ItemStack.CODEC).orElse(ItemStack.EMPTY)
    }

    companion object {
        private val BEAM = DustParticleOptions(0xFF3030, 1.0f)
    }
}
