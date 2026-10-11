package com.moigferdsrte.touy.client

import com.moigferdsrte.touy.core.TouyComponents
import com.moigferdsrte.touy.core.TouyItems
import com.moigferdsrte.touy.util.SubLevelTargeting
import com.moigferdsrte.touy.util.SubLevelTemplate
import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.companion.math.Pose3dc
import dev.ryanhcode.sable.sublevel.ClientSubLevel
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.gizmos.Gizmo
import net.minecraft.gizmos.GizmoPrimitives
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.gizmos.SimpleGizmoCollector
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import kotlin.math.max
import kotlin.math.min

/** Extract immutable geometry on the client thread; drawing never reads a live sub-level. */
object ToolOverlays {
    private val collector = SimpleGizmoCollector()
    private val transformScratch = Vector3d()
    private val SELECTED = GizmoStyle.stroke(-1, 2.0f)
    private val TRACKED = GizmoStyle.stroke(0xFF00FFFF.toInt(), 2.0f)
    private val REMOVAL = GizmoStyle.strokeAndFill(0xFFFF3333.toInt(), 2.0f, 0x22FF3333)
    private val ASSEMBLY = GizmoStyle.strokeAndFill(0xFF00FFFF.toInt(), 2.0f, 0x2200FFFF)

    fun extract(context: LevelExtractionContext) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        val level = context.level()
        val container = SubLevelContainer.getContainer(level) ?: return
        val partialTick = context.deltaTracker().getGameTimeDeltaPartialTick(true)
        Gizmos.withCollector(collector).use {
            val screen = minecraft.gui.screen()
            if (screen is CameraScreen) {
                val ids = screen.selectedIds
                var index = 0
                while (index < ids.size) {
                    val subLevel = container.getSubLevel(ids[index++]) as? ClientSubLevel ?: continue
                    outline(subLevel, partialTick, SELECTED)
                }
            }
            extractTool(level, player, player.mainHandItem, partialTick)
            if (!player.offhandItem.isEmpty) extractTool(level, player, player.offhandItem, partialTick)
        }
        context.levelRenderer().addMainThreadGizmos(collector.drainGizmos())
    }

    private fun extractTool(level: ClientLevel, player: LocalPlayer, stack: ItemStack, partialTick: Float) {
        when {
            stack.`is`(TouyItems.BUBBLE_BLOWER) -> {
                val start = stack.get(TouyComponents.ASSEMBLY_START) ?: return
                if (stack.get(TouyComponents.ASSEMBLY_DIMENSION) != level.dimension().identifier().toString()) return
                val hit = SubLevelTargeting.hit(level, player) ?: return
                val end = hit.blockPos
                val startGrid = Sable.HELPER.getContaining(level, start)
                val endGrid = Sable.HELPER.getContaining(level, end)
                if (startGrid !== endGrid || startGrid?.uniqueId != stack.get(TouyComponents.ASSEMBLY_SUB_LEVEL) ||
                    startGrid?.isRemoved == true || (startGrid == null && Sable.HELPER.isInPlotGrid(level, start))) return
                val bounds = AABB(min(start.x, end.x).toDouble(), min(start.y, end.y).toDouble(), min(start.z, end.z).toDouble(),
                    max(start.x, end.x) + 1.0, max(start.y, end.y) + 1.0, max(start.z, end.z) + 1.0)
                box(bounds, (startGrid as? ClientSubLevel)?.renderPose(partialTick), ASSEMBLY)
            }
            stack.`is`(TouyItems.CLONE_GUN) -> {
                val copied = stack.get(TouyComponents.COPIED_PLOT)
                if (copied != null && !player.isShiftKeyDown) {
                    val hit = SubLevelTargeting.hit(level, player) ?: return
                    val pose = SubLevelTemplate.placementPose(level, player, hit, copied)
                    box(SubLevelTemplate.previewBounds(copied), pose, TRACKED)
                } else (SubLevelTargeting.target(level, player) as? ClientSubLevel)?.let { outline(it, partialTick, TRACKED) }
            }
            stack.`is`(TouyItems.EVIL_ASS_ORB) -> {
                val hit = SubLevelTargeting.hit(level, player, partialTick)
                if (hit != null) {
                    (Sable.HELPER.getContaining(level, hit.blockPos) as? ClientSubLevel)?.let {
                        outline(it, partialTick, REMOVAL)
                    }
                }
                val minecraft = Minecraft.getInstance()
                if (!minecraft.options.cameraType.isFirstPerson) return
                val beamAlpha = OrbAnimation.beamAlpha(minecraft)
                if (beamAlpha <= 0f) return
                val camera = minecraft.gameRenderer.mainCamera()
                if (!camera.isInitialized) return
                val forward = camera.forwardVector()
                val right = Vec3(camera.leftVector()).scale(-1.0)
                val up = camera.upVector()
                val position = camera.position()
                val muzzle = position.add(
                    forward.x() * 0.65 + right.x() * 0.36 - up.x() * 0.16,
                    forward.y() * 0.65 + right.y() * 0.36 - up.y() * 0.16,
                    forward.z() * 0.65 + right.z() * 0.36 - up.z() * 0.16
                )
                val endpoint = if (hit == null) position.add(
                    forward.x() * 80.0, forward.y() * 80.0, forward.z() * 80.0
                ) else Sable.HELPER.projectOutOfSubLevel(level, hit.location)
                Gizmos.addGizmo(OrbBeam(muzzle, endpoint, right, Vec3(up), beamAlpha)).setAlwaysOnTop()
            }
            stack.`is`(TouyItems.TAPE) || stack.`is`(TouyItems.CAMCORDER) -> {
                val tracked = stack.get(TouyComponents.TRACKED_SUB_LEVEL)
                val target = if (tracked == null) SubLevelTargeting.target(level, player)
                    else SubLevelContainer.getContainer(level)?.getSubLevel(tracked)
                (target as? ClientSubLevel)?.let { outline(it, partialTick, TRACKED) }
            }
        }
    }

    private fun outline(subLevel: ClientSubLevel, partialTick: Float, style: GizmoStyle) {
        if (subLevel.isRemoved) return
        val bounds = subLevel.plot.boundingBox
        box(AABB(bounds.minX().toDouble(), bounds.minY().toDouble(), bounds.minZ().toDouble(),
            bounds.maxX() + 1.0, bounds.maxY() + 1.0, bounds.maxZ() + 1.0), subLevel.renderPose(partialTick), style)
    }

    private fun box(bounds: AABB, pose: Pose3dc?, style: GizmoStyle) {
        if (pose == null) {
            Gizmos.cuboid(bounds, style).setAlwaysOnTop()
            return
        }
        val corners = Array(8) { index ->
            transformScratch.set(if (index and 1 == 0) bounds.minX else bounds.maxX,
                if (index and 2 == 0) bounds.minY else bounds.maxY,
                if (index and 4 == 0) bounds.minZ else bounds.maxZ)
            pose.transformPosition(transformScratch)
            Vec3(transformScratch.x, transformScratch.y, transformScratch.z)
        }
        Gizmos.addGizmo(OrientedBox(corners, style)).setAlwaysOnTop()
    }

    private class OrbBeam(
        private val origin: Vec3,
        private val endpoint: Vec3,
        private val right: Vec3,
        private val up: Vec3,
        private val beamAlpha: Float
    ) : Gizmo {
        override fun emit(primitives: GizmoPrimitives, alpha: Float) {
            val color = (0xCC * (alpha * beamAlpha)).toInt().coerceIn(0, 0xCC) shl 24 or 0x00FF1A0F
            val baseHalfWidth = 0.10
            val baseHalfHeight = 0.08
            val tipHalfWidth = 0.018
            val tipHalfHeight = 0.018
            val baseRight = right.scale(baseHalfWidth)
            val baseUp = up.scale(baseHalfHeight)
            val tipRight = right.scale(tipHalfWidth)
            val tipUp = up.scale(tipHalfHeight)
            val baseTopRight = origin.add(baseRight).add(baseUp)
            val baseTopLeft = origin.subtract(baseRight).add(baseUp)
            val baseBottomLeft = origin.subtract(baseRight).subtract(baseUp)
            val baseBottomRight = origin.add(baseRight).subtract(baseUp)
            val tipTopRight = endpoint.add(tipRight).add(tipUp)
            val tipTopLeft = endpoint.subtract(tipRight).add(tipUp)
            val tipBottomLeft = endpoint.subtract(tipRight).subtract(tipUp)
            val tipBottomRight = endpoint.add(tipRight).subtract(tipUp)
            quad(primitives, baseTopRight, baseTopLeft, tipTopLeft, tipTopRight, color)
            quad(primitives, baseTopLeft, baseBottomLeft, tipBottomLeft, tipTopLeft, color)
            quad(primitives, baseBottomLeft, baseBottomRight, tipBottomRight, tipBottomLeft, color)
            quad(primitives, baseBottomRight, baseTopRight, tipTopRight, tipBottomRight, color)
        }

        private fun quad(primitives: GizmoPrimitives, a: Vec3, b: Vec3, c: Vec3, d: Vec3, color: Int) {
            primitives.addQuad(a, b, c, d, color)
            primitives.addQuad(d, c, b, a, color)
        }
    }

    /** Each instance owns a snapshot so it remains safe when rendering uses a different thread. */
    private class OrientedBox(private val corners: Array<Vec3>, private val style: GizmoStyle) : Gizmo {
        override fun emit(primitives: GizmoPrimitives, alpha: Float) {
            if (style.hasStroke()) {
                var index = 0
                while (index < 8) {
                    if (index and 1 == 0) primitives.addLine(corners[index], corners[index or 1], style.multipliedStroke(alpha), style.strokeWidth())
                    if (index and 2 == 0) primitives.addLine(corners[index], corners[index or 2], style.multipliedStroke(alpha), style.strokeWidth())
                    if (index and 4 == 0) primitives.addLine(corners[index], corners[index or 4], style.multipliedStroke(alpha), style.strokeWidth())
                    index++
                }
            }
            if (style.hasFill()) {
                val color = style.multipliedFill(alpha)
                primitives.addQuad(corners[0], corners[2], corners[3], corners[1], color)
                primitives.addQuad(corners[4], corners[5], corners[7], corners[6], color)
                primitives.addQuad(corners[0], corners[1], corners[5], corners[4], color)
                primitives.addQuad(corners[2], corners[6], corners[7], corners[3], color)
                primitives.addQuad(corners[0], corners[4], corners[6], corners[2], color)
                primitives.addQuad(corners[1], corners[3], corners[7], corners[5], color)
            }
        }
    }
}
