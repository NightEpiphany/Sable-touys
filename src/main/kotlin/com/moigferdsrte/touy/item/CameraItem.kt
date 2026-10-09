package com.moigferdsrte.touy.item

import com.moigferdsrte.touy.core.TouyItems
import com.moigferdsrte.touy.core.recording.PoseDataCodec
import com.moigferdsrte.touy.core.recording.PoseRecording
import com.moigferdsrte.touy.core.recording.PrimitivePoseBuffer
import com.moigferdsrte.touy.util.TouyConfig
import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import java.util.UUID

class CameraItem(properties: Properties, descriptionLines: Int) : TouyItem(properties, descriptionLines) {
    // The client callback opens the selector; its packet is validated by capture on the server.
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult = InteractionResult.SUCCESS

    companion object {
        const val MAX_CAPTURE_TARGETS = PoseDataCodec.MAX_SNAPSHOT_TARGETS

        @JvmStatic
        fun capture(level: ServerLevel, player: ServerPlayer, ids: List<UUID>): Boolean {
            val limit = TouyConfig.maxSnapshotSubLevels.coerceIn(1, MAX_CAPTURE_TARGETS)
            if (!player.isAlive || player.isSpectator || player.level() !== level ||
                (!player.mainHandItem.`is`(TouyItems.CAMERA) && !player.offhandItem.`is`(TouyItems.CAMERA)) ||
                ids.isEmpty() || ids.size > limit || ids.toSet().size != ids.size) return false
            val container = SubLevelContainer.getContainer(level) ?: return false
            val range = TouyConfig.itemRange.toDouble()
            val snapshot = LinkedHashMap<UUID, PoseRecording>(ids.size)
            for (id in ids) {
                val target = container.getSubLevel(id) as? ServerSubLevel ?: return invalid(player)
                if (target.isRemoved || !withinRange(target, player, range)) return invalid(player)
                val pose = target.logicalPose()
                val p = pose.position()
                val q = pose.orientation()
                val buffer = PrimitivePoseBuffer(1)
                if (!buffer.append(p.x, p.y, p.z, q.x, q.y, q.z, q.w)) return invalid(player)
                snapshot[id] = buffer.snapshot() ?: return invalid(player)
            }
            CamcorderItem.give(level, player, PhotoItem.create(RecordingService.dimension(level), snapshot))
            player.sendOverlayMessage(Component.translatable("touys.message.snapshot_saved", snapshot.size))
            return true
        }

        private fun withinRange(target: ServerSubLevel, player: Player, range: Double): Boolean {
            val bounds = target.boundingBox()
            val position = Sable.HELPER.getEyePositionInterpolated(player, 1.0f)
            if (!bounds.minX().isFinite() || !bounds.minY().isFinite() || !bounds.minZ().isFinite() ||
                !bounds.maxX().isFinite() || !bounds.maxY().isFinite() || !bounds.maxZ().isFinite() ||
                bounds.minX() > bounds.maxX() || bounds.minY() > bounds.maxY() || bounds.minZ() > bounds.maxZ()) return false
            val x = position.x.coerceIn(bounds.minX(), bounds.maxX())
            val y = position.y.coerceIn(bounds.minY(), bounds.maxY())
            val z = position.z.coerceIn(bounds.minZ(), bounds.maxZ())
            val dx = position.x - x
            val dy = position.y - y
            val dz = position.z - z
            return dx * dx + dy * dy + dz * dz <= range * range
        }

        private fun invalid(player: ServerPlayer): Boolean {
            player.sendOverlayMessage(Component.translatable("touys.message.capture_invalid"))
            return false
        }
    }
}
