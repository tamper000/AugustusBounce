package net.ccbluex.liquidbounce.features.module.modules.combat.velocity.mode

import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.utils.entity.movementForward
import net.ccbluex.liquidbounce.utils.entity.movementSideways
import net.ccbluex.liquidbounce.utils.entity.onGroundTicks
import net.ccbluex.liquidbounce.utils.item.getEnchantment
import net.ccbluex.liquidbounce.utils.math.getNearestPoint
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3

object VelocityIntaveLegacy : VelocityMode("IntaveLegacy") {

    private val xzOnHit by float("XZOnHit", 0.6f, -1f..1f)
    private val xzOnHitKnockbackItem by float("XZOnHitKnockbackItem", 0.6f, -1f..1f)
    private val xzOnHitSprint by float("XZOnHitSprint", 0.6f, -1f..1f)
    private val xzOnHitAtDoubleHit by float("XZOnHitAtDoubleHit", 1f, -1f..1f)
    private val smart by boolean("IntaveSmart", false)
    private val forceDoubleHit by boolean("ForceDoubleHit", false)
    private var slowdownApplied = false
    private var doubleHitFor: LocalPlayer? = null

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        slowdownApplied = false
        if (player.hurtTime == 0 || doubleHitFor !== player) doubleHitFor = null
    }

    override fun disable() {
        slowdownApplied = false
        doubleHitFor = null
        super.disable()
    }

    fun shouldDoubleHit(): Boolean {
        if (!running || !forceDoubleHit || xzOnHitAtDoubleHit == 1f) return false
        val localPlayer = mc.player ?: return false
        if (localPlayer.hurtTime == 0 || doubleHitFor === localPlayer ||
            (mc.hitResult as? EntityHitResult)?.entity !is LivingEntity
        ) return false

        doubleHitFor = localPlayer
        return true
    }

    fun attackFactor(player: LocalPlayer, target: Entity): Double? {
        if (!running || player.hurtTime == 0) return null

        val hasKnockback = player.mainHandItem.getEnchantment(Enchantments.KNOCKBACK) > 0
        val vanillaSlowdown = hasKnockback || player.isSprinting
        val factor = when {
            slowdownApplied && !vanillaSlowdown && xzOnHitAtDoubleHit != 1f -> xzOnHitAtDoubleHit
            slowdownApplied -> return null
            hasKnockback -> xzOnHitKnockbackItem
            vanillaSlowdown -> xzOnHitSprint
            else -> xzOnHit
        }.toDouble()

        // shortcut: compare linear next-tick positions until the legacy movement prediction is ported.
        if (!vanillaSlowdown && smart && target is LivingEntity) {
            val box = target.boundingBox.move(target.deltaMovement)
            val motion = player.deltaMovement
            val normal = player.eyePosition.add(motion)
            val slowed = player.eyePosition.add(motion.multiply(factor, 1.0, factor))
            if (slowed.distanceToSqr(box.getNearestPoint(slowed)) >= normal.distanceToSqr(box.getNearestPoint(normal))) {
                return null
            }
        }
        return factor
    }

    fun applyAttackSlowdown(player: LocalPlayer, before: Vec3, factor: Double) {
        slowdownApplied = true
        player.setDeltaMovement(before.x * factor, player.deltaMovement.y, before.z * factor)
        player.isSprinting = false
    }

    private object Boost : ToggleableValueGroup(VelocityIntaveLegacy, "IntaveBoost", false) {
        private val delay by int("Delay", 2000, 0..10000, "ms")
        private val groundTicks by int("OnGroundTicks", 2, 0..10, "ticks")
        private val xzVelocity by int("XZVelocity", 100, -300..300, "%")
        private val yVelocity by int("YVelocity", 100, -100..200, "%")
        private val sneakInAir by boolean("SneakInAir", false)
        private var lastBoost = 0L
        private var pendingFor: LocalPlayer? = null
        private var pendingPacket: ClientboundSetEntityMotionPacket? = null

        override fun onEnabled() {
            lastBoost = System.nanoTime()
        }

        override fun onDisabled() = releasePacket()

        private fun releasePacket() {
            val packet = pendingPacket
            val recipient = pendingFor
            pendingPacket = null
            pendingFor = null
            if (packet != null && mc.player === recipient) mc.connection?.let(packet::handle)
        }

        @Suppress("unused")
        private val worldHandler = handler<WorldChangeEvent> {
            pendingPacket = null
            pendingFor = null
            lastBoost = System.nanoTime()
        }

        @Suppress("unused")
        private val packetHandler = handler<PacketEvent> { event ->
            val packet = event.packet
            if (event.isCancelled || pendingFor === player || event.origin != TransferOrigin.INCOMING ||
                packet !is ClientboundSetEntityMotionPacket || packet.id != player.id || !player.onGround() ||
                player.horizontalCollision || player.onGroundTicks <= groundTicks ||
                (player.input.movementForward == 0f && player.input.movementSideways == 0f)
            ) return@handler

            val now = System.nanoTime()
            if (now - lastBoost < delay * 1_000_000L) return@handler
            lastBoost = now
            pendingFor = player
            if (xzVelocity == 0 && yVelocity == 0) {
                event.cancelEvent()
            } else {
                packet.movement.x *= xzVelocity / 100.0
                packet.movement.y *= yVelocity / 100.0
                packet.movement.z *= xzVelocity / 100.0
                if (player.tickCount > 20) {
                    pendingPacket = packet
                    event.cancelEvent()
                }
            }
        }

        @Suppress("unused")
        private val inputHandler = handler<MovementInputEvent> { event ->
            if (pendingFor === player && player.onGround()) {
                event.sneak = true
                event.directionalInput = event.directionalInput.copy(forwards = false, backwards = false)
            }
            releasePacket()
            if (sneakInAir && player.hurtTime > 2) event.sneak = true
        }
    }

    init {
        tree(Boost)
    }
}
