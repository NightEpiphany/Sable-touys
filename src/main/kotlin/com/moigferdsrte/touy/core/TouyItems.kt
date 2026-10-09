package com.moigferdsrte.touy.core

import com.moigferdsrte.touy.SableTouy
import com.moigferdsrte.touy.item.*
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item

object TouyItems {
    lateinit var BUBBLE_BLOWER: Item; private set
    lateinit var CAMCORDER: Item; private set
    lateinit var TAPE: Item; private set
    lateinit var CAMERA: Item; private set
    lateinit var PHOTO: Item; private set
    lateinit var CLONE_GUN: Item; private set
    lateinit var CROWBAR: Item; private set
    lateinit var EVIL_ASS_ORB: Item; private set
    lateinit var TAB: CreativeModeTab; private set

    fun register() {
        BUBBLE_BLOWER = item("bubble_blower") { BubbleBlowerItem(it, 2) }
        CAMCORDER = item("camcorder") { CamcorderItem(it, 2) }
        TAPE = item("tape") { TapeItem(it, 2) }
        CAMERA = item("camera") { CameraItem(it, 1) }
        PHOTO = item("photo") { PhotoItem(it, 1) }
        CLONE_GUN = item("clone_gun") { CloneGunItem(it, 2) }
        CROWBAR = item("crowbar") { CrowbarItem(it, 2) }
        EVIL_ASS_ORB = item("evil_ass_orb") { EvilAssOrbItem(it, 1) }
        TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, SableTouy.id("touys"),
            CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0).title(Component.translatable("itemGroup.touys"))
                .icon { CLONE_GUN.defaultInstance }.displayItems { _, output ->
                    output.accept(CLONE_GUN); output.accept(EVIL_ASS_ORB)
                    output.accept(BUBBLE_BLOWER); output.accept(CROWBAR)
                    output.accept(CAMCORDER); output.accept(CAMERA)
                    output.accept(TAPE); output.accept(PHOTO); output.accept(TouyBlocks.PROJECTOR_ITEM)
                }.build())
    }

    private fun <T : Item> item(path: String, factory: (Item.Properties) -> T): T {
        val id = SableTouy.id(path)
        return Registry.register(BuiltInRegistries.ITEM, id,
            factory(Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).stacksTo(1)))
    }
}