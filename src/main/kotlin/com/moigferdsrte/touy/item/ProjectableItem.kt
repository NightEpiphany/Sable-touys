package com.moigferdsrte.touy.item

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack

interface ProjectableItem {
    fun project(level: ServerLevel, stack: ItemStack, hitPos: BlockPos?)
    fun requiresRayCast(stack: ItemStack): Boolean = false
}
