/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.events.AttackEntityEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.combat.autoclicker.ModuleAutoClicker
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.KillAuraClicker
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features.KillAuraAutoBlock
import net.ccbluex.liquidbounce.utils.client.isBlocksAttacksExisting
import net.ccbluex.liquidbounce.utils.combat.CombatManager
import net.ccbluex.liquidbounce.utils.entity.useItem
import net.ccbluex.liquidbounce.utils.item.isSword
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemUseAnimation
import net.minecraft.world.phys.EntityHitResult

/**
 * AutoBlock module
 *
 * Blocks with a sword while a target is in reach and drops the block around every click, since a 1.8 client
 * cannot attack while it is blocking. Works on its own as well as with [ModuleAutoClicker] and KillAura.
 *
 * Ported from OpenAugustus' AutoBlock.
 */
object ModuleAutoBlock : ClientModule("AutoBlock", ModuleCategories.COMBAT) {

    private val range by float("Range", 4.5f, 3f..6f, "blocks")

    /**
     * Ticks the block stays down after a hit, so the click that follows is not swallowed by it.
     */
    private val unblockTicks by int("UnblockTicks", 2, 0..5, "ticks")

    /**
     * For 1.9~1.21.4 protocol on a 1.8 server, the server sends a shield to your offhand on using item.
     */
    private val assumeShield by boolean("AssumeShield", false)

    private var target: LivingEntity? = null
    private var blockingHand: InteractionHand? = null
    private var unblockTicksLeft = 0

    /**
     * Whether this module owns the use item action right now, checked by
     * [net.minecraft.client.Minecraft.handleKeybinds] so it does not release the block.
     */
    fun isEnforcingBlocking() = blockingHand != null

    @Suppress("unused")
    private val attackHandler = handler<AttackEntityEvent> { event ->
        val entity = event.entity as? LivingEntity ?: return@handler

        target = entity
        unblockTicksLeft = unblockTicks
        stopBlocking()
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        target = null
        unblockTicksLeft = 0
        stopBlocking()
    }

    /**
     * Runs after the clickers planned their ticks, but before they click, so the click is not eaten while blocking.
     */
    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent>(
        priority = EventPriorityConvention.CRITICAL_MODIFICATION
    ) {
        // KillAura has its own, better integrated AutoBlocking
        if (KillAuraAutoBlock.running || CombatManager.shouldPauseCombat || !hasTarget()) {
            unblockTicksLeft = 0
            stopBlocking()
            return@handler
        }

        val unblocked = unblockTicksLeft > 0
        if (unblocked) {
            unblockTicksLeft--
        }

        if (unblocked || clickIncoming()) {
            stopBlocking()
            return@handler
        }

        startBlocking()
    }

    override fun onDisabled() {
        target = null
        unblockTicksLeft = 0
        // Toggled outside of a world, the block ended with it
        if (inGame) {
            stopBlocking()
        } else {
            blockingHand = null
        }
    }

    private fun hasTarget(): Boolean {
        val attacked = target
        if (attacked != null && attacked.isAlive && player.distanceTo(attacked) <= range) {
            return true
        }

        target = null
        val crosshair = (mc.hitResult as? EntityHitResult)?.entity as? LivingEntity ?: return false
        return crosshair.isAlive && player.distanceTo(crosshair) <= range
    }

    /**
     * Whether a click is going to be made this tick, by the player or by a clicker.
     */
    private fun clickIncoming(): Boolean {
        if (mc.options.keyAttack.isDown) {
            return true
        }

        val autoClicker = ModuleAutoClicker.AttackButton.clicker
        if (autoClicker.running && autoClicker.willClickAt(0)) {
            return true
        }

        return KillAuraClicker.running && KillAuraClicker.willClickAt(0)
    }

    private fun startBlocking() {
        if (blockingHand != null || player.isUsingItem) {
            return
        }

        val hand = findBlockableHand() ?: return
        useItem(hand)
        blockingHand = hand
    }

    private fun stopBlocking() {
        if (blockingHand == null) {
            return
        }

        blockingHand = null
        // Only releases the use action this module started, not food or potions
        interaction.releaseUsingItem(player)
    }

    private fun findBlockableHand(): InteractionHand? {
        return InteractionHand.entries.find { hand ->
            val itemStack = player.getItemInHand(hand)
            // 1.21.4 swords or normal shields
            itemStack.useAnimation == ItemUseAnimation.BLOCK &&
                itemStack.isItemEnabled(world.enabledFeatures()) &&
                !player.cooldowns.isOnCooldown(itemStack)
        } ?: if (assumeShield && !isBlocksAttacksExisting && player.mainHandItem.isSword) {
            InteractionHand.MAIN_HAND
        } else {
            null
        }
    }

}
