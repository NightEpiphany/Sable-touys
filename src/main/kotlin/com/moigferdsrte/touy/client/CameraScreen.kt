package com.moigferdsrte.touy.client

import com.moigferdsrte.touy.core.TouyItems
import com.moigferdsrte.touy.item.CameraItem
import com.moigferdsrte.touy.network.SnapshotPayload
import com.moigferdsrte.touy.util.TouyConfig
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.joml.FrustumIntersection
import org.joml.Matrix4f
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/** Drag selection is extracted from the current camera and never pauses physics. */
class CameraScreen : Screen(Component.translatable("touys.camera.title")) {
    private val projectionView = Matrix4f()
    private val selection = Matrix4f()
    private val intersection = FrustumIntersection()
    private val selected = ArrayList<UUID>(CameraItem.MAX_CAPTURE_TARGETS)
    val selectedIds: List<UUID> get() = selected
    private var dragging = false
    private var startX = 0.0
    private var startY = 0.0
    private var endX = 0.0
    private var endY = 0.0
    private var overflow = false
    private var displayedCount = -1
    private var displayedOverflow = false
    private var selectionText: Component = Component.empty()
    private var feedback: Component? = null

    override fun init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel")) { onClose() }
            .pos(width / 2 - 50, height - 35).size(100, 20).build())
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        graphics.centeredText(font, INSTRUCTIONS, width / 2, 12, -1)
        graphics.centeredText(font, feedback ?: selectionText, width / 2, 26, -1)
        if (dragging) {
            val left = (min(startX, endX) * width).toInt()
            val top = (min(startY, endY) * height).toInt()
            val right = (max(startX, endX) * width).toInt()
            val bottom = (max(startY, endY) * height).toInt()
            graphics.fill(left, top, right, bottom, 0x22FFFFFF)
            graphics.outline(left, top, max(1, right - left), max(1, bottom - top), -1)
        }
    }

    override fun tick() {
        val player = minecraft.player
        if (minecraft.level == null || player == null ||
            (!player.mainHandItem.`is`(TouyItems.CAMERA) && !player.offhandItem.`is`(TouyItems.CAMERA))) {
            onClose()
            return
        }
        if (dragging) updateSelection()
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (super.mouseClicked(event, doubleClick)) return true
        if (event.button() != 0) return false
        dragging = true
        feedback = null
        startX = (event.x() / width).coerceIn(0.0, 1.0)
        startY = (event.y() / height).coerceIn(0.0, 1.0)
        endX = startX
        endY = startY
        updateSelection()
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        if (!dragging || event.button() != 0) return super.mouseDragged(event, dragX, dragY)
        endX = (event.x() / width).coerceIn(0.0, 1.0)
        endY = (event.y() / height).coerceIn(0.0, 1.0)
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (!dragging || event.button() != 0) return super.mouseReleased(event)
        endX = (event.x() / width).coerceIn(0.0, 1.0)
        endY = (event.y() / height).coerceIn(0.0, 1.0)
        updateSelection()
        dragging = false
        when {
            overflow -> feedback = Component.translatable("touys.camera.too_many", captureLimit())
            selected.isEmpty() -> feedback = EMPTY_SELECTION
            !ClientPlayNetworking.canSend(SnapshotPayload.TYPE) -> feedback = UNAVAILABLE
            else -> {
                ClientPlayNetworking.send(SnapshotPayload(selected))
                CameraFlash.start(startX, startY, endX, endY)
                onClose()
            }
        }
        selected.clear()
        return true
    }

    private fun updateSelection() {
        selected.clear()
        overflow = false
        val level = minecraft.level ?: return
        val container = SubLevelContainer.getContainer(level) ?: return
        val camera = minecraft.gameRenderer.mainCamera()
        if (!camera.isInitialized) return
        camera.getViewRotationProjectionMatrix(projectionView)
        val left = min(startX, endX) * 2.0 - 1.0
        val right = max(startX, endX) * 2.0 - 1.0
        val bottom = 1.0 - max(startY, endY) * 2.0
        val top = 1.0 - min(startY, endY) * 2.0
        selection.identity()
            .translate(((left + right) * 0.5).toFloat(), ((bottom + top) * 0.5).toFloat(), 0f)
            .scale(max(1.0 / width, (right - left) * 0.5).toFloat(), max(1.0 / height, (top - bottom) * 0.5).toFloat(), 1f)
            .invert().mul(projectionView)
        intersection.set(selection)
        val cameraPosition = camera.position()
        val rangeSquared = TouyConfig.itemRange.toDouble() * TouyConfig.itemRange
        val limit = captureLimit()
        val subLevels = container.allSubLevels
        var index = 0
        while (index < subLevels.size) {
            val subLevel = subLevels[index++]
            if (subLevel.isRemoved) continue
            val bounds = subLevel.boundingBox()
            val dx = cameraPosition.x - cameraPosition.x.coerceIn(bounds.minX(), bounds.maxX())
            val dy = cameraPosition.y - cameraPosition.y.coerceIn(bounds.minY(), bounds.maxY())
            val dz = cameraPosition.z - cameraPosition.z.coerceIn(bounds.minZ(), bounds.maxZ())
            if (dx * dx + dy * dy + dz * dz > rangeSquared) continue
            // Relative coordinates preserve precision even far from the world origin.
            if (intersection.testAab(
                    (bounds.minX() - cameraPosition.x).toFloat(), (bounds.minY() - cameraPosition.y).toFloat(), (bounds.minZ() - cameraPosition.z).toFloat(),
                    (bounds.maxX() - cameraPosition.x).toFloat(), (bounds.maxY() - cameraPosition.y).toFloat(), (bounds.maxZ() - cameraPosition.z).toFloat())) {
                if (selected.size >= limit) { overflow = true; break }
                selected.add(subLevel.uniqueId)
            }
        }
        if (displayedCount != selected.size || displayedOverflow != overflow) {
            displayedCount = selected.size
            displayedOverflow = overflow
            selectionText = if (overflow) Component.translatable("touys.camera.too_many", limit)
                else Component.translatable("touys.camera.selected", selected.size)
        }
    }

    private fun captureLimit() = TouyConfig.maxSnapshotSubLevels.coerceIn(1, CameraItem.MAX_CAPTURE_TARGETS)

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) = Unit
    override fun isPauseScreen() = false

    companion object {
        private val INSTRUCTIONS = Component.translatable("touys.camera.instructions")
        private val EMPTY_SELECTION = Component.translatable("touys.camera.empty")
        private val UNAVAILABLE = Component.translatable("touys.camera.unavailable")
    }
}
