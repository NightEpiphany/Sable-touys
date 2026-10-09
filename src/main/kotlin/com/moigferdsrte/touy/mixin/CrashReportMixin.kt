package com.moigferdsrte.touy.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.moigferdsrte.touy.util.TouyDiagnostics
import net.minecraft.CrashReport
import net.minecraft.SystemReport
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

@Mixin(CrashReport::class)
abstract class CrashReportMixin {
    @WrapOperation(
        method = ["getDetails(Ljava/lang/StringBuilder;)V"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/SystemReport;appendToCrashReportString(Ljava/lang/StringBuilder;)V")]
    )
    private fun touysAppendDiagnostics(report: SystemReport, builder: StringBuilder, original: Operation<Void>) {
        report.setDetail("Touys diagnostics", TouyDiagnostics.cached)
        original.call(report, builder)
    }
}
