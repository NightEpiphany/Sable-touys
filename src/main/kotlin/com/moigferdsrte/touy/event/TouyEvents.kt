package com.moigferdsrte.touy.event

import com.moigferdsrte.touy.item.RecordingService
import com.moigferdsrte.touy.util.TouyConfig
import com.moigferdsrte.touy.util.TouyDiagnostics
import dev.ryanhcode.sable.platform.SableEventPlatform
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.permissions.Permissions

object TouyEvents {
    private var configPollTicks = 0

    fun register() {
        TouyDiagnostics.refresh("initialized")
        ServerLifecycleEvents.SERVER_STARTED.register {
            configPollTicks = 0
            TouyConfig.reloadIfChanged()
            TouyDiagnostics.refresh("server running")
        }
        ServerLifecycleEvents.SERVER_STOPPING.register {
            RecordingService.stopAll()
            TouyDiagnostics.refresh("server stopping")
        }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            RecordingService.stopAll()
            TouyDiagnostics.refresh("server stopped")
        }
        ServerTickEvents.END_LEVEL_TICK.register { level -> RecordingService.tick(level) }
        ServerTickEvents.END_SERVER_TICK.register {
            if (++configPollTicks >= 20) {
                configPollTicks = 0
                TouyConfig.reloadIfChanged()
                TouyDiagnostics.refresh("server running")
            }
        }
        SableEventPlatform.INSTANCE.onPhysicsTick { system, _ -> RecordingService.physicsTick(system) }
        SableEventPlatform.INSTANCE.onSubLevelContainerReady { level, container ->
            if (level is ServerLevel) container.addObserver(RecordingService)
        }
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(Commands.literal("touys")
                .requires { source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN) }
                .then(Commands.literal("reload").executes { context ->
                    if (TouyConfig.reloadIfChanged(force = true)) {
                        TouyDiagnostics.refresh("server running")
                        context.source.sendSuccess({ Component.translatable("command.touys.reload.success") }, true)
                        1
                    } else {
                        context.source.sendFailure(Component.translatable("command.touys.reload.failed"))
                        0
                    }
                })
                .then(Commands.literal("diagnostics").executes { context ->
                    TouyDiagnostics.refresh("server running")
                    context.source.sendSuccess({ Component.literal(TouyDiagnostics.cached) }, false)
                    1
                }))
        }
    }
}
