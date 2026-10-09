package com.moigferdsrte.touy.client

import com.moigferdsrte.touy.SableTouy
import com.moigferdsrte.touy.core.TouyComponents
import com.moigferdsrte.touy.core.TouyItems
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.color.item.ItemTintSources
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.world.InteractionResult

object ClientEvents {
    fun register() {
        ItemTintSources.ID_MAPPER.put(SableTouy.id("crowbar_strength"), CrowbarTint.CODEC)
        RangeSelectItemModelProperties.ID_MAPPER.put(SableTouy.id("orb_open"), OrbAnimation.CODEC)
        UseItemCallback.EVENT.register { player, level, hand ->
            val minecraft = Minecraft.getInstance()
            if (level.isClientSide && player === minecraft.player && player.getItemInHand(hand).`is`(TouyItems.CAMERA)) {
                if (minecraft.gui.screen() == null) minecraft.gui.setScreen(CameraScreen())
                InteractionResult.SUCCESS
            } else InteractionResult.PASS
        }
        LevelExtractionEvents.END_EXTRACTION.register(ToolOverlays::extract)
        HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, SableTouy.id("tools")) { graphics, _ -> ToolHud.extract(graphics) }
        ClientTickEvents.END_CLIENT_TICK.register { minecraft -> CameraFlash.tick(); ToolHud.tick(minecraft); OrbAnimation.tick(minecraft) }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> CameraFlash.clear(); ToolHud.clear(); OrbAnimation.clear() }
        ItemTooltipCallback.EVENT.register { stack, _, _, lines ->
            val minecraft = Minecraft.getInstance()
            val count = stack.get(TouyComponents.ITEM_DESCRIPTION)
            if (count != null) {
                val prefix = stack.item.descriptionId + ".description_"
                if (!minecraft.hasShiftDown()) {
                    lines.removeIf { (it.contents as? TranslatableContents)?.key?.startsWith(prefix) == true }
                    lines.add(Component.translatable("touys.tooltip.description").withStyle(ChatFormatting.GRAY))
                } else {
                    val existing = HashSet<String>()
                    lines.forEach { (it.contents as? TranslatableContents)?.key?.let(existing::add) }
                    for (index in 0 until count.coerceIn(0, 32)) {
                        val key = prefix + index
                        if (existing.add(key)) lines.add(Component.translatable(key).withStyle(ChatFormatting.GRAY))
                    }
                }
            }
            val tracked = stack.get(TouyComponents.TRACKED_SUB_LEVEL)
            val level = minecraft.level
            if (tracked != null && level != null) {
                val subLevel = SubLevelContainer.getContainer(level)?.getSubLevel(tracked)
                if (subLevel != null) {
                    // Replace the persistent UUID fallback with the current display name.
                    lines.removeIf { (it.contents as? TranslatableContents)?.key == "touys.tooltip.tracking" }
                    val name = subLevel.name?.let(Component::literal) ?: Component.translatable("touys.sub_level.unnamed")
                    lines.add(Component.translatable("touys.tooltip.tracking", name).withStyle(ChatFormatting.AQUA))
                }
            }
        }
    }
}
