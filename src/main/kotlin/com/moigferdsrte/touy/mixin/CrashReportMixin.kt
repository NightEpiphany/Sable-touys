package com.moigferdsrte.touy.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.moigferdsrte.touy.util.TouyDiagnostics
import net.minecraft.CrashReport
import net.minecraft.SystemReport
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

// 26.3 的目标方法使用原始名称，不进行混淆映射。
@Mixin(CrashReport::class, remap = false)
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
