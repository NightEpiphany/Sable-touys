package com.moigferdsrte.touy.util

import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.companion.math.BoundingBox3d
import dev.ryanhcode.sable.companion.math.Pose3d
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import org.joml.Vector3d

/** Clones Sable's public plot format, including block entities and scheduled ticks. */
object SubLevelTemplate {
    private const val VERSION = 1
    private const val MAX_TEMPLATE_BYTES = 1_048_576

    fun of(subLevel: ServerSubLevel, player: Player): CompoundTag {
        require(!subLevel.isRemoved) { "Cannot copy a removed sub-level" }
        val plot = subLevel.plot
        val bounds = plot.boundingBox
        require(bounds.volume() > 0) { "Cannot copy an empty plot" }
        require(SelectionLimits.volume(bounds.minX(), bounds.minY(), bounds.minZ(),
            bounds.maxX(), bounds.maxY(), bounds.maxZ(), TouyConfig.cloneMaxBlocks.toLong()) > 0
        ) { "Clone exceeds the configured block limit" }

        val level = subLevel.level
        val cursor = BlockPos.MutableBlockPos()
        for (x in bounds.minX()..bounds.maxX()) {
            for (y in bounds.minY()..bounds.maxY()) {
                for (z in bounds.minZ()..bounds.maxZ()) {
                    cursor.set(x, y, z)
                    if (!level.getBlockState(cursor).isAir) {
                        require(SubLevelTargeting.canUse(level, player, cursor)) { "Protected clone source" }
                    }
                }
            }
        }

        val tag = CompoundTag()
        tag.putInt("template_version", VERSION)
        tag.putString("name", (subLevel.name ?: "Sub-level").take(128))
        val look = player.lookAngle
        val localLook = subLevel.logicalPose().transformNormalInverse(Vector3d(look.x, look.y, look.z))
        val direction = if (localLook.x * localLook.x + localLook.z * localLook.z < 1.0e-12)
            Direction.fromYRot(player.yRot.toDouble())
        else Direction.getApproximateNearest(localLook.x, 0.0, localLook.z)
        tag.putString("direction", direction.serializedName)
        tag.putInt("size_x", bounds.maxX() - bounds.minX() + 1)
        tag.putInt("size_y", bounds.maxY() - bounds.minY() + 1)
        tag.putInt("size_z", bounds.maxZ() - bounds.minZ() + 1)
        tag.putInt("min_x", bounds.minX())
        tag.putInt("min_y", bounds.minY())
        tag.putInt("min_z", bounds.minZ())
        tag.putInt("max_x", bounds.maxX())
        tag.putInt("max_y", bounds.maxY())
        tag.putInt("max_z", bounds.maxZ())
        val origin = plot.toGlobal(ChunkPos(0, 0))
        tag.putInt("origin_x", origin.minBlockX)
        tag.putInt("origin_z", origin.minBlockZ)
        tag.putInt("source_min_y", level.minY)
        tag.putInt("source_sections", level.sectionsCount)
        tag.putString("source_dimension", level.dimension().identifier().toString())
        val savedPlot = plot.save()
        val chunks = savedPlot.getCompoundOrEmpty("chunks")
        // Empty lighting-margin chunks do not contribute anything to a copied contraption.
        chunks.keySet().removeIf { chunks.getCompoundOrEmpty(it).getCompoundOrEmpty("sections").isEmpty }
        tag.put("plot", savedPlot)
        require(tag.sizeInBytes() <= MAX_TEMPLATE_BYTES) { "Clone NBT exceeds 1 MiB" }
        return tag
    }

    /** The preview pose places the geometric center of the copied bounds at the hit location. */
    fun placementPose(level: Level, player: Player, hit: BlockHitResult, tag: CompoundTag): Pose3d {
        val direction = Direction.byName(tag.getStringOr("direction", "south")) ?: Direction.SOUTH
        require(direction.axis.isHorizontal) { "Invalid clone direction" }
        val height = tag.getIntOr("size_y", 0)
        require(height in 1..TouyConfig.cloneMaxBlocks) { "Invalid clone height" }
        val worldHit = Sable.HELPER.projectOutOfSubLevel(level, hit.location)
        return Pose3d().also {
            it.position().set(worldHit.x, worldHit.y + (height + 1.0) * 0.5, worldHit.z)
            it.orientation().rotationY(Math.toRadians(Direction.getYRot(direction).toDouble() - player.yRot))
        }
    }

