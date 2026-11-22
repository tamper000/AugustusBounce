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

import net.ccbluex.fastutil.enumSetOf
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.events.AttackEntityEvent
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.utils.client.player
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity

/**
 * MoveHelper module
 *
 * Helps to break out of enemy combos and keeps AutoClicker/KillAura from clicking while the player is being
 * knocked away from the target.
 */
object ModuleMoveHelper : ClientModule("MoveHelper", ModuleCategories.COMBAT) {

    private enum class Mode(override val tag: String) : Tagged {
        COMBO_BREAKER("ComboBreaker"),
        HIT_SELECTION("HitSelection"),
    }

    private val modes by multiEnumChoice("Modes", enumSetOf(Mode.COMBO_BREAKER, Mode.HIT_SELECTION))
    private val combos by int("Combos", 1, 0..4)
    private val range by float("Range", 3f, 1f..6f, "blocks")

    private var hitsTaken = 0
    private var target: Entity? = null
    private var knockedAway = false

    private val comboBreaker: Boolean
        get() = Mode.COMBO_BREAKER in modes

    private val hitSelection: Boolean
        get() = Mode.HIT_SELECTION in modes

    /**
     * While set, AutoClicker and KillAura must not attack: the player is out of reach and any click would miss.
     */
    val shouldBlockClick: Boolean
        get() = running && hitSelection && knockedAway

    @Suppress("unused")
    private val attackHandler = handler<AttackEntityEvent> { event ->
        if (comboBreaker) {
            hitsTaken = 0
        }

        if (hitSelection && event.entity is LivingEntity) {
            target = event.entity
            knockedAway = false
        }
    }

    /**
     * Jumps after [combos] hits received in a row, which cuts the knockback taken on old-style servers.
     */
    @Suppress("unused")
    private val movementInputHandler = handler<MovementInputEvent> { event ->
        if (!comboBreaker || player.hurtTime != 9) {
            return@handler
        }

        if (++hitsTaken > combos && player.onGround()) {
            event.jump = true
            hitsTaken = 0
        }
    }

    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent> {
        val enemy = target
        if (!hitSelection || enemy == null || !enemy.isAlive) {
            target = null
            knockedAway = false
            return@handler
        }

        val delta = player.deltaMovement
        val offset = enemy.position().subtract(player.position())
        val distance = offset.horizontalDistance()

        // Pushed away from the target and already out of reach -> clicking would miss.
        if (offset.x * delta.x + offset.z * delta.z < 0.0 && distance > range) {
            knockedAway = true
        } else if (distance <= range) {
            knockedAway = false
        }
    }

    override fun onDisabled() {
        hitsTaken = 0
        target = null
        knockedAway = false
    }

}
