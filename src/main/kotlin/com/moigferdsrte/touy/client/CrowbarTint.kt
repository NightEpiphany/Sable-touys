package com.moigferdsrte.touy.client

import com.mojang.serialization.MapCodec
import com.moigferdsrte.touy.item.CrowbarItem
import net.minecraft.client.color.item.ItemTintSource
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack

/** Item model tint replaces the removed pre-26.3 ItemColor callback. */
object CrowbarTint : ItemTintSource {
    val CODEC: MapCodec<CrowbarTint> = MapCodec.unit(this)

    override fun calculate(stack: ItemStack, level: ClientLevel?, entity: LivingEntity?): Int = CrowbarItem.getColor(stack, 1)
    override fun type(): MapCodec<out ItemTintSource> = CODEC
}
