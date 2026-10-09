package com.moigferdsrte.touy.core

import com.mojang.serialization.Codec
import com.moigferdsrte.touy.SableTouy
import net.minecraft.core.BlockPos
import net.minecraft.core.Registry
import net.minecraft.core.UUIDUtil
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import java.util.UUID

object TouyComponents {
    lateinit var ITEM_DESCRIPTION: DataComponentType<Int>; private set
    lateinit var ASSEMBLY_START: DataComponentType<BlockPos>; private set
    lateinit var ASSEMBLY_DIMENSION: DataComponentType<String>; private set
    lateinit var ASSEMBLY_SUB_LEVEL: DataComponentType<UUID>; private set
    lateinit var TRACKED_SUB_LEVEL: DataComponentType<UUID>; private set
    lateinit var RECORDING_TICKS: DataComponentType<Int>; private set
    lateinit var RECORDING: DataComponentType<CompoundTag>; private set
    lateinit var SNAPSHOT: DataComponentType<CompoundTag>; private set
    lateinit var COPIED_PLOT: DataComponentType<CompoundTag>; private set
    lateinit var CUSTOM_CROWBAR: DataComponentType<Boolean>; private set

    fun register() {
        ITEM_DESCRIPTION = register("item_description", Codec.INT, ByteBufCodecs.INT)
        ASSEMBLY_START = register("assembly_start", BlockPos.CODEC, BlockPos.STREAM_CODEC)
        ASSEMBLY_DIMENSION = register("assembly_dimension", Codec.STRING, ByteBufCodecs.STRING_UTF8)
        ASSEMBLY_SUB_LEVEL = register("assembly_sub_level", UUIDUtil.CODEC, UUIDUtil.STREAM_CODEC)
        TRACKED_SUB_LEVEL = register("tracked_sub_level", UUIDUtil.CODEC, UUIDUtil.STREAM_CODEC)
        RECORDING_TICKS = register("recording_ticks", Codec.INT, ByteBufCodecs.INT)
        RECORDING = register("recording", CompoundTag.CODEC, ByteBufCodecs.COMPOUND_TAG)
        SNAPSHOT = register("snapshot", CompoundTag.CODEC, ByteBufCodecs.COMPOUND_TAG)
        COPIED_PLOT = register("copied_plot", CompoundTag.CODEC, ByteBufCodecs.COMPOUND_TAG)
        CUSTOM_CROWBAR = register("custom_crowbar", Codec.BOOL, ByteBufCodecs.BOOL)
    }

    private fun <T : Any> register(path: String, codec: Codec<T>, stream: StreamCodec<in RegistryFriendlyByteBuf, T>): DataComponentType<T> =
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, SableTouy.id(path),
            DataComponentType.builder<T>().persistent(codec).networkSynchronized(stream).build())
}
