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
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.minecraft.world.entity.LivingEntity
import kotlin.random.Random

/**
 * SmartClicking module
 *
 * Drops attacks the server would ignore because the target is still invulnerable,
 * instead of wasting the click on the same hurt cycle.
 */
object ModuleSmartClicking : ClientModule("SmartClicking", ModuleCategories.COMBAT) {

    /**
     * Target hurtTime range in which attacks are dropped.
     */
    private val hurtTime by intRange("HurtTime", 2..10, 0..10)

    /**
     * Chance to drop such an attack, for blending in with a human click rate.
     */
    private val chance by int("Chance", 100, 0..100, "%")

    @Suppress("unused")
    private val attackHandler = handler<AttackEntityEvent> { event ->
        val target = event.entity as? LivingEntity ?: return@handler
        if (target.hurtTime in hurtTime && Random.nextInt(100) < chance) {
            event.cancelEvent()
        }
    }

}
