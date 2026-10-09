package com.moigferdsrte.touy.util

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubLevelTemplateTest {
    @Test
    fun `moves block entities and both tick lists without changing inventory or source`() {
        val original = plot()
        val snapshot = original.copy()
        val relocated = SubLevelTemplate.relocatedPlot(original, 512, -256)
        val chunk = relocated.getCompoundOrEmpty("chunks").getCompoundOrEmpty("0")
        for (key in listOf("block_entities", "block_ticks", "fluid_ticks")) {
            val entry = chunk.getListOrEmpty(key).getCompoundOrEmpty(0)
            assertEquals(552, entry.getIntOr("x", 0))
            assertEquals(120, entry.getIntOr("y", 0))
            assertEquals(-208, entry.getIntOr("z", 0))
        }
        val chest = chunk.getListOrEmpty("block_entities").getCompoundOrEmpty(0)
        assertEquals("minecraft:diamond", chest.getListOrEmpty("Items").getCompoundOrEmpty(0).getStringOr("id", ""))
        assertEquals(19, chest.getListOrEmpty("Items").getCompoundOrEmpty(0).getIntOr("count", 0))
        assertEquals(37, chunk.getListOrEmpty("block_ticks").getCompoundOrEmpty(0).getIntOr("t", 0))
        assertEquals(snapshot, original)
        val second = SubLevelTemplate.relocatedPlot(original, -32, 64)
            .getCompoundOrEmpty("chunks").getCompoundOrEmpty("0")
            .getListOrEmpty("block_entities").getCompoundOrEmpty(0)
        assertEquals(8, second.getIntOr("x", 0))
        assertEquals(112, second.getIntOr("z", 0))
    }

    @Test
    fun `remaps section height and rebuilds lighting only on the copy`() {
        val original = plot()
        val relocated = SubLevelTemplate.relocatedPlot(original, 0, 0, -4, 16, true)
        val chunk = relocated.getCompoundOrEmpty("chunks").getCompoundOrEmpty("0")
        val sections = chunk.getCompoundOrEmpty("sections")
        assertTrue(sections.contains("7"))
        assertFalse(sections.contains("11"))
        assertFalse(sections.getCompoundOrEmpty("7").contains("SkyLight"))
        assertFalse(chunk.contains("heightmaps"))
        assertTrue(original.getCompoundOrEmpty("chunks").getCompoundOrEmpty("0")
            .getCompoundOrEmpty("sections").getCompoundOrEmpty("11").contains("SkyLight"))
    }

    @Test
    fun `rejects destination height and integer coordinate overflow`() {
        val original = plot()
        assertThrows(IllegalArgumentException::class.java) {
            SubLevelTemplate.relocatedPlot(original, 0, 0, -12, 16)
        }
        assertThrows(ArithmeticException::class.java) {
            SubLevelTemplate.relocatedPlot(original, Int.MAX_VALUE, 0)
        }
        assertEquals(40, original.getCompoundOrEmpty("chunks").getCompoundOrEmpty("0")
            .getListOrEmpty("block_entities").getCompoundOrEmpty(0).getIntOr("x", 0))
    }

    private fun plot(): CompoundTag {
        val plot = CompoundTag()
        val chunks = CompoundTag()
        val chunk = CompoundTag()
        for (key in listOf("block_entities", "block_ticks", "fluid_ticks")) {
            val list = ListTag()
            val entry = CompoundTag()
            entry.putInt("x", 40)
            entry.putInt("y", 120)
            entry.putInt("z", 48)
            entry.putInt("t", 37)
            if (key == "block_entities") {
                val items = ListTag()
                val item = CompoundTag()
                item.putString("id", "minecraft:diamond")
                item.putInt("count", 19)
                items.add(item)
                entry.put("Items", items)
            }
            list.add(entry)
            chunk.put(key, list)
        }
        val sections = CompoundTag()
        val section = CompoundTag()
        section.put("block_states", CompoundTag())
        section.putByteArray("SkyLight", byteArrayOf(1, 2, 3))
        sections.put("11", section)
        chunk.put("sections", sections)
        chunk.put("heightmaps", CompoundTag())
        chunks.put("0", chunk)
        plot.put("chunks", chunks)
        return plot
    }
}
