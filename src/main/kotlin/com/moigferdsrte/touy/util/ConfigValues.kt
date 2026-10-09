package com.moigferdsrte.touy.util

/** One immutable snapshot makes a reload visible to every tool atomically. */
data class ConfigValues(
    val itemRange: Int = 80,
    val assemblyMaxBlocks: Int = 65_536,
    val cloneMaxBlocks: Int = 65_536,
    val crowbarStrength: Double = 400.0,
    val recordingMaxDuration: Int = 6_000,
    val recordingInactiveTicks: Int = 2_400,
    val recordingMaxSessions: Int = 16,
    val maxSnapshotSubLevels: Int = 128,
) {
    companion object {
        fun parse(text: String): ConfigValues {
            val root = Json5.parse(text) as? Map<*, *> ?: throw IllegalArgumentException("Configuration must be a JSON5 object")
            val defaults = ConfigValues()
            fun number(key: String, fallback: Number): Number {
                if (!root.containsKey(key)) return fallback
                return root[key] as? Number ?: throw IllegalArgumentException("$key must be a number")
            }
            fun integer(key: String, fallback: Int, min: Int, max: Int): Int {
                val value = number(key, fallback).toDouble()
                require(value.isFinite() && value % 1.0 == 0.0 && value >= min && value <= max) { "$key must be an integer in $min..$max" }
                return value.toInt()
            }
            val strength = number("crowbar_strength", defaults.crowbarStrength).toDouble()
            require(strength.isFinite() && strength in 0.0..4096.0) { "crowbar_strength must be finite and in 0..4096" }
            return ConfigValues(
                integer("item_range", defaults.itemRange, 1, 256),
                integer("assembly_max_blocks", defaults.assemblyMaxBlocks, 1, 65_536),
                integer("clone_max_blocks", defaults.cloneMaxBlocks, 1, 65_536), strength,
                integer("recording_max_duration", defaults.recordingMaxDuration, -1, 12_000),
                integer("recording_inactive_ticks", defaults.recordingInactiveTicks, -1, Int.MAX_VALUE),
                integer("recording_max_sessions", defaults.recordingMaxSessions, 1, 64),
                integer("max_snapshot_sub_levels", defaults.maxSnapshotSubLevels, 1, 128),
            )
        }
    }
}
