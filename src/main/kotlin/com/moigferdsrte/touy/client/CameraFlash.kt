package com.moigferdsrte.touy.client

import com.moigferdsrte.touy.SableTouy
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvent
import kotlin.math.max
import kotlin.math.min

object CameraFlash {
    private val CLICK = SoundEvent.createVariableRangeEvent(SableTouy.id("item.camera_click"))
    private var ticks = 0
    private var minX = 0.0
    private var minY = 0.0
    private var maxX = 0.0
    private var maxY = 0.0

    fun start(startX: Double, startY: Double, endX: Double, endY: Double) {
        minX = min(startX, endX); minY = min(startY, endY)
        maxX = max(startX, endX); maxY = max(startY, endY)
        ticks = 8
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(CLICK, 1.0f))
    }

    fun tick() { if (ticks > 0) ticks-- }
    fun clear() { ticks = 0 }

    fun extract(graphics: GuiGraphicsExtractor) {
        if (ticks <= 0) return
        val alpha = (ticks * 255 / 8).coerceIn(0, 255)
        graphics.fill((minX * graphics.guiWidth()).toInt(), (minY * graphics.guiHeight()).toInt(),
            (maxX * graphics.guiWidth()).toInt(), (maxY * graphics.guiHeight()).toInt(), (alpha shl 24) or 0xFFFFFF)
    }
}
