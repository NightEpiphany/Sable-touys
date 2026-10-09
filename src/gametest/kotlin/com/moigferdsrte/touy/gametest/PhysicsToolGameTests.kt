package com.moigferdsrte.touy.gametest

import com.moigferdsrte.touy.core.TouyComponents
import com.moigferdsrte.touy.core.TouyItems
import com.moigferdsrte.touy.item.BubbleBlowerItem
import com.moigferdsrte.touy.item.CrowbarItem
import com.moigferdsrte.touy.util.SubLevelTargeting
import com.moigferdsrte.touy.util.SubLevelTemplate
import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.companion.math.BoundingBox3i
import dev.ryanhcode.sable.fabric.network.FabricSablePacketContext
import dev.ryanhcode.sable.index.SableAttributes
import dev.ryanhcode.sable.network.packets.tcp.ServerboundPunchSubLevelPacket
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.ticks.LevelChunkTicks
import net.minecraft.world.ticks.ScheduledTick
import net.minecraft.world.ticks.TickPriority
import org.joml.Vector3d
import java.util.UUID
import kotlin.math.abs

/** Exercises the real Sable plot serializer, physics bodies and server-authoritative item ray casts. */
class PhysicsToolGameTests {
    @GameTest
    fun clonePreservesChestContentsScheduledTicksAndPreviewBounds(helper: GameTestHelper) = withBodies(helper) {
        val chest = BlockPos(2, 2, 2)
        val stone = BlockPos(3, 2, 2)
        helper.setBlock(chest, Blocks.CHEST)
        helper.setBlock(stone, Blocks.STONE)
        val diamonds = ItemStack(Items.DIAMOND, 7).apply {
            set(DataComponents.CUSTOM_NAME, Component.literal("Cloned cargo"))
        }
        helper.getBlockEntity(chest, ChestBlockEntity::class.java).setItem(4, diamonds.copy())
        val player = helper.makeMockPlayer(GameType.CREATIVE)
        aimAt(player, Vec3.atCenterOf(helper.absolutePos(chest)))
        player.yRot = 90.0f
        val source = assemble(helper, chest, stone)
        val sourceChest = source.plot.centerBlock
        val offset = helper.absolutePos(stone).subtract(helper.absolutePos(chest))
        val sourceStone = sourceChest.offset(offset)
        val sourceChunk = requireNotNull(source.plot.getChunk(source.plot.toLocal(ChunkPos.containing(sourceChest))))
        val now = helper.level.gameTime
        @Suppress("UNCHECKED_CAST")
        (sourceChunk.blockTicks as LevelChunkTicks<Block>).schedule(
            ScheduledTick(Blocks.STONE, sourceStone, now + 120, TickPriority.HIGH, 0))
        @Suppress("UNCHECKED_CAST")
        (sourceChunk.fluidTicks as LevelChunkTicks<Fluid>).schedule(
            ScheduledTick(Fluids.WATER, sourceChest, now + 90, TickPriority.LOW, 1))

        val template = SubLevelTemplate.of(source, player)
        val unchanged = template.copy()
        player.yRot = 0.0f
        val destination = helper.absolutePos(BlockPos(5, 1, 5))
        val hit = BlockHitResult(Vec3(destination.x + 0.5, destination.y + 1.0, destination.z + 0.5),
            Direction.UP, destination, false)
        val pose = SubLevelTemplate.placementPose(helper.level, player, hit, template)
        val preview = SubLevelTemplate.previewBounds(template, pose)
        val clone = SubLevelTemplate.create(helper.level, pose, template, player)
        val sourceOrigin = source.plot.toGlobal(ChunkPos(0, 0))
        val cloneOrigin = clone.plot.toGlobal(ChunkPos(0, 0))
        val copiedChest = sourceChest.offset(cloneOrigin.minBlockX - sourceOrigin.minBlockX, 0,
            cloneOrigin.minBlockZ - sourceOrigin.minBlockZ)
        val copiedStone = copiedChest.offset(offset)
        val copiedChunk = requireNotNull(clone.plot.getChunk(clone.plot.toLocal(ChunkPos.containing(copiedChest))))
        val copiedEntity = copiedChunk.getBlockEntity(copiedChest) as? ChestBlockEntity
            ?: throw helper.assertionException("The copied chest must load as a live block entity")
        val contents = copiedEntity.getItem(4)
        helper.assertTrue(ItemStack.isSameItemSameComponents(contents, diamonds), "Inventory item components survive cloning")
        helper.assertValueEqual(contents.count, 7, "Inventory item count survives cloning")
        val ticks = copiedChunk.getTicksForSerialization(now)
        val blockTick = ticks.blocks().single { it.type() === Blocks.STONE && it.pos() == copiedStone }
        val fluidTick = ticks.fluids().single { it.type() === Fluids.WATER && it.pos() == copiedChest }
        helper.assertValueEqual(blockTick.delay(), 120, "Block tick delay survives relocation")
        helper.assertValueEqual(blockTick.priority(), TickPriority.HIGH, "Block tick priority survives relocation")
        helper.assertValueEqual(fluidTick.delay(), 90, "Fluid tick delay survives relocation")
        helper.assertValueEqual(fluidTick.priority(), TickPriority.LOW, "Fluid tick priority survives relocation")
        val bounds = clone.boundingBox()
        assertBounds(helper, preview, AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
            bounds.maxX(), bounds.maxY(), bounds.maxZ()))
        helper.assertTrue(abs(abs(clone.logicalPose().orientation().dot(pose.orientation())) - 1.0) < 1.0e-8,
            "Pasted body uses the preview rotation")
        SubLevelTemplate.create(helper.level, pose, template, player)
        helper.assertTrue(template == unchanged, "Repeated paste must leave the stored template unchanged")
        helper.assertValueEqual((sourceChunk.getBlockEntity(sourceChest) as ChestBlockEntity).getItem(4).count,
            7, "Cloning must preserve the original chest inventory")
        helper.succeed()
    }

    @GameTest
    fun invalidCloneIsRejectedBeforeAllocatingBody(helper: GameTestHelper) = withBodies(helper) { container ->
        val block = BlockPos(2, 2, 2)
        helper.setBlock(block, Blocks.IRON_BLOCK)
        val player = helper.makeMockPlayer(GameType.CREATIVE)
        aimAt(player, Vec3.atCenterOf(helper.absolutePos(block)))
        val source = assemble(helper, block, block)
        val template = SubLevelTemplate.of(source, player)
        val hit = BlockHitResult(helper.absoluteVec(Vec3(5.5, 2.0, 5.5)), Direction.UP,
            helper.absolutePos(BlockPos(5, 1, 5)), false)
        val pose = SubLevelTemplate.placementPose(helper.level, player, hit, template)
        val previous = container.allSubLevels.size
        template.putInt("size_x", template.getIntOr("size_x", 1) + 1)
        var rejected = false
        try {
            SubLevelTemplate.create(helper.level, pose, template, player)
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        helper.assertTrue(rejected, "Inconsistent template bounds must be rejected")
        helper.assertValueEqual(container.allSubLevels.size, previous, "Invalid templates do not allocate a Sable body")
        helper.succeed()
    }

    @GameTest
    fun bubbleBlowerAssemblesSelectionAndPreservesInventory(helper: GameTestHelper) = withBodies(helper) { container ->
        val chest = BlockPos(2, 2, 2)
        val stone = BlockPos(4, 2, 2)
        helper.setBlock(chest, Blocks.CHEST)
        helper.setBlock(stone, Blocks.STONE)
        val emeralds = ItemStack(Items.EMERALD, 3).apply {
            set(DataComponents.CUSTOM_NAME, Component.literal("Assembled cargo"))
        }
        helper.getBlockEntity(chest, ChestBlockEntity::class.java).setItem(0, emeralds.copy())
        val player = helper.makeMockPlayer(GameType.CREATIVE)
        val tool = ItemStack(TouyItems.BUBBLE_BLOWER)
        player.setItemInHand(InteractionHand.MAIN_HAND, tool)
        val previous = bodyIds(container)
        helper.assertTrue(useBubble(helper, player, helper.absolutePos(chest)) == InteractionResult.SUCCESS,
            "First click records a selection")
        helper.assertTrue(tool.get(TouyComponents.ASSEMBLY_START) == helper.absolutePos(chest),
            "Selection stores the authoritative server hit")
        helper.assertTrue(useBubble(helper, player, helper.absolutePos(stone)) == InteractionResult.SUCCESS,
            "Second click assembles the selected blocks")
        helper.assertValueEqual(container.allSubLevels.size, previous.size + 1, "Selection allocates exactly one body")
        helper.assertBlockPresent(Blocks.AIR, chest)
        helper.assertBlockPresent(Blocks.AIR, stone)
        helper.assertTrue(tool.get(TouyComponents.ASSEMBLY_START) == null, "Successful assembly clears its selection")
        val assembled = newBody(helper, container, previous)
        val movedChest = plotPositionOf(assembled, helper.absolutePos(chest))
        val movedStone = plotPositionOf(assembled, helper.absolutePos(stone))
        val inventory = helper.level.getBlockEntity(movedChest) as? ChestBlockEntity
            ?: throw helper.assertionException("Assembly must move the chest block entity")
        helper.assertTrue(ItemStack.isSameItemSameComponents(inventory.getItem(0), emeralds),
            "Assembly preserves inventory type and custom item components")
        helper.assertValueEqual(inventory.getItem(0).count, 3, "Assembly preserves inventory count")
        helper.assertTrue(helper.level.getBlockState(movedChest).`is`(Blocks.CHEST), "Chest retains its block state")
        helper.assertTrue(helper.level.getBlockState(movedStone).`is`(Blocks.STONE),
            "Selection preserves gaps and relative block positions")
        helper.assertTrue(movedStone.subtract(movedChest) == helper.absolutePos(stone).subtract(helper.absolutePos(chest)),
            "Block spacing survives assembly in each structure rotation")
        helper.assertTrue(helper.level.getBlockState(BlockPos.containing(
            (movedChest.x.toDouble() + movedStone.x) * 0.5, movedChest.y.toDouble(),
            (movedChest.z.toDouble() + movedStone.z) * 0.5)).isAir, "The gap remains empty")
        helper.succeed()
    }

    @GameTest
    fun bubbleBlowerRejectsProtectedBlocksWithoutPartialMovement(helper: GameTestHelper) = withBodies(helper) { container ->
        val first = BlockPos(2, 2, 2)
        val protected = BlockPos(3, 2, 2)
        helper.setBlock(first, Blocks.STONE)
        helper.setBlock(protected, Blocks.BEDROCK)
        val player = helper.makeMockPlayer(GameType.SURVIVAL)
        val tool = ItemStack(TouyItems.BUBBLE_BLOWER)
        player.setItemInHand(InteractionHand.MAIN_HAND, tool)
        val previous = bodyIds(container)
        useBubble(helper, player, helper.absolutePos(first))
        helper.assertTrue(useBubble(helper, player, helper.absolutePos(protected)) == InteractionResult.FAIL,
            "A survival selection containing bedrock must fail")
        helper.assertBlockPresent(Blocks.STONE, first)
        helper.assertBlockPresent(Blocks.BEDROCK, protected)
        helper.assertValueEqual(bodyIds(container), previous, "Rejected selection does not allocate a body")
        helper.assertTrue(tool.get(TouyComponents.ASSEMBLY_START) == helper.absolutePos(first),
            "Rejected selection remains available for correction")
        helper.succeed()
    }

    @GameTest
    fun bubbleBlowerKeepsSubLevelSelectionOnItsOriginalGrid(helper: GameTestHelper) = withBodies(helper) { container ->
        val first = BlockPos(2, 2, 2)
        val last = BlockPos(3, 2, 2)
        val static = BlockPos(5, 2, 2)
        helper.setBlock(first, Blocks.IRON_BLOCK)
        helper.setBlock(last, Blocks.STONE)
        helper.setBlock(static, Blocks.DIRT)
        val original = assemble(helper, first, last)
        val player = helper.makeMockPlayer(GameType.CREATIVE)
        val tool = ItemStack(TouyItems.BUBBLE_BLOWER)
        player.setItemInHand(InteractionHand.MAIN_HAND, tool)
        helper.assertTrue(useBubble(helper, player, helper.absolutePos(first)) == InteractionResult.SUCCESS,
            "A ray may select a block in a moving sub-level")
        helper.assertTrue(tool.get(TouyComponents.ASSEMBLY_START) == original.plot.centerBlock,
            "Sub-level selection stores plot coordinates")
        helper.assertTrue(tool.get(TouyComponents.ASSEMBLY_SUB_LEVEL) == original.uniqueId,
            "Sub-level selection remembers its original grid identity")
        val previous = bodyIds(container)
        helper.assertTrue(useBubble(helper, player, helper.absolutePos(static)) == InteractionResult.FAIL,
            "A selection must not span a sub-level and the static world")
        helper.assertValueEqual(bodyIds(container), previous, "Mixed grids do not allocate a body")
        helper.assertBlockPresent(Blocks.DIRT, static)
        helper.assertTrue(helper.level.getBlockState(original.plot.centerBlock).`is`(Blocks.IRON_BLOCK),
            "Mixed-grid rejection preserves the original body")
        helper.assertTrue(useBubble(helper, player, helper.absolutePos(last)) == InteractionResult.SUCCESS,
            "Correcting the second click back to the original grid succeeds")
        helper.assertTrue(tool.get(TouyComponents.ASSEMBLY_START) == null, "Sub-level assembly clears its selection")
        val assembled = newBody(helper, container, previous)
        val movedFirst = plotPositionOf(assembled, helper.absolutePos(first))
        val movedLast = plotPositionOf(assembled, helper.absolutePos(last))
        val newGrid = Sable.HELPER.getContaining(helper.level, movedFirst)
        helper.assertTrue(newGrid === assembled && newGrid !== original, "Selected blocks move into a new sub-level grid")
        helper.assertTrue(helper.level.getBlockState(movedFirst).`is`(Blocks.IRON_BLOCK), "Corrected selection preserves its first block")
        helper.assertTrue(helper.level.getBlockState(movedLast).`is`(Blocks.STONE), "Corrected selection preserves its last block")
        helper.succeed()
    }

    @GameTest
    fun actualPunchPacketMovesBodyAndHonorsCrowbarStrength(helper: GameTestHelper) = withBodies(helper) { container ->
        val local = BlockPos(2, 2, 2)
        helper.setBlock(local, Blocks.IRON_BLOCK)
        val target = assemble(helper, local, local)
        val pipeline = container.physicsSystem().pipeline
        val hit = target.plot.centerBlock
        val point = Vector3d(hit.x + 0.5, hit.y + 0.5, hit.z.toDouble())
        target.logicalPose().transformPosition(point)
        point.sub(target.logicalPose().position())
        val direction = Vector3d(0.0, 0.0, 1.0)

        val bareHanded = helper.makeMockPlayer(GameType.SURVIVAL)
        bareHanded.snapTo(helper.absoluteVec(Vec3(2.5, 2.0, 0.5)), 0.0f, 0.0f)
        bareHanded.setOnGround(true)
        pipeline.resetVelocity(target)
        ServerboundPunchSubLevelPacket(hit, point, direction).handle(FabricSablePacketContext(bareHanded))
        val normalVelocity = pipeline.getLinearVelocity(target, Vector3d())
        helper.assertTrue(normalVelocity.z > 1.0e-6 && normalVelocity.isFinite,
            "The actual left-click punch packet applies a forward physics impulse")

        val player = helper.makeMockPlayer(GameType.SURVIVAL)
        player.snapTo(helper.absoluteVec(Vec3(2.5, 2.0, 0.5)), 0.0f, 0.0f)
        player.setOnGround(true)
        val crowbar = ItemStack(TouyItems.CROWBAR)
        player.setItemInHand(InteractionHand.MAIN_HAND, crowbar)
        // Apply the same native modifier the equipment update applies when this stack is held.
        val attribute = requireNotNull(player.getAttribute(SableAttributes.PUNCH_STRENGTH))
        attribute.addTransientModifier(requireNotNull(CrowbarItem.getPunchAttribute(crowbar)))
        helper.assertTrue(attribute.value > 1.0, "Holding the registered crowbar strengthens Sable's punch attribute")
        pipeline.resetVelocity(target)
        ServerboundPunchSubLevelPacket(hit, point, direction).handle(FabricSablePacketContext(player))
        val strengthened = pipeline.getLinearVelocity(target, Vector3d())
        helper.assertTrue(strengthened.z > normalVelocity.z * 1.1 && strengthened.isFinite,
            "The actual punch handler honors the crowbar's strength modifier")
        helper.assertTrue(helper.level.getBlockState(hit).`is`(Blocks.IRON_BLOCK), "Punching preserves the target block")
        helper.assertFalse(crowbar.canDestroyBlock(helper.level.getBlockState(hit), helper.level, hit, player),
            "The crowbar prevents normal left-click block destruction")
        helper.succeed()
    }

    private fun useBubble(helper: GameTestHelper, player: Player, worldPos: BlockPos): InteractionResult {
        aimAt(player, Vec3.atCenterOf(worldPos))
        return (TouyItems.BUBBLE_BLOWER as BubbleBlowerItem).use(helper.level, player, InteractionHand.MAIN_HAND)
    }

    private fun aimAt(player: Player, worldPoint: Vec3) {
        player.snapTo(worldPoint.x, worldPoint.y + 3.0, worldPoint.z, 0.0f, 90.0f)
        player.setOldPosAndRot()
        SubLevelTargeting.invalidate(player)
    }

    private fun assemble(helper: GameTestHelper, first: BlockPos, last: BlockPos): ServerSubLevel {
        val anchor = helper.absolutePos(first)
        val end = helper.absolutePos(last)
        val blocks = if (anchor == end) listOf(anchor) else listOf(anchor, end)
        return SubLevelAssemblyHelper.assembleBlocks(helper.level, anchor, blocks, BoundingBox3i(anchor, end))
    }

    private fun bodyIds(container: ServerSubLevelContainer): Set<UUID> =
        container.allSubLevels.mapTo(HashSet()) { it.uniqueId }

    private fun newBody(helper: GameTestHelper, container: ServerSubLevelContainer, previous: Set<UUID>): ServerSubLevel {
        val added = container.allSubLevels.filter { it.uniqueId !in previous }
        helper.assertValueEqual(added.size, 1, "The tool creates exactly one identifiable new body")
        return added.single() as ServerSubLevel
    }

    private fun plotPositionOf(body: ServerSubLevel, worldBlock: BlockPos): BlockPos {
        val point = Vector3d(worldBlock.x + 0.5, worldBlock.y + 0.5, worldBlock.z + 0.5)
        body.logicalPose().transformPositionInverse(point)
        return BlockPos.containing(point.x, point.y, point.z)
    }

    private fun assertBounds(helper: GameTestHelper, expected: AABB, actual: AABB) {
        helper.assertTrue(abs(expected.minX - actual.minX) < 1.0e-6 && abs(expected.minY - actual.minY) < 1.0e-6 &&
            abs(expected.minZ - actual.minZ) < 1.0e-6 && abs(expected.maxX - actual.maxX) < 1.0e-6 &&
            abs(expected.maxY - actual.maxY) < 1.0e-6 && abs(expected.maxZ - actual.maxZ) < 1.0e-6,
            "Pasted body bounds must match the preview despite mass and rotation")
    }

    private inline fun withBodies(helper: GameTestHelper, test: (ServerSubLevelContainer) -> Unit) {
        val container = SubLevelContainer.getContainer(helper.level) as? ServerSubLevelContainer
            ?: throw helper.assertionException("Sable server container must be initialized")
        val previous = container.allSubLevels.mapTo(HashSet()) { it.uniqueId }
        try {
            test(container)
        } finally {
            for (body in container.allSubLevels.toList()) {
                if (body.uniqueId !in previous && !body.isRemoved) {
                    container.removeSubLevel(body, SubLevelRemovalReason.REMOVED)
                }
            }
        }
    }
}