    /** Centered local bounds, for an outline transformed by [placementPose]. */
    fun previewBounds(tag: CompoundTag): AABB {
        val x = tag.getIntOr("size_x", 0)
        val y = tag.getIntOr("size_y", 0)
        val z = tag.getIntOr("size_z", 0)
        require(x > 0 && y > 0 && z > 0 && SelectionLimits.volume(0, 0, 0,
            x - 1, y - 1, z - 1, TouyConfig.cloneMaxBlocks.toLong()) > 0
        ) { "Invalid clone dimensions" }
        return AABB(-x * 0.5, -y * 0.5, -z * 0.5, x * 0.5, y * 0.5, z * 0.5)
    }

    fun previewBounds(tag: CompoundTag, pose: Pose3d): AABB {
        val bounds = BoundingBox3d(previewBounds(tag)).transform(pose)
        return AABB(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ())
    }

    fun create(level: ServerLevel, pose: Pose3d, tag: CompoundTag, player: Player): ServerSubLevel {
        require(tag.getIntOr("template_version", 0) == VERSION) { "Unsupported clone version" }
        require(tag.sizeInBytes() <= MAX_TEMPLATE_BYTES) { "Clone NBT exceeds 1 MiB" }
        require(player.mayBuild() && !player.isSpectator) { "Player cannot build" }
        require(pose.position().isFinite && pose.orientation().isFinite) { "Invalid clone pose" }
        val container = requireNotNull(SubLevelContainer.getContainer(level)) { "Sable container unavailable" }
        val plotTag = requiredCompound(tag, "plot")
        val logSize = plotTag.getIntOr("log_size", -1)
        require(logSize == container.logPlotSize && logSize in 1..20) { "Incompatible plot size" }
        require(plotTag.getIntOr("data_version", -1) in 0..1) { "Unsupported Sable plot version" }
        val bounds = sourceBounds(tag)
        val worldBounds = previewBounds(tag, pose)
        require(worldBounds.minY >= level.minY && worldBounds.maxY <= level.maxY + 1.0 &&
            worldBounds.minX >= level.worldBorder.minX && worldBounds.maxX <= level.worldBorder.maxX &&
            worldBounds.minZ >= level.worldBorder.minZ && worldBounds.maxZ <= level.worldBorder.maxZ
        ) { "Clone would cross the build limit or world border" }
        validatePlot(level, pose, tag, plotTag, bounds, player)

        val subLevel = container.allocateNewSubLevel(pose) as ServerSubLevel
        try {
            val plot = subLevel.plot
            val destinationOrigin = plot.toGlobal(ChunkPos(0, 0))
            val offsetX = Math.subtractExact(destinationOrigin.minBlockX, requiredInt(tag, "origin_x"))
            val offsetZ = Math.subtractExact(destinationOrigin.minBlockZ, requiredInt(tag, "origin_z"))
            // Preserve absolute plot Y and remap section indices when dimension build heights differ.
            val sectionOffset = Math.floorDiv(requiredInt(tag, "source_min_y") - level.minY, 16)
            val crossDimension = tag.getStringOr("source_dimension", "") != level.dimension().identifier().toString()
            val relocated = relocatedPlot(plotTag, offsetX, offsetZ, sectionOffset, level.sectionsCount,
                crossDimension || requiredInt(tag, "source_sections") != level.sectionsCount)
            plot.load(relocated)
            require(!subLevel.isRemoved) { "Loaded clone was removed" }
            val loadedBounds = plot.boundingBox
            val centerOfMass = requireNotNull(subLevel.massTracker.centerOfMass) { "Loaded clone has no mass" }
            val offset = Vector3d(centerOfMass).sub(
                (loadedBounds.minX().toDouble() + loadedBounds.maxX() + 1.0) * 0.5,
                (loadedBounds.minY().toDouble() + loadedBounds.maxY() + 1.0) * 0.5,
                (loadedBounds.minZ().toDouble() + loadedBounds.maxZ() + 1.0) * 0.5)
            pose.orientation().transform(offset)
            val physicalPose = subLevel.logicalPose()
            physicalPose.position().set(pose.position()).add(offset)
            physicalPose.orientation().set(pose.orientation())
            physicalPose.rotationPoint().set(centerOfMass)
            physicalPose.scale().set(1.0, 1.0, 1.0)
            container.physicsSystem().pipeline.teleport(subLevel, physicalPose.position(), physicalPose.orientation())
            subLevel.name = tag.getStringOr("name", "Sub-level").take(128)
            subLevel.updateLastPose()
            subLevel.updateBoundingBox()
            return subLevel
        } catch (exception: RuntimeException) {
            try {
                container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED)
            } catch (cleanupFailure: RuntimeException) {
                exception.addSuppressed(cleanupFailure)
            }
            throw exception
        }
    }

    /** Deep-copy before relocating, so repeatedly pasting an item never changes its stored template. */
    internal fun relocatedPlot(plotTag: CompoundTag, offsetX: Int, offsetZ: Int,
        sectionOffset: Int = 0, targetSections: Int = Int.MAX_VALUE, invalidateLighting: Boolean = false
    ): CompoundTag {
        val relocated = plotTag.copy()
        for (chunkTag in relocated.getCompoundOrEmpty("chunks").values()) {
            require(chunkTag is CompoundTag) { "Invalid plot chunk" }
            for (key in arrayOf("block_entities", "block_ticks", "fluid_ticks")) {
                for (entry in chunkTag.getListOrEmpty(key)) {
                    require(entry is CompoundTag) { "Invalid $key entry" }
                    entry.putInt("x", Math.addExact(requiredInt(entry, "x"), offsetX))
                    entry.putInt("z", Math.addExact(requiredInt(entry, "z"), offsetZ))
                }
            }
            val sections = chunkTag.getCompoundOrEmpty("sections")
            val shifted = CompoundTag()
            for ((key, section) in sections.entrySet()) {
                val index = Math.addExact(key.toInt(), sectionOffset)
                require(index in 0 until targetSections && section is CompoundTag) { "Section outside destination height" }
                if (invalidateLighting) {
                    section.remove("BlockLight")
                    section.remove("SkyLight")
                }
                shifted.put(index.toString(), section)
            }
            chunkTag.put("sections", shifted)
            if (invalidateLighting || sectionOffset != 0) {
                chunkTag.remove("heightmaps")
                chunkTag.putBoolean("isLightOn", false)
            }
        }
        return relocated
    }

    private data class SourceBounds(val minX: Int, val minY: Int, val minZ: Int,
        val maxX: Int, val maxY: Int, val maxZ: Int
    ) {
        val centerX: Double get() = (minX.toDouble() + maxX + 1.0) * 0.5
        val centerY: Double get() = (minY.toDouble() + maxY + 1.0) * 0.5
        val centerZ: Double get() = (minZ.toDouble() + maxZ + 1.0) * 0.5
    }

    private fun sourceBounds(tag: CompoundTag): SourceBounds {
        val bounds = SourceBounds(requiredInt(tag, "min_x"), requiredInt(tag, "min_y"), requiredInt(tag, "min_z"),
            requiredInt(tag, "max_x"), requiredInt(tag, "max_y"), requiredInt(tag, "max_z"))
        require(bounds.maxX >= bounds.minX && bounds.maxY >= bounds.minY && bounds.maxZ >= bounds.minZ)
        require(SelectionLimits.volume(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY,
            bounds.maxZ, TouyConfig.cloneMaxBlocks.toLong()) > 0) { "Clone exceeds the configured block limit" }
        require(tag.getIntOr("size_x", 0).toLong() == bounds.maxX.toLong() - bounds.minX + 1 &&
            tag.getIntOr("size_y", 0).toLong() == bounds.maxY.toLong() - bounds.minY + 1 &&
            tag.getIntOr("size_z", 0).toLong() == bounds.maxZ.toLong() - bounds.minZ + 1
        ) { "Clone dimensions do not match its bounds" }
        return bounds
    }

    private fun validatePlot(level: ServerLevel, pose: Pose3d, template: CompoundTag,
        plot: CompoundTag, bounds: SourceBounds, player: Player
    ) {
        val gameMode = requireNotNull(player.gameMode()) { "Player game mode unavailable" }
        val originX = requiredInt(template, "origin_x")
        val originZ = requiredInt(template, "origin_z")
        val sourceMinY = requiredInt(template, "source_min_y")
        val sourceSections = requiredInt(template, "source_sections")
        require(sourceMinY % 16 == 0 && sourceSections in 1..4096) { "Invalid source build height" }
        val plotSize = 1L shl (plot.getIntOr("log_size", -1) + 4)
        require(bounds.minX >= originX && bounds.maxX.toLong() < originX.toLong() + plotSize &&
            bounds.minZ >= originZ && bounds.maxZ.toLong() < originZ.toLong() + plotSize)
        val codec = level.palettedContainerFactory().blockStatesContainerCodec()
        val worldPoint = Vector3d()
        val worldPos = BlockPos.MutableBlockPos()
        var count = 0
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE; var maxY = Int.MIN_VALUE; var maxZ = Int.MIN_VALUE
        for ((chunkKey, chunkValue) in requiredCompound(plot, "chunks").entrySet()) {
            require(chunkValue is CompoundTag) { "Invalid plot chunk" }
            val chunkKeyLong = chunkKey.toLong()
            val chunkX = ChunkPos.getX(chunkKeyLong)
            val chunkZ = ChunkPos.getZ(chunkKeyLong)
            require(chunkX in 0 until (plotSize / 16).toInt() && chunkZ in 0 until (plotSize / 16).toInt())
            val baseX = Math.addExact(originX, chunkX * 16)
            val baseZ = Math.addExact(originZ, chunkZ * 16)
            require(baseX.toLong() <= bounds.maxX && baseX.toLong() + 15 >= bounds.minX &&
                baseZ.toLong() <= bounds.maxZ && baseZ.toLong() + 15 >= bounds.minZ) { "Chunk outside clone bounds" }
            for ((sectionKey, sectionValue) in requiredCompound(chunkValue, "sections").entrySet()) {
                require(sectionValue is CompoundTag) { "Invalid plot section" }
                val index = sectionKey.toInt()
                require(index in 0 until sourceSections) { "Invalid source section" }
                val baseY = Math.addExact(sourceMinY, index * 16)
                require(baseY.toLong() <= bounds.maxY && baseY.toLong() + 15 >= bounds.minY) { "Section outside clone bounds" }
                val states = codec.parse(NbtOps.INSTANCE, requiredCompound(sectionValue, "block_states")).getOrThrow()
                for (x in 0..15) for (y in 0..15) for (z in 0..15) {
                    if (states.get(x, y, z).isAir) continue
                    require(++count <= TouyConfig.cloneMaxBlocks) { "Clone exceeds the configured block limit" }
                    val blockX = baseX + x; val blockY = baseY + y; val blockZ = baseZ + z
                    require(blockX in bounds.minX..bounds.maxX && blockY in bounds.minY..bounds.maxY &&
                        blockZ in bounds.minZ..bounds.maxZ) { "Block outside clone bounds" }
                    minX = minOf(minX, blockX); minY = minOf(minY, blockY); minZ = minOf(minZ, blockZ)
                    maxX = maxOf(maxX, blockX); maxY = maxOf(maxY, blockY); maxZ = maxOf(maxZ, blockZ)
                    worldPoint.set(blockX + 0.5 - bounds.centerX, blockY + 0.5 - bounds.centerY, blockZ + 0.5 - bounds.centerZ)
                    pose.transformPosition(worldPoint)
                    worldPos.set(Mth.floor(worldPoint.x), Mth.floor(worldPoint.y), Mth.floor(worldPoint.z))
                    require(level.isLoaded(worldPos) && level.mayInteract(player, worldPos) &&
                        !player.blockActionRestricted(level, worldPos, gameMode)) { "Protected or unloaded clone destination" }
                }
            }
        }
        require(count > 0 && bounds == SourceBounds(minX, minY, minZ, maxX, maxY, maxZ)) { "Clone bounds do not match its blocks" }
    }

    private fun requiredInt(tag: CompoundTag, key: String): Int =
        tag.getInt(key).orElseThrow { IllegalArgumentException("Missing clone field: $key") }

    private fun requiredCompound(tag: CompoundTag, key: String): CompoundTag =
        tag.getCompound(key).orElseThrow { IllegalArgumentException("Missing clone field: $key") }
}
