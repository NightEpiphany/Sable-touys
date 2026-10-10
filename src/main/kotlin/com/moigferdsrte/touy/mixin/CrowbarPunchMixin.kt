package com.moigferdsrte.touy.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.moigferdsrte.touy.item.CrowbarItem
import com.moigferdsrte.touy.util.CrowbarPhysics
import com.moigferdsrte.touy.util.TouyConfig
import dev.ryanhcode.sable.api.physics.PhysicsPipeline
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle
import dev.ryanhcode.sable.network.packets.tcp.ServerboundPunchSubLevelPacket
import dev.ryanhcode.sable.network.tcp.SablePacketContext
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import org.joml.Vector3d
import org.joml.Vector3dc
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

@Mixin(ServerboundPunchSubLevelPacket::class, remap = false)
abstract class CrowbarPunchMixin {
    @WrapOperation(
        method = ["handle"],
        at = [At(
            value = "INVOKE",
            target = "Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;applyImpulse(Ldev/ryanhcode/sable/api/physics/PhysicsPipelineBody;Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;)V"
        )],
        require = 3
    )
    private fun touysLimitCrowbarPush(pipeline: PhysicsPipeline, body: PhysicsPipelineBody,
        position: Vector3dc, impulse: Vector3dc, original: Operation<Void>, context: SablePacketContext
    ) {
        // 保留 Sable 的冷却、移动检查和反作用力，只限制手持撬棍时的冲量。
        if (context.player().mainHandItem.item is CrowbarItem && body is ServerSubLevel) {
            val handle = RigidBodyHandle.of(body)
            val strength = CrowbarItem.getPunchAttribute(context.player().mainHandItem)?.amount()
                ?: TouyConfig.crowbarStrength
            val limited = if (handle == null) Vector3d() else CrowbarPhysics.limitImpulse(
                body, handle, position, impulse, CrowbarPhysics.speedLimit(strength))
            original.call(pipeline, body, position, limited)
        } else {
            original.call(pipeline, body, position, impulse)
        }
    }
}
