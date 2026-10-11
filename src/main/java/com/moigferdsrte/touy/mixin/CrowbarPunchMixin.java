package com.moigferdsrte.touy.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moigferdsrte.touy.item.CrowbarItem;
import com.moigferdsrte.touy.util.CrowbarPhysics;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.network.packets.tcp.ServerboundPunchSubLevelPacket;
import dev.ryanhcode.sable.network.tcp.SablePacketContext;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ServerboundPunchSubLevelPacket.class, remap = false)
public abstract class CrowbarPunchMixin {
    @WrapOperation(
        method = "handle",
        at = @At(
            value = "INVOKE",
            target = "Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;applyImpulse(Ldev/ryanhcode/sable/api/physics/PhysicsPipelineBody;Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;)V"
        ),
        require = 3
    )
    private void touys$limitCrowbarPush(
        PhysicsPipeline pipeline,
        PhysicsPipelineBody body,
        Vector3dc position,
        Vector3dc impulse,
        Operation<Void> original,
        SablePacketContext context
    ) {
        if (context.player().getMainHandItem().getItem() instanceof CrowbarItem && body instanceof ServerSubLevel subLevel) {
            RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
            double strength = CrowbarItem.Companion.getPunchAttribute(context.player().getMainHandItem()) != null
                ? CrowbarItem.Companion.getPunchAttribute(context.player().getMainHandItem()).amount()
                : CrowbarItem.DEFAULT_STRENGTH;
            Vector3d limited = handle == null
                ? new Vector3d()
                : CrowbarPhysics.INSTANCE.limitImpulse(
                    subLevel,
                    handle,
                    position,
                    impulse,
                    CrowbarPhysics.INSTANCE.speedLimit(strength)
                );
            original.call(pipeline, body, position, limited);
        } else {
            original.call(pipeline, body, position, impulse);
        }
    }
}
