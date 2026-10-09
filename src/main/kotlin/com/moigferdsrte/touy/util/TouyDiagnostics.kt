package com.moigferdsrte.touy.util

import com.moigferdsrte.touy.SableTouy
import com.moigferdsrte.touy.item.RecordingService
import net.fabricmc.loader.api.FabricLoader

/** The crash thread only reads this immutable snapshot, never live native physics or session maps. */
object TouyDiagnostics {
    @Volatile var cached: String = "Touys initialization has not completed"
        private set

    fun refresh(state: String) {
        val loader = FabricLoader.getInstance()
        val version = loader.getModContainer(SableTouy.MOD_ID).map { it.metadata.version.friendlyString }.orElse("unknown")
        val sableVersion = loader.getModContainer("sable").map { it.metadata.version.friendlyString }.orElse("missing")
        cached = "version=$version; sable=$sableVersion; environment=${loader.environmentType}; state=$state; " +
            "${RecordingService.diagnostics()}; config=${TouyConfig.snapshot()}"
    }
}
