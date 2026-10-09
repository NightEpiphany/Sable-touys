package com.moigferdsrte.touy.item

import com.moigferdsrte.touy.SableTouy
import com.moigferdsrte.touy.core.recording.PoseRecording
import com.moigferdsrte.touy.core.recording.PrimitivePoseBuffer
import com.moigferdsrte.touy.core.recording.RecordingExpiry
import com.moigferdsrte.touy.core.recording.ReplayCursor
import com.moigferdsrte.touy.util.TouyConfig
import dev.ryanhcode.sable.api.physics.PhysicsPipeline
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.SubLevel
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
import net.minecraft.server.level.ServerLevel
import org.joml.Quaterniond
import org.joml.Vector3d
import java.util.Collections
import java.util.EnumSet
import java.util.IdentityHashMap
import java.util.UUID

/** Server-thread service. Each level owns its sessions; hot loops use fixed arrays and cached poses. */
object RecordingService : SubLevelObserver {
    private const val MAX_SESSIONS = 64
    private val lockedAxes = Collections.unmodifiableSet(EnumSet.allOf(ConstraintJointAxis::class.java))
    private val levels = IdentityHashMap<ServerLevel, LevelSessions>()

    private class RecordingSession(
        val target: UUID,
        val owner: UUID,
        val token: UUID,
        val buffer: PrimitivePoseBuffer,
        val expiry: RecordingExpiry,
    )

    private class ReplaySession(val target: ServerSubLevel, val frames: PoseRecording) {
        val position = Vector3d()
        val orientation = Quaterniond()
        val localOrientation = Quaterniond()
        val linearVelocity = Vector3d()
        val angularVelocity = Vector3d()
        var constraint: GenericConstraintHandle? = null
        val cursor = ReplayCursor(frames.frameCount)

        fun readFrame() {
            val frame = cursor.frame
            position.set(frames.component(frame, 0), frames.component(frame, 1), frames.component(frame, 2))
            orientation.set(frames.component(frame, 3), frames.component(frame, 4), frames.component(frame, 5), frames.component(frame, 6))
        }
    }

    private class LevelSessions(val level: ServerLevel) {
        val recordings = arrayOfNulls<RecordingSession>(MAX_SESSIONS)
        val replays = arrayOfNulls<ReplaySession>(MAX_SESSIONS)
    }

    fun start(level: ServerLevel, target: ServerSubLevel, owner: UUID): UUID? {
        if (target.level !== level || target.isRemoved) return null
        val state = levels.getOrPut(level) { LevelSessions(level) }
        if (state.recordings.any { it?.target == target.uniqueId }) return null
        val limit = TouyConfig.recordingMaxSessions.coerceIn(1, MAX_SESSIONS)
        if (state.recordings.count { it != null } >= limit) return null
        val slot = state.recordings.indexOfFirst { it == null }
        if (slot < 0) return null
        val configured = TouyConfig.recordingMaxDuration
        val capacity = (if (configured < 0) PoseRecording.MAX_FRAMES else configured).coerceIn(1, PoseRecording.MAX_FRAMES)
        val token = UUID.randomUUID()
        val session = RecordingSession(target.uniqueId, owner, token, PrimitivePoseBuffer(capacity), RecordingExpiry(level.gameTime))
        capture(session, target)
        state.recordings[slot] = session
        SableTouy.LOGGER.debug("Started recording {} in {} (capacity {} ticks)", target.uniqueId, dimension(level), capacity)
        return token
    }

    fun stop(level: ServerLevel, id: UUID, owner: UUID?, token: UUID, sourceDimension: String, save: Boolean = true): PoseRecording? {
        val state = findSource(level, sourceDimension) ?: return null
        for (i in state.recordings.indices) {
            val session = state.recordings[i] ?: continue
            if (session.target != id || session.token != token || (owner != null && session.owner != owner)) continue
            state.recordings[i] = null
            SableTouy.LOGGER.debug("Stopped recording {} after {} frames", id, session.buffer.size)
            return if (save) session.buffer.snapshot() else null
        }
        return null
    }

