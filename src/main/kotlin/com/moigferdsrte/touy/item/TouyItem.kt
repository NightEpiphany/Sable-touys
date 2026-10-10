package com.moigferdsrte.touy.item

import com.moigferdsrte.touy.core.TouyComponents
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.component.TooltipDisplay
import java.util.function.Consumer

open class TouyItem(properties: Properties, descriptionLines: Int) : Item(properties.component(TouyComponents.ITEM_DESCRIPTION, descriptionLines)) {
    @Deprecated("Deprecated in Java")
    override fun appendHoverText(stack: ItemStack, context: TooltipContext, display: TooltipDisplay, lines: Consumer<Component>, flag: TooltipFlag) {
        super.appendHoverText(stack, context, display, lines, flag)
        val count = stack.get(TouyComponents.ITEM_DESCRIPTION) ?: return
        for (index in 0 until count) {
            lines.accept(Component.translatable("${descriptionId}.description_$index").withStyle(ChatFormatting.GRAY))
        }
    }
}