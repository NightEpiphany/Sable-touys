package com.moigferdsrte.touy

import com.moigferdsrte.touy.core.TouyBlocks
import com.moigferdsrte.touy.core.TouyComponents
import com.moigferdsrte.touy.core.TouyItems
import com.moigferdsrte.touy.core.TouyBlockEntities
import com.moigferdsrte.touy.event.TouyEvents
import com.moigferdsrte.touy.network.TouyNetwork
import com.moigferdsrte.touy.util.TouyConfig
import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

object SableTouy : ModInitializer {
    const val MOD_ID = "touys"
    val LOGGER = LoggerFactory.getLogger(MOD_ID)

    override fun onInitialize() {
        TouyConfig.load(FabricLoader.getInstance().configDir.resolve("touys.json5"))
        TouyComponents.register()
        TouyBlocks.register()
        TouyBlockEntities.register()
        TouyItems.register()
        TouyNetwork.register()
        TouyEvents.register()
        LOGGER.info("Sable Touys initialized for Minecraft 26.3")
    }

    fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
