package com.moigferdsrte.touy.core

import com.moigferdsrte.touy.SableTouy
import com.moigferdsrte.touy.block.ProjectorBlock
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour

object TouyBlocks {
    lateinit var PROJECTOR: Block; private set
    lateinit var PROJECTOR_ITEM: Item; private set

    fun register() {
        val id = SableTouy.id("projector")
        PROJECTOR = Registry.register(BuiltInRegistries.BLOCK, id,
            ProjectorBlock(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, id)).strength(2f)))
        PROJECTOR_ITEM = Registry.register(BuiltInRegistries.ITEM, id,
            BlockItem(PROJECTOR, Item.Properties().setId(ResourceKey.create(Registries.ITEM, id))
                .useBlockDescriptionPrefix().component(TouyComponents.ITEM_DESCRIPTION, 2)))
    }
}