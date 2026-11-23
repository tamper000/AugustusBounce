package net.ccbluex.liquidbounce.features.module.modules.combat.velocity.mode

import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket

internal object VelocityLadder : VelocityMode("Ladder") {

    private val xzOnly by boolean("LadderXZOnly", true)

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        val packet = event.packet
        if (!event.isCancelled && event.origin == TransferOrigin.INCOMING &&
            packet is ClientboundSetEntityMotionPacket && packet.id == player.id && player.onClimbable()
        ) {
            packet.movement.x = 0.0
            packet.movement.z = 0.0
            if (!xzOnly) packet.movement.y = 0.0
        }
    }
}
