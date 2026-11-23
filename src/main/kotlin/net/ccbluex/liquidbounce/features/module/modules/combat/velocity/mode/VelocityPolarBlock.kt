package net.ccbluex.liquidbounce.features.module.modules.combat.velocity.mode

import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.utils.math.intersects
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket

internal object VelocityPolarBlock : VelocityMode("PolarBlock") {

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        val packet = event.packet
        if (event.isCancelled || event.origin != TransferOrigin.INCOMING ||
            packet !is ClientboundSetEntityMotionPacket || packet.id != player.id || !player.onGround()
        ) return@handler

        val pos = BlockPos.containing(player.x, player.y + 2.0, player.z)
        val shape = world.getBlockState(pos).getCollisionShape(world, pos).move(pos)
        if (shape intersects player.boundingBox.move(0.0, 2.05 - player.bbHeight, 0.0)) {
            packet.movement.x = player.deltaMovement.x
            packet.movement.z = player.deltaMovement.z
        }
    }
}
