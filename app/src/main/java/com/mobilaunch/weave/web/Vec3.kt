package com.mobilaunch.weave.web

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    operator fun div(s: Float) = Vec3(x / s, y / s, z / s)

    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(dot(this))
    fun distanceTo(o: Vec3) = (this - o).length()

    fun normalized(fallback: Vec3 = UP): Vec3 {
        val l = length()
        return if (l < 1e-5f) fallback else this / l
    }

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        val UP = Vec3(0f, 1f, 0f)

        fun lerp(a: Vec3, b: Vec3, t: Float) = a + (b - a) * t

        /** Uniformly distributed point on the unit sphere. */
        fun randomUnit(rnd: Random = Random.Default): Vec3 {
            val z = rnd.nextFloat() * 2f - 1f
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            val r = sqrt(1f - z * z)
            return Vec3(r * cos(a), r * sin(a), z)
        }
    }
}

/** Row-major 3x3 rotation matrix. Rows are the camera's right, up and forward axes in world space. */
class Mat3(val m: FloatArray = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)) {

    fun transform(v: Vec3) = Vec3(
        m[0] * v.x + m[1] * v.y + m[2] * v.z,
        m[3] * v.x + m[4] * v.y + m[5] * v.z,
        m[6] * v.x + m[7] * v.y + m[8] * v.z,
    )

    /** Applies the inverse rotation (the transpose, since the matrix is orthonormal). */
    fun transformTransposed(v: Vec3) = Vec3(
        m[0] * v.x + m[3] * v.y + m[6] * v.z,
        m[1] * v.x + m[4] * v.y + m[7] * v.z,
        m[2] * v.x + m[5] * v.y + m[8] * v.z,
    )

    operator fun times(o: Mat3): Mat3 {
        val r = FloatArray(9)
        for (i in 0..2) for (j in 0..2) {
            var s = 0f
            for (k in 0..2) s += m[i * 3 + k] * o.m[k * 3 + j]
            r[i * 3 + j] = s
        }
        return Mat3(r)
    }

    /** Gram-Schmidt on the rows so accumulated float error never skews the web. */
    fun orthonormalized(): Mat3 {
        val r0 = Vec3(m[0], m[1], m[2]).normalized(Vec3(1f, 0f, 0f))
        val raw1 = Vec3(m[3], m[4], m[5])
        val r1 = (raw1 - r0 * r0.dot(raw1)).normalized(Vec3(0f, 1f, 0f))
        val r2 = r0.cross(r1)
        return Mat3(floatArrayOf(r0.x, r0.y, r0.z, r1.x, r1.y, r1.z, r2.x, r2.y, r2.z))
    }

    companion object {
        fun rotationX(a: Float): Mat3 {
            val c = cos(a); val s = sin(a)
            return Mat3(floatArrayOf(1f, 0f, 0f, 0f, c, -s, 0f, s, c))
        }

        fun rotationY(a: Float): Mat3 {
            val c = cos(a); val s = sin(a)
            return Mat3(floatArrayOf(c, 0f, s, 0f, 1f, 0f, -s, 0f, c))
        }

        fun rotationZ(a: Float): Mat3 {
            val c = cos(a); val s = sin(a)
            return Mat3(floatArrayOf(c, -s, 0f, s, c, 0f, 0f, 0f, 1f))
        }
    }
}
