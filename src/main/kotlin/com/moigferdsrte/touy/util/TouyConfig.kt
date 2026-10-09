package com.moigferdsrte.touy.util

import com.moigferdsrte.touy.SableTouy
import java.nio.file.Files
import java.nio.file.Path

/** Configuration I/O is polled once per second, never once per dimension or physics substep. */
object TouyConfig {
    @Volatile private var values = ConfigValues()
    val itemRange: Int get() = values.itemRange
    val assemblyMaxBlocks: Int get() = values.assemblyMaxBlocks
    val cloneMaxBlocks: Int get() = values.cloneMaxBlocks
    val crowbarStrength: Double get() = values.crowbarStrength
    val recordingMaxDuration: Int get() = values.recordingMaxDuration
    val recordingInactiveTicks: Int get() = values.recordingInactiveTicks
    val recordingMaxSessions: Int get() = values.recordingMaxSessions
    val maxSnapshotSubLevels: Int get() = values.maxSnapshotSubLevels
    private var lastModified: Long = Long.MIN_VALUE
    private var lastSize: Long = -1
    private var file: Path? = null

    fun load(path: Path = Path.of("config", "touys.json5")) {
        file = path
        try {
            path.toAbsolutePath().parent?.let(Files::createDirectories)
            if (!Files.exists(path)) Files.writeString(path, DEFAULT_CONFIG)
            reloadIfChanged(force = true)
        } catch (e: Exception) { SableTouy.LOGGER.warn("Unable to load Touys config at {}", path, e) }
    }

    fun reloadIfChanged(force: Boolean = false): Boolean {
        val path = file ?: return false
        return try {
            if (!Files.exists(path)) return false
            val modified = Files.getLastModifiedTime(path).toMillis()
            val size = Files.size(path)
            if (!force && modified == lastModified && size == lastSize) return true
            // Remember a rejected revision to avoid flooding the log on every poll.
            lastModified = modified
            lastSize = size
            require(size <= 65_536) { "Configuration exceeds 64 KiB" }
            val candidate = ConfigValues.parse(Files.readString(path))
            values = candidate
            SableTouy.LOGGER.info("Reloaded Touys config: {}", candidate)
            true
        } catch (e: Exception) {
            SableTouy.LOGGER.warn("Rejected Touys config at {}; keeping the last valid configuration: {}", path, e.message)
            false
        }
    }

    fun snapshot(): ConfigValues = values

    private val DEFAULT_CONFIG = """
        {
          // Maximum targeting distance, in blocks.
          item_range: 80,
          assembly_max_blocks: 65536,
          clone_max_blocks: 65536,
          crowbar_strength: 400,
          // -1 uses the hard memory limit of 12000 ticks (10 minutes).
          recording_max_duration: 6000,
          // -1 disables the inactivity timeout.
          recording_inactive_ticks: 2400,
          recording_max_sessions: 16,
          max_snapshot_sub_levels: 128,
        }
    """.trimIndent() + "\n"
}
