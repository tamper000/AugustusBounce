package net.ccbluex.liquidbounce.features.module.modules.combat.velocity.mode

import net.ccbluex.liquidbounce.event.events.BlockShapeEvent
import net.ccbluex.liquidbounce.event.handler
import net.minecraft.world.phys.shapes.Shapes

internal object VelocityBoundingBox : VelocityMode("BoundingBox") {

    private val hurtTime by intRange("HurtTime", 0..10, 0..10)
    private val height by float("BoundingBoxHeight", 1f, 0f..1f)

    @Suppress("unused")
    private val shapeHandler = handler<BlockShapeEvent> { event ->
        if (player.hurtTime > hurtTime.first && player.hurtTime <= hurtTime.last &&
            event.state.isAir && event.shape.isEmpty && event.pos.y == player.y.toInt() + 1 && height > 0f
        ) {
            event.shape = Shapes.box(0.0, 0.0, 0.0, 1.0, height.toDouble(), 1.0)
        }
    }
}
