package com.moigferdsrte.touy.gametest

import com.moigferdsrte.touy.core.TouyComponents
import com.moigferdsrte.touy.core.recording.PoseDataCodec
import com.moigferdsrte.touy.core.recording.PoseRecording
import com.moigferdsrte.touy.core.recording.PrimitivePoseBuffer
import com.moigferdsrte.touy.item.PhotoItem
import com.moigferdsrte.touy.item.RecordingService
import com.moigferdsrte.touy.item.TapeItem
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.companion.math.BoundingBox3i
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.Blocks
import org.joml.Quaterniond
import org.joml.Vector3d
import java.util.UUID
import kotlin.math.abs

/** Tests exercise registered components and Sable's actual server physics bodies. */
class RecordingGameTests {
    @GameTest
    fun snapshotRestoresPoseAndClearsVelocity(helper: GameTestHelper) {
        val fixture = assemble(helper)
        try {
            val target = fixture.target
            val pipeline = fixture.container.physicsSystem().pipeline
            val savedPosition = Vector3d(target.logicalPose().position())
            val savedOrientation = Quaterniond().rotateY(0.4)
            pipeline.teleport(target, savedPosition, savedOrientation)
            val photo = PhotoItem.create(RecordingService.dimension(helper.level), mapOf(target.uniqueId to pose(target)))
            val tag = photo.get(TouyComponents.SNAPSHOT)
                ?: throw helper.assertionException("Photo must store a snapshot component")
            helper.assertValueEqual(PoseDataCodec.decodeSnapshot(tag)?.size ?: -1, 1, "snapshot target count")

            pipeline.teleport(target, Vector3d(savedPosition).add(2.0, 1.0, 0.0), Quaterniond())
            pipeline.addLinearAndAngularVelocity(target, Vector3d(3.0, 2.0, 1.0), Vector3d(0.0, 1.0, 0.0))
            helper.assertValueEqual(PhotoItem.activate(helper.level, photo), 1, "restored target count")
            assertPose(helper, target, savedPosition, savedOrientation)
            helper.assertTrue(pipeline.getLinearVelocity(target, Vector3d()).lengthSquared() < 1.0e-10, "Snapshot must clear linear velocity")
            helper.assertTrue(pipeline.getAngularVelocity(target, Vector3d()).lengthSquared() < 1.0e-10, "Snapshot must clear angular velocity")

            val otherDimension = photo.copy()
            otherDimension.set(TouyComponents.SNAPSHOT, tag.copy().apply { putString("dimension", "touys_test:absent") })
            helper.assertValueEqual(PhotoItem.activate(helper.level, otherDimension), 0, "cross-dimension snapshots must be rejected")
            assertPose(helper, target, savedPosition, savedOrientation)
            helper.succeed()
        } finally {
            fixture.close()
        }
    }

    @GameTest
    fun recordingTapeAndReplayShareWorldTickFrames(helper: GameTestHelper) {
        val fixture = assemble(helper)
        val target = fixture.target
        val owner = UUID.randomUUID()
        val dimension = RecordingService.dimension(helper.level)
        val previousReplays = RecordingService.activeReplayCount(helper.level)
        val token = RecordingService.start(helper.level, target, owner)
            ?: throw helper.assertionException("A loaded Sable body must be recordable")
        try {
            val first = Vector3d(target.logicalPose().position())
            val last = Vector3d(first).add(2.0, 0.0, 0.0)
            val pipeline = fixture.container.physicsSystem().pipeline
            helper.assertTrue(RecordingService.start(helper.level, target, owner) == null, "A body cannot have duplicate recordings")
            helper.assertTrue(RecordingService.stop(helper.level, target.uniqueId, UUID.randomUUID(), token, dimension) == null, "Another owner must not stop the recording")
            helper.assertTrue(RecordingService.stop(helper.level, target.uniqueId, owner, UUID.randomUUID(), dimension) == null, "Another token must not stop the recording")
            pipeline.teleport(target, last, Quaterniond())
            RecordingService.tick(helper.level)
            val recording = RecordingService.stop(helper.level, target.uniqueId, owner, token, dimension)
                ?: throw helper.assertionException("The matching owner and token must retrieve the recording")
            helper.assertValueEqual(recording.frameCount, 2, "recorded frame count")
            val tape = TapeItem.create(recording)
            val decoded = tape.get(TouyComponents.RECORDING)?.let(PoseDataCodec::decodeRecording)
                ?: throw helper.assertionException("Tape data must survive component encoding")
            helper.assertValueEqual(decoded.frameCount, 2, "decoded frame count")

            helper.assertTrue(RecordingService.startReplay(target, decoded), "A recorded tape must create a replay joint")
            val system = fixture.container.physicsSystem()
            repeat(4) {
                RecordingService.physicsTick(system)
                assertPose(helper, target, first, Quaterniond())
            }
            RecordingService.tick(helper.level)
            repeat(4) {
                RecordingService.physicsTick(system)
                assertPose(helper, target, last, Quaterniond())
            }
            RecordingService.tick(helper.level)
            helper.assertValueEqual(RecordingService.activeReplayCount(helper.level), previousReplays, "finished replay count")
            // Once released, later pre-physics events must leave subsequent body movement alone.
            val afterReplay = Vector3d(last).add(0.5, 0.0, 0.0)
            pipeline.teleport(target, afterReplay, Quaterniond())
            RecordingService.physicsTick(system)
            assertPose(helper, target, afterReplay, Quaterniond())
            helper.succeed()
        } finally {
            RecordingService.stop(helper.level, target.uniqueId, owner, token, dimension, false)
            fixture.close()
        }
    }