    fun touch(level: ServerLevel, id: UUID, token: UUID, sourceDimension: String): Boolean {
        val state = findSource(level, sourceDimension) ?: return false
        for (session in state.recordings) {
            if (session != null && session.target == id && session.token == token) {
                session.expiry.touch(state.level.gameTime)
                return true
            }
        }
        return false
    }

    fun recordingTicks(level: ServerLevel, id: UUID, token: UUID, sourceDimension: String): Int? {
        val state = findSource(level, sourceDimension) ?: return null
        for (session in state.recordings) if (session != null && session.target == id && session.token == token) return session.buffer.size
        return null
    }

    fun startReplay(target: ServerSubLevel, frames: PoseRecording): Boolean {
        val level = target.level as? ServerLevel ?: return false
        if (target.isRemoved || frames.frameCount == 0) return false
        val state = levels.getOrPut(level) { LevelSessions(level) }
        stopReplay(target)
        if (state.replays.count { it != null } >= TouyConfig.recordingMaxSessions.coerceIn(1, MAX_SESSIONS)) return false
        val slot = state.replays.indexOfFirst { it == null }
        if (slot < 0) return false
        val container = SubLevelContainer.getContainer(level) as? ServerSubLevelContainer ?: return false
        val session = ReplaySession(target, frames)
        session.readFrame()
        val pipeline = container.physicsSystem().pipeline
        try {
            // Generic joints expose mutable frames in Sable 26.3, so the same fixed joint is reused.
            session.constraint = pipeline.addConstraint(null, target, GenericConstraintConfiguration(
                session.position, target.logicalPose().rotationPoint(), session.orientation, session.localOrientation, lockedAxes,
            ))
            if (session.constraint == null) return false
            resetVelocity(pipeline, session)
            pipeline.teleport(target, session.position, session.orientation)
            pipeline.wakeUp(target)
            state.replays[slot] = session
            SableTouy.LOGGER.debug("Started replay {} ({} frames)", target.uniqueId, frames.frameCount)
            return true
        } catch (failure: RuntimeException) {
            release(session)
            SableTouy.LOGGER.warn("Cannot start replay for {} in {}", target.uniqueId, dimension(level), failure)
            return false
        }
    }

    fun stopReplay(target: ServerSubLevel) {
        val level = target.level as? ServerLevel ?: return
        val state = levels[level] ?: return
        for (i in state.replays.indices) {
            val session = state.replays[i] ?: continue
            if (session.target === target) {
                release(session)
                state.replays[i] = null
            }
        }
    }

    fun tick(level: ServerLevel) {
        val state = levels[level] ?: return
        val system = SubLevelPhysicsSystem.get(level) ?: return
        if (!level.tickRateManager().runsNormally() || system.paused) return
        val container = SubLevelContainer.getContainer(level) ?: return
        val timeout = TouyConfig.recordingInactiveTicks.let { if (it < 0) -1 else it.coerceAtLeast(1) }
        for (i in state.recordings.indices) {
            val session = state.recordings[i] ?: continue
            val target = container.getSubLevel(session.target) as? ServerSubLevel
            if (target?.isRemoved == true) {
                state.recordings[i] = null
                SableTouy.LOGGER.debug("Discarded recording of removed sub-level {}", session.target)
                continue
            }
            val targetPresent = target != null
            if (target != null) {
                if (!session.buffer.isFull) capture(session, target)
            }
            if (session.expiry.expired(targetPresent, level.gameTime, timeout)) {
                state.recordings[i] = null
                SableTouy.LOGGER.debug("Expired inactive recording {}", session.target)
            }
        }
        for (i in state.replays.indices) {
            val session = state.replays[i] ?: continue
            if (session.target.isRemoved || container.getSubLevel(session.target.uniqueId) !== session.target) {
                release(session)
                state.replays[i] = null
            } else if (session.cursor.advanceAfterPhysics()) {
                resetVelocity(system.pipeline, session)
                release(session)
                state.replays[i] = null
            }
        }
    }

