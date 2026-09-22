package com.mobilaunch.weave.web

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

data class Projection(val x: Float, val y: Float, val depth: Float, val ppu: Float)

/**
 * Orbit camera around [center]. Rotation is an unconstrained matrix, so the web can be
 * turned endlessly in any direction – no gimbal stops at the poles.
 */
class Camera {
    var rotation: Mat3 = (Mat3.rotationX(-0.38f) * Mat3.rotationY(0.55f)).orthonormalized()
        private set
    var distance = 8f
        private set
    var center = Vec3.ZERO
        private set

    /** Vertical shift of the look-at point, as a fraction of the view height. */
    var offsetFrac = 0f
        private set

    var viewW = 1f
        private set
    var viewH = 1f
        private set
    var focal = 1000f
        private set

    private var spinX = 0f
    private var spinY = 0f
    private var tween: Tween? = null

    val targetOffsetFrac: Float get() = tween?.toOffset ?: offsetFrac

    fun setViewport(w: Float, h: Float) {
        viewW = w
        viewH = h
        focal = min(w, h) * 0.95f
    }

    fun project(p: Vec3): Projection? {
        val v = rotation.transform(p - center)
        val depth = v.z + distance
        if (depth < NEAR) return null
        val ppu = focal / depth
        return Projection(
            x = viewW / 2f + v.x * ppu,
            y = viewH * (0.5f + offsetFrac) - v.y * ppu,
            depth = depth,
            ppu = ppu,
        )
    }

    /** Converts a finger movement at a given depth into a world-space translation. */
    fun screenDeltaToWorld(dx: Float, dy: Float, depth: Float): Vec3 =
        rotation.transformTransposed(Vec3(dx * depth / focal, -dy * depth / focal, 0f))

    fun rotateScreen(ax: Float, ay: Float) {
        rotation = (Mat3.rotationY(ay) * Mat3.rotationX(ax) * rotation).orthonormalized()
    }

    fun roll(a: Float) {
        rotation = (Mat3.rotationZ(a) * rotation).orthonormalized()
    }

    fun zoomBy(factor: Float) {
        if (factor <= 0f) return
        tween = tween?.let { it.copy(toDistance = (it.toDistance / factor).coerceIn(MIN_DISTANCE, MAX_DISTANCE)) }
        distance = (distance / factor).coerceIn(MIN_DISTANCE, MAX_DISTANCE)
    }

    fun spin(sx: Float, sy: Float) {
        spinX = sx.coerceIn(-MAX_SPIN, MAX_SPIN)
        spinY = sy.coerceIn(-MAX_SPIN, MAX_SPIN)
    }

    fun stopSpin() {
        spinX = 0f
        spinY = 0f
    }

    /** Glides to a new framing. Parameters left null keep heading to whatever is already targeted. */
    fun animateTo(
        center: Vec3? = null,
        distance: Float? = null,
        offsetFrac: Float? = null,
        durationMs: Int = 900,
    ) {
        val current = tween
        tween = Tween(
            fromCenter = this.center,
            toCenter = center ?: current?.toCenter ?: this.center,
            fromDistance = this.distance,
            toDistance = (distance ?: current?.toDistance ?: this.distance).coerceIn(MIN_DISTANCE, MAX_DISTANCE),
            fromOffset = this.offsetFrac,
            toOffset = offsetFrac ?: current?.toOffset ?: this.offsetFrac,
            durationNanos = durationMs * 1_000_000L,
        )
    }

    fun update(now: Long, dt: Float, idle: Boolean) {
        tween?.let { tw ->
            if (tw.start < 0L) tw.start = now
            val t = ((now - tw.start).toFloat() / tw.durationNanos).coerceIn(0f, 1f)
            val e = easeInOutCubic(t)
            center = Vec3.lerp(tw.fromCenter, tw.toCenter, e)
            distance = mix(tw.fromDistance, tw.toDistance, e)
            offsetFrac = mix(tw.fromOffset, tw.toOffset, e)
            if (t >= 1f) tween = null
        }
        if (abs(spinX) > 1e-3f || abs(spinY) > 1e-3f) {
            rotateScreen(spinX * dt, spinY * dt)
            val k = exp(-2.4f * dt)
            spinX *= k
            spinY *= k
        } else if (idle) {
            rotateScreen(0f, IDLE_SPIN * dt)
        }
    }

    private data class Tween(
        val fromCenter: Vec3,
        val toCenter: Vec3,
        val fromDistance: Float,
        val toDistance: Float,
        val fromOffset: Float,
        val toOffset: Float,
        val durationNanos: Long,
        var start: Long = -1L,
    )

    companion object {
        const val MIN_DISTANCE = 2.2f
        const val MAX_DISTANCE = 60f
        private const val NEAR = 0.35f
        private const val MAX_SPIN = 9f
        private const val IDLE_SPIN = 0.07f
    }
}
