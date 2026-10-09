package com.moigferdsrte.touy.gametest

import com.moigferdsrte.touy.item.ProjectableItem
import net.fabricmc.api.ModInitializer
import net.minecraft.core.BlockPos
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

object GameTestSupport : ModInitializer {
    lateinit var probe: ProjectionProbe
        private set

    override fun onInitialize() {
        val id = Identifier.fromNamespaceAndPath("touys_test", "projection_probe")
        probe = Registry.register(BuiltInRegistries.ITEM, id,
            ProjectionProbe(Item.Properties().setId(ResourceKey.create(Registries.ITEM, id))))
    }
}

class ProjectionProbe(properties: Item.Properties) : Item(properties), ProjectableItem {
    var invocations = 0
    var lastHit: BlockPos? = null
    override fun requiresRayCast(stack: ItemStack): Boolean = true
    override fun project(level: ServerLevel, stack: ItemStack, hitPos: BlockPos?) {
        invocations++
        lastHit = hitPos
    }
}
