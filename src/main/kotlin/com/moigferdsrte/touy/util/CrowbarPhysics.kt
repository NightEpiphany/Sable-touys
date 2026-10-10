package com.moigferdsrte.touy.util

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import org.joml.Vector3d
import org.joml.Vector3dc
import kotlin.math.min
import kotlin.math.sqrt

object CrowbarPhysics {
    // Sable 使用格/秒；默认 1.5 格/秒在无障碍物的前半秒约移动 0.75 格。
    const val NORMAL_PUSH_SPEED = 1.5
    const val MAX_PUSH_SPEED = 3.0
    private const val REFERENCE_STRENGTH = 400.0

    fun speedLimit(strength: Double): Double {
        if (!strength.isFinite()) return NORMAL_PUSH_SPEED
        return (NORMAL_PUSH_SPEED * sqrt((strength.coerceIn(0.0, 4096.0) + 1.0) /
            (REFERENCE_STRENGTH + 1.0))).coerceAtMost(MAX_PUSH_SPEED)
    }

    fun limitImpulse(target: ServerSubLevel, handle: RigidBodyHandle,
        position: Vector3dc, impulse: Vector3dc, speedLimit: Double = NORMAL_PUSH_SPEED
    ): Vector3d {
        val direction = Vector3d(impulse)
        val magnitude = direction.length()
        if (!magnitude.isFinite() || magnitude <= 0.0 || target.isRemoved) return direction.zero()
        direction.div(magnitude)

        val mass = target.massTracker
        val center = mass.centerOfMass ?: return direction.zero()
        val inverseMass = mass.inverseMass
        if (!inverseMass.isFinite() || inverseMass <= 0.0) return direction.zero()

        val pose = target.logicalPose()
        val linearVelocity = pose.transformNormalInverse(handle.getLinearVelocity(Vector3d()))
        val angularVelocity = pose.transformNormalInverse(handle.getAngularVelocity(Vector3d()))
        val offset = Vector3d(position).sub(center)
        val pointVelocity = angularVelocity.cross(offset).add(linearVelocity)
        val linearChange = Vector3d(direction).mul(inverseMass)
        val torqueChange = offset.cross(direction, Vector3d())
        mass.inverseInertiaTensor.transform(torqueChange)
        val pointChange = torqueChange.cross(offset).add(linearChange)

        // 同时限制质心和命中点的合速度，防止不同方向的连续敲击叠加超速。
        val allowed = min(magnitude, min(
            allowedImpulse(linearVelocity, linearChange, speedLimit),
            allowedImpulse(pointVelocity, pointChange, speedLimit)
        ))
        return direction.mul(allowed)
    }

    private fun allowedImpulse(velocity: Vector3d, changePerImpulse: Vector3d, speedLimit: Double): Double {
        val changeSquared = changePerImpulse.lengthSquared()
        val projection = velocity.dot(changePerImpulse)
        val discriminant = projection * projection +
            changeSquared * (speedLimit * speedLimit - velocity.lengthSquared())
        if (!discriminant.isFinite() || discriminant < 0.0 || changeSquared <= 0.0) return 0.0
        return ((sqrt(discriminant) - projection) / changeSquared).coerceAtLeast(0.0)
    }
}