    @GameTest
    fun removingTargetReleasesRecordingAndReplaySessions(helper: GameTestHelper) {
        val fixture = assemble(helper)
        val previousReplays = RecordingService.activeReplayCount(helper.level)
        val owner = UUID.randomUUID()
        val dimension = RecordingService.dimension(helper.level)
        val token = RecordingService.start(helper.level, fixture.target, owner)
            ?: throw helper.assertionException("Recording must start before removing its target")
        try {
            helper.assertTrue(RecordingService.startReplay(fixture.target, pose(fixture.target)), "Replay must start before removing its target")
            fixture.container.removeSubLevel(fixture.target, SubLevelRemovalReason.REMOVED)
            helper.assertTrue(RecordingService.recordingTicks(helper.level, fixture.target.uniqueId, token, dimension) == null, "Permanent removal must immediately discard its recording")
            RecordingService.physicsTick(fixture.container.physicsSystem())
            helper.assertValueEqual(RecordingService.activeReplayCount(helper.level), previousReplays, "removed target replay count")
            helper.succeed()
        } finally {
            RecordingService.stop(helper.level, fixture.target.uniqueId, owner, token, dimension, false)
            fixture.close()
        }
    }

    @GameTest(maxTicks = 40)
    fun projectedTapeCompletesThroughRegisteredTickEvents(helper: GameTestHelper) {
        val fixture = assemble(helper)
        val target = fixture.target
        val initial = Vector3d(target.logicalPose().position())
        val buffer = PrimitivePoseBuffer(3)
        for (frame in 0..2) buffer.append(initial.x + frame * 0.5, initial.y, initial.z, 0.0, 0.0, 0.0, 1.0)
        val tape = TapeItem.create(requireNotNull(buffer.snapshot()))
        tape.set(TouyComponents.TRACKED_SUB_LEVEL, target.uniqueId)
        // Let the synchronous service tests finish before testing only registered tick events.
        helper.runAfterDelay(2) {
            try {
                (tape.item as TapeItem).project(helper.level, tape, null)
                helper.assertTrue(RecordingService.activeReplayCount(helper.level) > 0, "Projecting a bound tape must start replay without a ray cast")
            } catch (failure: RuntimeException) {
                fixture.close()
                throw failure
            }
            helper.runAfterDelay(8) {
                try {
                    helper.assertFalse(target.isRemoved, "The test body must stay loaded during replay")
                    helper.assertValueEqual(RecordingService.activeReplayCount(helper.level), 0, "replay must finish through registered events")
                    helper.assertTrue(abs(target.logicalPose().position().x - (initial.x + 1.0)) < 0.05, "The actual physics body must reach the final replay frame")
                    helper.succeed()
                } finally {
                    fixture.close()
                }
            }
        }
    }

    private fun pose(target: ServerSubLevel): PoseRecording {
        val position = target.logicalPose().position()
        val rotation = target.logicalPose().orientation()
        val buffer = PrimitivePoseBuffer(1)
        check(buffer.append(position.x, position.y, position.z, rotation.x, rotation.y, rotation.z, rotation.w))
        return requireNotNull(buffer.snapshot())
    }

    private fun assertPose(helper: GameTestHelper, target: ServerSubLevel, position: Vector3d, rotation: Quaterniond) {
        val actual = target.logicalPose()
        helper.assertTrue(actual.position().distanceSquared(position) < 1.0e-10, "Body position must match saved pose")
        helper.assertTrue(abs(abs(actual.orientation().dot(rotation)) - 1.0) < 1.0e-8, "Body orientation must match saved pose")
    }

    private fun assemble(helper: GameTestHelper): Fixture {
        val local = BlockPos(2, 3, 2)
        helper.setBlock(local, Blocks.IRON_BLOCK)
        val anchor = helper.absolutePos(local)
        val container = SubLevelContainer.getContainer(helper.level) as? ServerSubLevelContainer
            ?: throw helper.assertionException("Sable server container must be initialized")
        val target = SubLevelAssemblyHelper.assembleBlocks(helper.level, anchor, listOf(anchor), BoundingBox3i(anchor, anchor))
        return Fixture(container, target)
    }

    private class Fixture(val container: ServerSubLevelContainer, val target: ServerSubLevel) {
        fun close() {
            RecordingService.stopReplay(target)
            if (!target.isRemoved && container.getSubLevel(target.uniqueId) === target) {
                container.removeSubLevel(target, SubLevelRemovalReason.REMOVED)
            }
        }
    }
}
