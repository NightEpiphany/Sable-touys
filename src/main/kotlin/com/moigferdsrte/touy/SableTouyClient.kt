package com.moigferdsrte.touy

import com.moigferdsrte.touy.client.ClientEvents
import net.fabricmc.api.ClientModInitializer

object SableTouyClient : ClientModInitializer {
    override fun onInitializeClient() {
        ClientEvents.register()
        SableTouy.LOGGER.info("Sable Touys client initialized")
    }
}
