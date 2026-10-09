package com.moigferdsrte.touy.client

import com.mojang.serialization.MapCodec
import com.moigferdsrte.touy.core.TouyItems
import com.moigferdsrte.touy.util.SubLevelTargeting
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty
import net.minecraft.world.entity.ItemOwner
import net.minecraft.world.item.ItemStack

/** Cached opening state feeds native item-model range selection, including the closing animation. */
object OrbAnimation : RangeSelectItemModelProperty {
    val CODEC: MapCodec<OrbAnimation> = MapCodec.unit(this)
    private var previous = 0
    private var current = 0

    fun tick(minecraft: Minecraft) {
        previous = current
        val player = minecraft.player
        val level = minecraft.level
        val active = player != null && level != null && player.isUsingItem && player.useItem.`is`(TouyItems.EVIL_ASS_ORB)
        val step = if (active) {
            if (SubLevelTargeting.target(level!!, player!!) == null) 1 else 6
        } else -1
        current = (current + step).coerceIn(0, 5)
    }

    override fun get(stack: ItemStack, level: ClientLevel?, owner: ItemOwner?, seed: Int): Float {
        val minecraft = Minecraft.getInstance()
        if (owner !== minecraft.player) return 0f
        val partialTick = minecraft.deltaTracker.getGameTimeDeltaPartialTick(true)
        return (previous + (current - previous) * partialTick) / 5f
    }

    override fun type(): MapCodec<out RangeSelectItemModelProperty> = CODEC
    fun clear() { previous = 0; current = 0 }
}
