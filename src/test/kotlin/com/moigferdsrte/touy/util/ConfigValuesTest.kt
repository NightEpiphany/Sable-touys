package com.moigferdsrte.touy.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ConfigValuesTest {
    @Test
    fun `JSON5 configuration accepts comments trailing commas hex and signed decimals`() {
        val config = ConfigValues.parse("""
            { // bounded selection
              item_range: 0x50, assembly_max_blocks: 65536,
              'recording_max_duration': -1,
              /* remaining values use defaults */
            }
        """.trimIndent())
        assertEquals(80, config.itemRange)
        assertEquals(-1, config.recordingMaxDuration)
        assertEquals(16, config.recordingMaxSessions)
    }

    @Test
    fun `legacy crowbar configuration cannot change the fixed item strength`() {
        assertEquals(ConfigValues(), ConfigValues.parse("{crowbar_strength: 400}"))
    }

    @Test
    fun `unsafe numbers invalid types and out of range limits are rejected`() {
        listOf("{item_range: NaN}", "{item_range: Infinity}", "{item_range: 80.5}",
            "{item_range: 257}", "{clone_max_blocks: 65537}", "{recording_max_sessions: 0}",
            "{recording_max_duration: 12001}",
            "{max_snapshot_sub_levels: 129}", "{item_range: '80'}", "[]").forEach { text ->
            assertThrows(IllegalArgumentException::class.java, { ConfigValues.parse(text) }, text)
        }
    }

    @Test
    fun `incomplete duplicated and excessive JSON5 inputs are rejected`() {
        listOf("{item_range: 80, item_range: 81}", "{item_range:}", "{item_range:80}extra",
            "{/*unfinished", "{a:'unfinished}", "[".repeat(34) + "0" + "]".repeat(34),
            " ".repeat(65537)).forEach { text ->
            assertThrows(IllegalArgumentException::class.java, { Json5.parse(text) })
        }
    }

    @Test
    fun `strings and nested JSON5 values preserve escaped contents`() {
        val parsed = Json5.parse("""{name:'a\'b', path:"a\\b", data:[true,null, .5,], unicode:'\u4e2d'}""") as Map<*, *>
        assertEquals("a'b", parsed["name"])
        assertEquals("a\\b", parsed["path"])
        assertEquals(listOf(true, null, 0.5), parsed["data"])
        assertEquals("中", parsed["unicode"])
    }
}
