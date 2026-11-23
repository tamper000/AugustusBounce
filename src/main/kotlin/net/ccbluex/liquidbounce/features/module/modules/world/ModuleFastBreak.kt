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
package net.ccbluex.liquidbounce.features.module.modules.world

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.NoneMode
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.utils.item.isMiningTool
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket

/**
 * FastBreak module
 *
 * Allows you to break blocks faster.
 */
object ModuleFastBreak : ClientModule("FastBreak", ModuleCategories.WORLD) {

    private const val VANILLA_DESTROY_DELAY = 5

    private val breakDamage by float("BreakDamage", 0.8f, 0.1f..1f)
    private val onlyTool by boolean("OnlyTool", false)
    private val multiplier by float("Multiplier", 1f, 1f..2f)
    private val customInAirMultiplier by boolean("CustomInAirMultiplier", false)
    private val inAirMultiplier by float("InAirMultiplier", 0.2f, 0f..1f)
    private val artificialMiningSpeedEffect by int("ArtificialMiningSpeedEffect", 0, 0..3)
    private val instant by boolean("Instant", false)

    private val modeChoice = choices("Mode", 0) { arrayOf(NoneMode(it), AbortAnother) }.apply(::tagBy)

    val repeatable = handler<GameTickEvent> {
        if (onlyTool && !player.mainHandItem.isMiningTool) {
            return@handler
        }

        interaction.destroyDelay = 0

        if (interaction.isDestroying && (instant || interaction.destroyProgress > breakDamage)) {
            interaction.destroyProgress = 1f
        }
    }

    /**
     * Haste-like speed boost and in-air multiplier, applied from MixinPlayer#getDestroySpeed.
     */
    @JvmStatic
    fun modifyDestroySpeed(original: Float, hasAirPenalty: Boolean): Float {
        if (!running || onlyTool && !player.mainHandItem.isMiningTool) {
            return original
        }

        var speed = original * (1f + artificialMiningSpeedEffect * 0.2f) * multiplier
        // Vanilla divides by 5 in air; replace that instead of stacking on top of it
        if (customInAirMultiplier && hasAirPenalty) {
            speed *= 5f * inAirMultiplier
        }
        return speed
    }

    override fun onDisabled() {
        interaction.destroyDelay = VANILLA_DESTROY_DELAY
        super.onDisabled()
    }

    /**
     * Bypass Grim 2.3.48 anti-cheat
     * Tested on eu.loyisa.cn
     *
     * https://github.com/GrimAnticheat/Grim/issues/1296
     */
    object AbortAnother : Mode("Grim-2.3.48", aliases = listOf("AbortAnother")) {

        override val parent: ModeValueGroup<Mode>
            get() = modeChoice

        val packetHandler = handler<PacketEvent> {
            if (onlyTool && !player.mainHandItem.isMiningTool) {
                return@handler
            }

            val packet = it.packet

            if (packet is ServerboundPlayerActionPacket &&
                packet.action == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK
            ) {
                val blockPos = packet.pos

                // Abort block break on the block above (which we are not breaking)
                network.send(
                    ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                        blockPos.above(), packet.direction
                    )
                )
            }
        }

    }

}
