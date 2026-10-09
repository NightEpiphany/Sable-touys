package com.moigferdsrte.touy.item

import com.moigferdsrte.touy.SableTouy
import com.moigferdsrte.touy.core.TouyComponents
import com.moigferdsrte.touy.util.SubLevelTargeting
import com.moigferdsrte.touy.util.TouyConfig
import dev.ryanhcode.sable.Sable
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle
import dev.ryanhcode.sable.index.SableAttributes
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.EquipmentSlotGroup
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.SlotAccess
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ClickAction
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemAttributeModifiers
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import org.joml.Vector3d

/** The Sable punch attribute strengthens normal left-click punches at the actual hit point. */
class CrowbarItem(properties: Properties, descriptionLines: Int) :
    TouyItem(properties.attributes(defaultAttributes()), descriptionLines) {

    override fun inventoryTick(stack: ItemStack, level: ServerLevel, entity: Entity, slot: EquipmentSlot?) {
        super.inventoryTick(stack, level, entity, slot)
        if (entity.tickCount % 20 == 0) refreshDefault(stack)
    }

    override fun canDestroyBlock(stack: ItemStack, state: BlockState, level: Level,
        pos: BlockPos, entity: LivingEntity
    ): Boolean = false

    override fun overrideOtherStackedOnMe(stack: ItemStack, other: ItemStack, slot: Slot,
        action: ClickAction, player: Player, access: SlotAccess
    ): Boolean {
        if (stack.item !== this || other.item !== this || !slot.allowModification(player)) return false
        refreshDefault(stack)
        refreshDefault(other)
        val own = getPunchAttribute(stack) ?: return false
        val incoming = getPunchAttribute(other) ?: return false
        val strength = (own.amount() + incoming.amount()).coerceIn(0.0, MAX_STRENGTH)
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, createAttributes(strength))
        stack.set(TouyComponents.CUSTOM_CROWBAR, true)
        other.shrink(1)
        return true
    }

    override fun useOn(context: UseOnContext): InteractionResult {
        val player = context.player ?: return InteractionResult.PASS
        return use(context.level, player, context.hand)
    }

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        if (level.isClientSide) return InteractionResult.SUCCESS
        val stack = player.getItemInHand(hand)
        refreshDefault(stack)
        // Shift-use changes the push multiplier; combining two crowbars adds their strengths.
        if (player.isShiftKeyDown) {
            val current = getPunchAttribute(stack)?.amount() ?: TouyConfig.crowbarStrength
            val strength = if (current >= MAX_STRENGTH) TouyConfig.crowbarStrength else (current * 2).coerceAtMost(MAX_STRENGTH)
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS, createAttributes(strength))
            stack.set(TouyComponents.CUSTOM_CROWBAR, true)
            player.sendOverlayMessage(Component.translatable("touys.message.crowbar_strength", strength.toInt()))
            return InteractionResult.SUCCESS
        }
        val hit = SubLevelTargeting.hit(level, player) ?: return InteractionResult.PASS
        val target = Sable.HELPER.getContaining(level, hit.blockPos) as? ServerSubLevel ?: return InteractionResult.PASS
        if (!SubLevelTargeting.canUse(level, player, hit.blockPos) || player.cooldowns.isOnCooldown(stack)) {
            return InteractionResult.FAIL
        }
        val handle = RigidBodyHandle.of(target) ?: return InteractionResult.FAIL
        val strength = (getPunchAttribute(stack)?.amount() ?: TouyConfig.crowbarStrength) + 1.0
        val direction = target.logicalPose().transformNormalInverse(
            Vector3d(player.lookAngle.x, player.lookAngle.y, player.lookAngle.z))
        handle.applyImpulseAtPoint(Vector3d(hit.location.x, hit.location.y, hit.location.z), direction.mul(strength))
        player.cooldowns.addCooldown(stack, 5)
        return InteractionResult.SUCCESS
    }

    companion object {
        const val MAX_STRENGTH = 4096.0
        private val ATTRIBUTE_ID = SableTouy.id("crowbar")
        private data class CachedAttributes(val strength: Double, val attributes: ItemAttributeModifiers)
        @Volatile private var cachedDefaults: CachedAttributes? = null

        /** Builds one attribute component per configuration revision, shared by all default stacks. */
        private fun defaultAttributes(): ItemAttributeModifiers {
            val strength = TouyConfig.crowbarStrength.coerceIn(0.0, MAX_STRENGTH)
            val previous = cachedDefaults
            if (previous != null && previous.strength == strength) return previous.attributes
            val attributes = createAttributes(strength)
            cachedDefaults = CachedAttributes(strength, attributes)
            return attributes
        }

        private fun refreshDefault(stack: ItemStack) {
            val custom = stack.get(TouyComponents.CUSTOM_CROWBAR)
            if (custom == true) return
            // Legacy or command-created explicit attributes must not be silently replaced.
            if (custom == null && stack.hasNonDefault(DataComponents.ATTRIBUTE_MODIFIERS)) {
                stack.set(TouyComponents.CUSTOM_CROWBAR, true)
                return
            }
            if (custom == null) stack.set(TouyComponents.CUSTOM_CROWBAR, false)
            val attributes = defaultAttributes()
            if (stack.get(DataComponents.ATTRIBUTE_MODIFIERS) != attributes) {
                stack.set(DataComponents.ATTRIBUTE_MODIFIERS, attributes)
            }
        }

        fun getPunchAttribute(stack: ItemStack): AttributeModifier? {
            val modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS) ?: return null
            for (entry in modifiers.modifiers()) {
                if (entry.matches(SableAttributes.PUNCH_STRENGTH, ATTRIBUTE_ID)) return entry.modifier()
            }
            return null
        }

        fun createAttributes(strength: Double): ItemAttributeModifiers = ItemAttributeModifiers.builder()
            .add(SableAttributes.PUNCH_STRENGTH,
                AttributeModifier(ATTRIBUTE_ID, strength.coerceIn(0.0, MAX_STRENGTH), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                EquipmentSlotGroup.MAINHAND)
            .build()

        fun getColor(stack: ItemStack, index: Int = 1): Int {
            if (index != 1) return -1
            val strength = getPunchAttribute(stack)?.amount() ?: return -1
            val amount = (strength / MAX_STRENGTH).coerceIn(0.0, 1.0)
            val value = amount / (amount + 0.005)
            val red = (255 * (value * 2).coerceAtMost(1.0)).toInt()
            val green = (255 * ((1.0 - value) * 2).coerceAtMost(1.0)).toInt()
            return (0xff shl 24) or (red shl 16) or (green shl 8)
        }
    }
}
