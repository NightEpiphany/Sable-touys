package com.moigferdsrte.touy.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

class ConfigReloadTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `hot reload applies whole valid snapshot and preserves it after an invalid edit`() {
        val file = directory.resolve("touys.json5")
        Files.writeString(file, "{item_range: 60, crowbar_strength: 200}")
        TouyConfig.load(file)
        val valid = TouyConfig.snapshot()
        assertEquals(60, valid.itemRange)
        assertEquals(200.0, valid.crowbarStrength)
        Files.writeString(file, "{item_range: 120, crowbar_strength: Infinity}")
        assertFalse(TouyConfig.reloadIfChanged(force = true))
        assertSame(valid, TouyConfig.snapshot())
        Files.writeString(file, "{item_range: 120, crowbar_strength: 300}")
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 2_000))
        assertTrue(TouyConfig.reloadIfChanged())
        assertEquals(120, TouyConfig.itemRange)
        assertEquals(300.0, TouyConfig.crowbarStrength)
        TouyConfig.load(directory.resolve("defaults.json5"))
    }
}
