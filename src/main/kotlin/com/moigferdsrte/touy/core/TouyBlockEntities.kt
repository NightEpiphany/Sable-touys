package com.moigferdsrte.touy.core

import com.moigferdsrte.touy.SableTouy
import com.moigferdsrte.touy.block.ProjectorBlockEntity
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.entity.BlockEntityType

object TouyBlockEntities {
    lateinit var PROJECTOR: BlockEntityType<ProjectorBlockEntity>; private set

    fun register() {
        PROJECTOR = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, SableTouy.id("projector"),
            BlockEntityType(::ProjectorBlockEntity, setOf(TouyBlocks.PROJECTOR)))
    }
}