    /** Permanent deletion always frees recording memory; unloading follows the configured expiry. */
    override fun onSubLevelRemoved(subLevel: SubLevel, reason: SubLevelRemovalReason) {
        val level = subLevel.level as? ServerLevel ?: return
        val state = levels[level] ?: return
        if (reason == SubLevelRemovalReason.REMOVED) {
            for (i in state.recordings.indices) {
                if (state.recordings[i]?.target == subLevel.uniqueId) state.recordings[i] = null
            }
        }
        for (i in state.replays.indices) {
            val replay = state.replays[i] ?: continue
            if (replay.target === subLevel) {
                release(replay)
                state.replays[i] = null
            }
        }
    }

    /** Called by Sable's pre-physics event once per substep; no frame, vector or joint is allocated. */
    fun physicsTick(system: SubLevelPhysicsSystem) {
        val state = levels[system.level] ?: return
        if (system.paused) return
        val pipeline = system.pipeline
        for (i in state.replays.indices) {
            val session = state.replays[i] ?: continue
            if (session.target.isRemoved) {
                release(session)
                state.replays[i] = null
                continue
            }
            val constraint = session.constraint
            if (constraint == null || !constraint.isValid) {
                release(session)
                state.replays[i] = null
                continue
            }
            session.readFrame()
            constraint.setFrame1(session.position, session.orientation)
            // Mass changes move this anchor; keep a reused joint aligned with the current pose pivot.
            constraint.setFrame2(session.target.logicalPose().rotationPoint(), session.localOrientation)
            resetVelocity(pipeline, session)
            pipeline.teleport(session.target, session.position, session.orientation)
            session.cursor.markApplied()
        }
    }

    fun stopAll() {
        for (state in levels.values) {
            for (session in state.replays) if (session != null) release(session)
            state.recordings.fill(null)
            state.replays.fill(null)
        }
        levels.clear()
    }

    fun diagnostics(): String {
        var recordings = 0
        var replays = 0
        var storedFrames = 0
        for (state in levels.values) {
            for (session in state.recordings) if (session != null) { recordings++; storedFrames += session.buffer.size }
            for (session in state.replays) if (session != null) replays++
        }
        return "levels=${levels.size}, recordings=$recordings, recordedFrames=$storedFrames, replays=$replays"
    }

    fun activeRecordingCount(level: ServerLevel): Int = levels[level]?.recordings?.count { it != null } ?: 0
    fun activeReplayCount(level: ServerLevel): Int = levels[level]?.replays?.count { it != null } ?: 0

    private fun capture(session: RecordingSession, target: ServerSubLevel) {
        val pose = target.logicalPose()
        val position = pose.position()
        val orientation = pose.orientation()
        session.buffer.append(position.x, position.y, position.z, orientation.x, orientation.y, orientation.z, orientation.w)
    }

    private fun resetVelocity(pipeline: PhysicsPipeline, session: ReplaySession) {
        pipeline.getLinearVelocity(session.target, session.linearVelocity).negate()
        pipeline.getAngularVelocity(session.target, session.angularVelocity).negate()
        pipeline.addLinearAndAngularVelocity(session.target, session.linearVelocity, session.angularVelocity)
    }

    private fun release(session: ReplaySession) {
        val constraint = session.constraint ?: return
        session.constraint = null
        if (constraint.isValid) constraint.remove()
    }

    private fun findSource(current: ServerLevel, dimensionId: String): LevelSessions? {
        val sameLevel = levels[current]
        if (sameLevel != null && dimension(current) == dimensionId) return sameLevel
        for (state in levels.values) if (state.level.server === current.server && dimension(state.level) == dimensionId) return state
        return null
    }

    fun dimension(level: ServerLevel): String = level.dimension().identifier().toString()
}
