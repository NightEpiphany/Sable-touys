package com.moigferdsrte.touy.util

import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.sublevel.SubLevel
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Uses Sable's sub-level-aware ray cast and shares a result between tools in the same tick. */
object SubLevelTargeting {
    private class CachedRay {
        var level = WeakReference<Level>(null)
        var tick = Long.MIN_VALUE
        var x = Double.NaN
        var y = Double.NaN
        var z = Double.NaN
        var yaw = Float.NaN
        var pitch = Float.NaN
        var partialTicks = Float.NaN
        var range = -1
        var result: BlockHitResult? = null
    }

    private val rays = ThreadLocal.withInitial { WeakHashMap<Player, CachedRay>() }

    fun hit(level: Level, player: Player): BlockHitResult? = hit(level, player, 1.0f)

    fun hit(level: Level, player: Player, partialTicks: Float): BlockHitResult? {
        val cache = rays.get().getOrPut(player) { CachedRay() }
        val range = TouyConfig.itemRange
        if (cache.level.get() === level && cache.tick == level.gameTime &&
            cache.x == player.x && cache.y == player.y && cache.z == player.z &&
            cache.yaw == player.yRot && cache.pitch == player.xRot &&
            cache.partialTicks == partialTicks && cache.range == range
        ) return cache.result

        val eye = Sable.HELPER.getEyePositionInterpolated(player, partialTicks)
        val end = eye.add(player.lookAngle.scale(range.toDouble()))
        val result = level.clip(ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
        if (cache.level.get() !== level) cache.level = WeakReference(level)
        cache.tick = level.gameTime
        cache.x = player.x
        cache.y = player.y
        cache.z = player.z
        cache.yaw = player.yRot
        cache.pitch = player.xRot
        cache.partialTicks = partialTicks
        cache.range = range
        cache.result = result.takeIf { it.type == HitResult.Type.BLOCK }
        return cache.result
    }

    fun target(level: Level, player: Player): SubLevel? {
        val result = hit(level, player) ?: return null
        return Sable.HELPER.getContaining(level, result.blockPos)?.takeUnless { it.isRemoved }
    }

    fun withinRange(level: Level, player: Player, pos: BlockPos): Boolean {
        val world = Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(pos))
        val eye = Sable.HELPER.projectOutOfSubLevel(level, player.eyePosition)
        // Centers can be half a block beyond the face that the ray actually hits.
        val range = TouyConfig.itemRange + 0.875
        return eye.distanceToSqr(world) <= range * range
    }

    fun canUse(level: Level, player: Player, pos: BlockPos): Boolean {
        val gameMode = player.gameMode() ?: return false
        if (!player.mayBuild() || player.isSpectator ||
            player.blockActionRestricted(level, pos, gameMode)
        ) return false
        val world = Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(pos))
        val worldPos = BlockPos.containing(world)
        return (level !is ServerLevel || level.worldBorder.isWithinBounds(worldPos)) && level.mayInteract(player, worldPos)
    }

    /** Invalidates geometry removed or moved by a tool during this same game tick. */
    fun invalidate(player: Player) {
        rays.get()[player]?.tick = Long.MIN_VALUE
    }
}
