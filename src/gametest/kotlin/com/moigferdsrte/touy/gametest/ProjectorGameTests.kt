package com.moigferdsrte.touy.gametest

import com.moigferdsrte.touy.block.ProjectorBlock
import com.moigferdsrte.touy.block.ProjectorBlockEntity
import com.moigferdsrte.touy.core.TouyBlocks
import com.moigferdsrte.touy.core.TouyComponents
import com.moigferdsrte.touy.core.TouyItems
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.CrashReport
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.DirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

class ProjectorGameTests {
    private val position = BlockPos(1, 1, 1)

    @GameTest
    fun crashReportIncludesCachedDiagnostics(helper: GameTestHelper) {
        val report = CrashReport("Touys diagnostic test", IllegalStateException("test only"))
        helper.assertTrue(report.details.contains("Touys diagnostics"), "The Mixin must add Touys diagnostics to crash reports")
        helper.assertTrue(report.details.contains("sable="), "Crash diagnostics must include the active Sable version")
        helper.succeed()
    }

    @GameTest
    fun storedPhotoSurvivesSaveLoad(helper: GameTestHelper) {
        helper.setBlock(position, TouyBlocks.PROJECTOR)
        val entity = helper.getBlockEntity(position, ProjectorBlockEntity::class.java)
        val data = CompoundTag().apply { putString("dimension", "minecraft:overworld"); putInt("version", 1) }
        entity.setTheItem(ItemStack(TouyItems.PHOTO).apply { set(TouyComponents.SNAPSHOT, data) })
        helper.assertBlockProperty(position, ProjectorBlock.TAPE, true)
        val tag = entity.saveWithFullMetadata(helper.level.registryAccess())
        val restored = BlockEntity.loadStatic(helper.absolutePos(position), entity.blockState, tag,
            helper.level.registryAccess()) as? ProjectorBlockEntity
        helper.assertTrue(restored != null, "Projector block entity loads with its registered type")
        helper.assertTrue(restored!!.theItem.`is`(TouyItems.PHOTO), "Saved item is a photo")
        helper.assertTrue(restored.theItem.get(TouyComponents.SNAPSHOT) == data, "Snapshot component survives save/load")
        helper.succeed()
    }

    @GameTest
    fun swappingAndFullInventoryRemovalPreserveItems(helper: GameTestHelper) {
        helper.setBlock(position, TouyBlocks.PROJECTOR)
        val player = helper.makeMockPlayer(GameType.SURVIVAL)
        player.setPos(Vec3.atCenterOf(helper.absolutePos(position)))
        val absolute = helper.absolutePos(position)
        val entity = helper.getBlockEntity(position, ProjectorBlockEntity::class.java)
        entity.setTheItem(ItemStack(TouyItems.PHOTO))
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(TouyItems.TAPE))
        val hit = BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false)
        entity.blockState.useItemOn(player.mainHandItem, helper.level, player, InteractionHand.MAIN_HAND, hit)
        helper.assertTrue(entity.theItem.`is`(TouyItems.TAPE), "Inserted tape occupies the projector")
        helper.assertTrue(player.mainHandItem.`is`(TouyItems.PHOTO), "Previous photo is returned exactly once")
        for (slot in 0 until player.inventory.containerSize) player.inventory.setItem(slot, ItemStack(Items.COBBLESTONE, 64))
        entity.blockState.useWithoutItem(helper.level, player, hit)
        helper.assertTrue(entity.isEmpty, "Removing the tape empties the single-item container")
        helper.assertBlockProperty(position, ProjectorBlock.TAPE, false)
        helper.assertItemEntityCountIs(TouyItems.TAPE, position, 3.0, 1)
        helper.succeed()
    }

    @GameTest
    fun breakingProjectorDropsItsContentsOnce(helper: GameTestHelper) {
        helper.setBlock(position, TouyBlocks.PROJECTOR)
        helper.getBlockEntity(position, ProjectorBlockEntity::class.java).setTheItem(ItemStack(TouyItems.TAPE))
        helper.level.destroyBlock(helper.absolutePos(position), true)
        helper.assertItemEntityCountIs(TouyItems.TAPE, position, 3.0, 1)
        helper.assertItemEntityCountIs(TouyBlocks.PROJECTOR_ITEM, position, 3.0, 1)
        helper.succeed()
    }

    @GameTest(maxTicks = 20)
    fun sustainedPowerTriggersOnceAndRaycastsForward(helper: GameTestHelper) {
        val probe = GameTestSupport.probe
        probe.invocations = 0
        val direction = helper.getAbsoluteDirection(Direction.EAST)
        helper.setBlock(position, TouyBlocks.PROJECTOR.defaultBlockState().setValue(DirectionalBlock.FACING, direction))
        helper.setBlock(BlockPos(4, 1, 1), Blocks.STONE)
        helper.getBlockEntity(position, ProjectorBlockEntity::class.java).setTheItem(ItemStack(probe))
        val signal = BlockPos(1, 1, 2)
        helper.setBlock(signal, Blocks.REDSTONE_BLOCK)
        helper.assertValueEqual(probe.invocations, 1, "First rising edge triggers projection")
        helper.assertTrue(probe.lastHit == helper.absolutePos(BlockPos(4, 1, 1)), "Projection targets the forward block")
        helper.runAfterDelay(5) {
            helper.assertBlockProperty(position, ProjectorBlock.POWERED, true)
            helper.level.updateNeighborsAt(helper.absolutePos(signal), Blocks.REDSTONE_BLOCK, null)
            helper.assertValueEqual(probe.invocations, 1, "Unchanged signal does not repeat projection")
            helper.setBlock(signal, Blocks.AIR)
            helper.assertBlockProperty(position, ProjectorBlock.POWERED, false)
            helper.setBlock(signal, Blocks.REDSTONE_BLOCK)
            helper.assertValueEqual(probe.invocations, 2, "A second rising edge triggers another projection")
            helper.succeed()
        }
    }
}
