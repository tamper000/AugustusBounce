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
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features.KillAuraAutoBlock
import net.ccbluex.liquidbounce.features.module.modules.misc.ModuleTeams
import net.ccbluex.liquidbounce.utils.client.isBlocksAttacksExisting
import net.ccbluex.liquidbounce.utils.combat.CombatManager
import net.ccbluex.liquidbounce.utils.combat.EntityTaggingManager
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.entity.isBlockAction
import net.ccbluex.liquidbounce.utils.entity.useItem
import net.ccbluex.liquidbounce.utils.item.isSword
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.READ_FINAL_STATE
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemUseAnimation
import net.minecraft.world.item.ShieldItem
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
    fun isEnforcingBlocking(): Boolean {
        val hand = blockingHand ?: return false
        val player = mc.player ?: return false
        if (!player.isUsingItem) {
            return false
        }

        return player.usedItemHand == hand && player.isBlockAction ||
            hand == InteractionHand.MAIN_HAND && isAssumedShield() &&
            (player.usedItemHand == hand && player.useItem.isSword || player.useItem.item is ShieldItem)
    }

    @Suppress("unused")
    private val attackHandler = handler<AttackEntityEvent>(READ_FINAL_STATE) { event ->
        val entity = event.entity as? LivingEntity ?: return@handler
        if (event.isCancelled || !isValidTarget(entity)) {
            return@handler
        }

        target = entity
        unblockTicksLeft = unblockTicks
        unblockForAttack()
    }

    @Suppress("unused")
    private val worldChangeHandler = handler<WorldChangeEvent> {
        target = null
        unblockTicksLeft = 0
        blockingHand = null
    }

    /**
     * Runs after the clickers, so queued vanilla clicks can take precedence over blocking.
     */
    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent>(READ_FINAL_STATE) {
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

        if (unblocked || mc.options.keyAttack.clickCount > 0) {
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
        val crosshair = (mc.hitResult as? EntityHitResult)?.entity as? LivingEntity
        if (crosshair != null && isAlly(crosshair)) {
            target = null
            return false
        }

        val attacked = target
        if (attacked != null && isValidTarget(attacked)) {
            return true
        }

        target = null
        return crosshair != null && isValidTarget(crosshair)
    }

    /**
     * Respects the global target config, so teammates (Teams module) and friends are not blocked against.
     */
    private fun isValidTarget(entity: LivingEntity) =
        entity.isAlive && player.distanceTo(entity) <= range && !isAlly(entity) && entity.shouldBeAttacked()

    private fun isAlly(entity: LivingEntity) = EntityTaggingManager.getTag(entity).targetingInfo.isFriend ||
        (ModuleTeams.running && ModuleTeams.isInClientPlayersTeam(entity))

    internal fun unblockForAttack() = stopBlocking()

    private fun startBlocking() {
        if (isEnforcingBlocking() || player.isUsingItem) {
            return
        }

        val hand = findBlockableHand() ?: return
        blockingHand = if (useItem(hand) is InteractionResult.Success) hand else null
    }

    private fun stopBlocking() {
        val wasBlocking = isEnforcingBlocking()
        blockingHand = null
        if (wasBlocking) {
            interaction.releaseUsingItem(player)
        }
    }

    private fun isAssumedShield() = assumeShield && !isBlocksAttacksExisting && player.mainHandItem.isSword

    private fun findBlockableHand(): InteractionHand? {
        return InteractionHand.entries.find { hand ->
            val itemStack = player.getItemInHand(hand)
            // 1.21.4 swords or normal shields
            itemStack.useAnimation == ItemUseAnimation.BLOCK &&
                itemStack.isItemEnabled(world.enabledFeatures()) &&
                !player.cooldowns.isOnCooldown(itemStack)
        } ?: if (isAssumedShield()) {
            InteractionHand.MAIN_HAND
        } else {
            null
        }
    }

}
