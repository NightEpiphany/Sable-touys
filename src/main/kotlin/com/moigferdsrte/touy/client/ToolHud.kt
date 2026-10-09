package com.moigferdsrte.touy.client

import com.moigferdsrte.touy.core.TouyComponents
import com.moigferdsrte.touy.core.TouyItems
import com.moigferdsrte.touy.util.SubLevelTargeting
import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import java.util.UUID

/** Cache translated status until its item data or displayed second actually changes. */
object ToolHud {
    private var lastKind = ""
    private var lastName = ""
    private var lastId: UUID? = null
    private var lastSeconds = -1
    private var text: Component? = null
    private var textColor = 0xFF00FFFF.toInt()
    private var cachedLevel: ClientLevel? = null
    private var cachedDimension = ""

    fun tick(minecraft: Minecraft) {
        val player = minecraft.player ?: return clear()
        val level = minecraft.level ?: return clear()
        if (level !== cachedLevel) {
            cachedLevel = level
            cachedDimension = level.dimension().identifier().toString()
        }
        val invalidSelection = invalidAssembly(level, player, player.mainHandItem) ||
            invalidAssembly(level, player, player.offhandItem)
        var stack = player.mainHandItem
        if (!hasStatus(stack)) stack = player.offhandItem
        val tracked = stack.get(TouyComponents.TRACKED_SUB_LEVEL)
        val copied = stack.get(TouyComponents.COPIED_PLOT)
        val seconds = (stack.get(TouyComponents.RECORDING_TICKS) ?: 0) / 20
        val kind = when {
            invalidSelection -> "invalid_selection"
            stack.`is`(TouyItems.CAMCORDER) && tracked != null -> "recording"
            copied != null -> "contains"
            tracked != null -> "tracking"
            else -> ""
        }
        val name = if (copied != null) copied.getStringOr("name", "") else if (tracked != null)
            minecraft.level?.let { SubLevelContainer.getContainer(it)?.getSubLevel(tracked)?.name } ?: "" else ""
        if (kind == lastKind && name == lastName && tracked == lastId && seconds == lastSeconds) return
        lastKind = kind; lastName = name; lastId = tracked; lastSeconds = seconds
        textColor = if (invalidSelection) 0xFFFF5555.toInt() else 0xFF00FFFF.toInt()
        val displayName: Component = if (name.isBlank()) UNNAMED else Component.literal(name)
        text = when (kind) {
            "invalid_selection" -> INVALID_SELECTION
            "recording" -> Component.translatable("touys.hud.recording", displayName, seconds)
            "contains" -> Component.translatable("touys.tooltip.contains", displayName)
            "tracking" -> Component.translatable("touys.tooltip.tracking", displayName)
            else -> null
        }
    }

    private fun hasStatus(stack: ItemStack) = stack.has(TouyComponents.TRACKED_SUB_LEVEL) || stack.has(TouyComponents.COPIED_PLOT)

    /** Check the same grid identity used by assembly and its preview, with a shared cached ray. */
    private fun invalidAssembly(level: ClientLevel, player: LocalPlayer, stack: ItemStack): Boolean {
        if (!stack.`is`(TouyItems.BUBBLE_BLOWER)) return false
        val start = stack.get(TouyComponents.ASSEMBLY_START) ?: return false
        if (stack.get(TouyComponents.ASSEMBLY_DIMENSION) != cachedDimension) return true
        val startGrid = Sable.HELPER.getContaining(level, start)
        if (startGrid?.uniqueId != stack.get(TouyComponents.ASSEMBLY_SUB_LEVEL) ||
            startGrid?.isRemoved == true || (startGrid == null && Sable.HELPER.isInPlotGrid(level, start))) return true
        val hit = SubLevelTargeting.hit(level, player) ?: return false
        return startGrid !== Sable.HELPER.getContaining(level, hit.blockPos)
    }

    fun extract(graphics: GuiGraphicsExtractor) {
        CameraFlash.extract(graphics)
        val status = text ?: return
        val minecraft = Minecraft.getInstance()
        if (minecraft.gui.screen() != null) return
        graphics.centeredText(minecraft.font, status, graphics.guiWidth() / 2, graphics.guiHeight() / 2 + 24, textColor)
    }

    fun clear() {
        lastKind = ""; lastName = ""; lastId = null; lastSeconds = -1; text = null
        textColor = 0xFF00FFFF.toInt(); cachedLevel = null; cachedDimension = ""
    }

    private val UNNAMED = Component.translatable("touys.sub_level.unnamed")
    private val INVALID_SELECTION = Component.translatable("touys.message.invalid_selection").withStyle(ChatFormatting.RED)
}
