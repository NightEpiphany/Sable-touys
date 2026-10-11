package com.moigferdsrte.touy.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moigferdsrte.touy.util.TouyDiagnostics;
import net.minecraft.CrashReport;
import net.minecraft.SystemReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CrashReport.class, remap = false)
public abstract class CrashReportMixin {
    @WrapOperation(
        method = "getDetails(Ljava/lang/StringBuilder;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/SystemReport;appendToCrashReportString(Ljava/lang/StringBuilder;)V"
        )
    )
    private void touys$appendDiagnostics(SystemReport report, StringBuilder builder, Operation<Void> original) {
        report.setDetail("Touys diagnostics", TouyDiagnostics.INSTANCE.getCached());
        original.call(report, builder);
    }
}
