package com.mobilaunch.weave.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import com.mobilaunch.weave.data.Mood
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** Every mood grows its own little world. */
enum class Biome(
    val title: String,
    val ocean: Color,
    val land: Color,
    val accent: Color,
    val atmosphere: Color,
    val spin: Float,
) {
    Stardust("A quiet rock, waiting for a feeling", Color(0xFF8E8A9A), Color(0xFF6B6779), Color(0xFFC9C4D6), Color(0xFFB9B4C7), 0.18f),
    Meadow("Sunny meadow world", Color(0xFF4FA8F0), Color(0xFF5CC46A), Color(0xFFFFE082), Color(0xFF9FE3FF), 0.25f),
    Lagoon("Tranquil lagoon", Color(0xFF3CC4C0), Color(0xFFF1D9A6), Color(0xFF2E9E6A), Color(0xFFB6F0EA), 0.12f),
    Forest("Mystery mushroom forest", Color(0xFF2F3E6E), Color(0xFF3F7A5A), Color(0xFFFF6F61), Color(0xFFB39DDB), 0.2f),
    Crystal("Crystal aurora world", Color(0xFF5B4BC4), Color(0xFFB39DFF), Color(0xFF7DF9FF), Color(0xFFE1BEE7), 0.3f),
    Volcano("A world on fire", Color(0xFFB8321A), Color(0xFF2A1512), Color(0xFFFF8A1A), Color(0xFFFF5722), 0.35f),
    Storm("Stormy water world", Color(0xFF1B3B6F), Color(0xFF2C5A8F), Color(0xFFE3F2FD), Color(0xFF5C7FA8), 0.22f),
    ;

    companion object {
        fun of(mood: Mood?): Biome = when (mood) {
            Mood.Joyful -> Meadow
            Mood.Calm -> Lagoon
            Mood.Curious -> Forest
            Mood.Inspired -> Crystal
            Mood.Fired -> Volcano
            Mood.Blue -> Storm
            null -> Stardust
        }
    }
}

private class Spot(val lon: Float, val lat: Float, val size: Float, val seed: Float)

private val spotCache = HashMap<Biome, List<Spot>>()

private fun spotsFor(b: Biome): List<Spot> = spotCache.getOrPut(b) {
    val rnd = Random(b.ordinal * 31 + 7)
    val n = when (b) {
        Biome.Stardust -> 9
        Biome.Storm -> 10
        Biome.Crystal -> 12
        else -> 7
    }
    List(n) {
        Spot(
            lon = rnd.nextFloat() * 2f * PI.toFloat(),
            lat = (rnd.nextFloat() * 2f - 1f) * 1.1f,
            size = 0.18f + rnd.nextFloat() * 0.3f,
            seed = rnd.nextFloat(),
        )
    }
}

/**
 * Draws a little world.
 *
 * @param detail 0..1 – how many creatures, trees and landmarks have appeared (grows as you type).
 * @param opacity overall opacity, used to cross-fade between biomes.
 * @param sky draw the weather and orbiting things around the planet (skipped for tiny planets).
 */
fun DrawScope.drawPlanet(
    c: Offset,
    r: Float,
    biome: Biome,
    t: Float,
    detail: Float = 1f,
    opacity: Float = 1f,
    sky: Boolean = true,
) {
    if (r < 1f || opacity <= 0.01f) return
    fun Color.a(x: Float = 1f) = copy(alpha = (opacity * x * this.alpha).coerceIn(0f, 1f))
    val spin = t * biome.spin

    // Atmosphere.
    val pulse = if (biome == Biome.Volcano) 0.85f + 0.15f * sin(t * 6f) else 1f
    drawCircle(
        brush = Brush.radialGradient(
            0.55f to biome.atmosphere.a(0.35f * pulse),
            0.7f to biome.atmosphere.a(0.18f * pulse),
            1f to Color.Transparent,
            center = c,
            radius = r * 1.6f,
        ),
        radius = r * 1.6f,
        center = c,
    )
    if (sky) skyBehind(c, r, biome, t, opacity)

    // The globe itself, shaded like a sphere.
    drawCircle(
        brush = Brush.radialGradient(
            0f to lerp(biome.ocean, Color.White, 0.25f).a(),
            1f to lerp(biome.ocean, Color.Black, 0.35f).a(),
            center = c + Offset(-0.35f * r, -0.35f * r),
            radius = r * 1.5f,
        ),
        radius = r,
        center = c,
    )
    val globe = Path().apply { addOval(Rect(c, r)) }
    clipPath(globe) {
        surface(c, r, biome, t, spin, opacity)
        // Night side.
        drawCircle(
            brush = Brush.radialGradient(
                0.5f to Color.Transparent,
                1f to Color.Black.a(0.5f),
                center = c + Offset(-0.3f * r, -0.3f * r),
                radius = r * 1.45f,
            ),
            radius = r,
            center = c,
        )
    }
    drawCircle(lerp(biome.atmosphere, Color.White, 0.3f).a(0.35f), radius = r, center = c, style = Stroke(max(0.7f, r * 0.025f)))

    if (detail > 0f) rimLife(c, r, biome, t, detail, opacity)
    if (sky) skyFront(c, r, biome, t, opacity)
}

/** Continents, seas, craters and lava plates sliding by as the globe turns. */
private fun DrawScope.surface(c: Offset, r: Float, b: Biome, t: Float, spin: Float, opacity: Float) {
    fun Color.a(x: Float = 1f) = copy(alpha = (opacity * x * this.alpha).coerceIn(0f, 1f))
    // Storm clouds and ocean swells move in bands.
    if (b == Biome.Storm) {
        for (i in 0 until 5) {
            val y = c.y + r * (-0.8f + i * 0.4f)
            val shift = ((t * 0.25f * (1 + i % 2) + i * 0.37f) % 1f) * 2f * r
            for (k in -1..1) {
                val x = c.x - r + shift + k * 2f * r
                drawArc(b.land.a(0.8f), 200f, 140f, false, topLeft = Offset(x - r * 0.3f, y - r * 0.08f), size = Size(r * 0.6f, r * 0.2f), style = Stroke(r * 0.03f, cap = StrokeCap.Round))
            }
        }
    }
    for (s in spotsFor(b)) {
        val a = s.lon + spin
        val facing = cos(a)
        if (facing < -0.15f) continue
        val x = c.x + r * cos(s.lat * 0.9f) * sin(a)
        val y = c.y - r * sin(s.lat * 0.9f)
        val w = s.size * r * (0.3f + 0.7f * max(0f, facing))
        val h = s.size * r * 0.7f
        val tl = Offset(x - w, y - h)
        val sz = Size(w * 2f, h * 2f)
        when (b) {
            Biome.Stardust -> {
                drawOval(b.land.a(0.9f), tl, sz)
                drawOval(b.accent.a(0.5f), tl, sz, style = Stroke(max(0.6f, r * 0.015f)))
            }
            Biome.Meadow -> {
                drawOval(b.land.a(), tl, sz)
                drawCircle(b.accent.a(0.9f), radius = r * 0.025f, center = Offset(x, y))
                drawCircle(Color(0xFFFF8FB1).a(0.9f), radius = r * 0.02f, center = Offset(x + w * 0.4f, y + h * 0.3f))
            }
            Biome.Lagoon -> {
                drawOval(lerp(b.ocean, Color.White, 0.4f).a(0.7f), tl - Offset(w * 0.3f, h * 0.3f), Size(sz.width * 1.3f, sz.height * 1.3f))
                drawOval(b.land.a(), tl + Offset(w * 0.4f, h * 0.4f), Size(sz.width * 0.6f, sz.height * 0.6f))
            }
            Biome.Forest -> {
                drawOval(b.land.a(), tl, sz)
                val glow = 0.5f + 0.5f * sin(t * 3f + s.seed * 10f)
                drawCircle(Color(0xFFB2FF59).a(0.8f * glow), radius = r * 0.02f, center = Offset(x, y))
            }
            Biome.Crystal -> {
                val d = Path().apply {
                    moveTo(x, y - h)
                    lineTo(x + w * 0.6f, y)
                    lineTo(x, y + h)
                    lineTo(x - w * 0.6f, y)
                    close()
                }
                drawPath(d, b.land.a(0.8f))
                val glint = (sin(t * 4f + s.seed * 20f)).coerceAtLeast(0f)
                drawCircle(Color.White.a(glint), radius = r * 0.02f, center = Offset(x, y - h * 0.4f))
            }
            Biome.Volcano -> {
                drawOval(b.land.a(), tl, sz)
                drawOval(b.accent.a(0.6f + 0.4f * sin(t * 5f + s.seed * 9f)), tl, sz, style = Stroke(max(0.7f, r * 0.02f)))
            }
            Biome.Storm -> drawOval(lerp(b.land, Color.White, 0.15f).a(0.5f), tl, Size(sz.width * 1.4f, sz.height * 0.35f))
        }
    }
}

/** Trees, animals and landmarks standing on the horizon, popping in as [detail] grows. */
private fun DrawScope.rimLife(c: Offset, r: Float, b: Biome, t: Float, detail: Float, opacity: Float) {
    val things = rimPlan(b)
    val k = r / 100f
    val turn = t * 0.12f
    val shown = detail * things.size
    for ((i, thing) in things.withIndex()) {
        val appear = (shown - i).coerceIn(0f, 1f)
        if (appear <= 0f) continue
        val pop = if (appear < 1f) appear * (1f + 0.3f * sin(appear * PI.toFloat())) else 1f
        val ang = thing.angle + turn
        val base = c + Offset(cos(ang), sin(ang)) * (r * 0.97f)
        translate(base.x, base.y) {
            rotate(degrees = ang * 180f / PI.toFloat() + 90f, pivot = Offset.Zero) {
                drawThing(thing.kind, k * pop, t + i * 1.7f, b, opacity)
            }
        }
    }
}

private enum class Kind { Tree, Pine, Sheep, Flower, Palm, Whale, Mushroom, Crystal, Volcano, Flame, Lighthouse, Rock, Bird }

private class Thing(val kind: Kind, val angle: Float)

private val rimCache = HashMap<Biome, List<Thing>>()

private fun rimPlan(b: Biome): List<Thing> = rimCache.getOrPut(b) {
    val kinds = when (b) {
        Biome.Meadow -> listOf(Kind.Tree, Kind.Sheep, Kind.Flower, Kind.Tree, Kind.Sheep, Kind.Flower, Kind.Tree, Kind.Flower, Kind.Sheep)
        Biome.Lagoon -> listOf(Kind.Palm, Kind.Whale, Kind.Palm, Kind.Rock, Kind.Palm)
        Biome.Forest -> listOf(Kind.Mushroom, Kind.Pine, Kind.Mushroom, Kind.Pine, Kind.Pine, Kind.Mushroom, Kind.Pine, Kind.Mushroom)
        Biome.Crystal -> listOf(Kind.Crystal, Kind.Crystal, Kind.Crystal, Kind.Crystal, Kind.Crystal, Kind.Crystal)
        Biome.Volcano -> listOf(Kind.Volcano, Kind.Flame, Kind.Flame, Kind.Volcano, Kind.Flame, Kind.Flame, Kind.Flame, Kind.Volcano, Kind.Flame, Kind.Flame)
        Biome.Storm -> listOf(Kind.Lighthouse, Kind.Rock)
        Biome.Stardust -> listOf(Kind.Rock, Kind.Rock, Kind.Rock)
    }
    val rnd = Random(b.ordinal * 13 + 3)
    kinds.mapIndexed { i, kind ->
        Thing(kind, i * (2f * PI.toFloat() / kinds.size) + rnd.nextFloat() * 0.3f)
    }
}

/** Draws one horizon object in a local frame where -y points away from the planet. */
private fun DrawScope.drawThing(kind: Kind, k: Float, t: Float, b: Biome, opacity: Float) {
    fun Color.a(x: Float = 1f) = copy(alpha = (opacity * x * this.alpha).coerceIn(0f, 1f))
    when (kind) {
        Kind.Tree -> {
            drawRect(Color(0xFF6D4C41).a(), Offset(-1.2f * k, -9f * k), Size(2.4f * k, 9f * k))
            drawCircle(Color(0xFF43A047).a(), radius = 6.5f * k, center = Offset(0f, -13f * k))
            drawCircle(Color(0xFF66BB6A).a(), radius = 4f * k, center = Offset(-2f * k, -15f * k))
        }
        Kind.Pine -> {
            drawRect(Color(0xFF4E342E).a(), Offset(-1f * k, -5f * k), Size(2f * k, 5f * k))
            val p = Path().apply {
                moveTo(0f, -20f * k); lineTo(6f * k, -4f * k); lineTo(-6f * k, -4f * k); close()
            }
            drawPath(p, Color(0xFF1B5E20).a())
        }
        Kind.Sheep -> {
            // Hops happily.
            val hop = -abs(sin(t * 3.5f)) * 3.5f * k
            translate(0f, hop) {
                drawLine(Color(0xFF3E2723).a(), Offset(-3f * k, -3f * k), Offset(-3f * k, 0f), strokeWidth = 1.2f * k)
                drawLine(Color(0xFF3E2723).a(), Offset(3f * k, -3f * k), Offset(3f * k, 0f), strokeWidth = 1.2f * k)
                for (dx in listOf(-3f, 0f, 3f)) drawCircle(Color.White.a(), radius = 3.6f * k, center = Offset(dx * k, -6f * k))
                drawCircle(Color.White.a(), radius = 3.6f * k, center = Offset(0f, -8.5f * k))
                drawCircle(Color(0xFF3E2723).a(), radius = 2.4f * k, center = Offset(6.5f * k, -7.5f * k))
            }
        }
        Kind.Flower -> {
            val sway = sin(t * 2f) * 1.5f * k
            drawLine(Color(0xFF2E7D32).a(), Offset.Zero, Offset(sway, -8f * k), strokeWidth = 1f * k)
            for (i in 0 until 5) {
                val a = i * 2f * PI.toFloat() / 5f
                drawCircle(Color(0xFFFF8FB1).a(), radius = 1.8f * k, center = Offset(sway + cos(a) * 2.2f * k, -8f * k + sin(a) * 2.2f * k))
            }
            drawCircle(b.accent.a(), radius = 1.5f * k, center = Offset(sway, -8f * k))
        }
        Kind.Palm -> {
            rotate(degrees = sin(t * 1.2f) * 6f, pivot = Offset.Zero) {
                val trunk = Path().apply {
                    moveTo(-1f * k, 0f); quadraticBezierTo(2f * k, -8f * k, 1f * k, -17f * k)
                    lineTo(2.5f * k, -17f * k); quadraticBezierTo(3.5f * k, -8f * k, 1.5f * k, 0f); close()
                }
                drawPath(trunk, Color(0xFF8D6E63).a())
                val top = Offset(1.8f * k, -17f * k)
                for (i in 0 until 5) {
                    val a = -PI.toFloat() + i * PI.toFloat() / 4f
                    val end = top + Offset(cos(a) * 8f * k, sin(a) * 4f * k + 3f * k)
                    drawLine(b.accent.a(), top, end, strokeWidth = 2f * k, cap = StrokeCap.Round)
                }
            }
        }
        Kind.Whale -> {
            // Surfaces now and then with a spout.
            val cycle = (t * 0.25f) % 1f
            val up = sin(cycle * PI.toFloat()).coerceAtLeast(0f)
            if (up > 0.02f) {
                val lift = up * 5f * k
                drawOval(Color(0xFF37474F).a(), Offset(-8f * k, -lift - 2f * k), Size(16f * k, 7f * k))
                drawOval(Color(0xFF90A4AE).a(), Offset(-5f * k, -lift + 1f * k), Size(9f * k, 3f * k))
                drawCircle(Color.White.a(), radius = 0.9f * k, center = Offset(4f * k, -lift))
                if (up > 0.6f) {
                    for (dx in listOf(-2f, 0f, 2f)) {
                        drawLine(Color(0xFFB3E5FC).a(up), Offset(0f, -lift - 3f * k), Offset(dx * k, -lift - 9f * k), strokeWidth = 1f * k, cap = StrokeCap.Round)
                    }
                }
            }
        }
        Kind.Mushroom -> {
            drawRect(Color(0xFFF5E6D3).a(), Offset(-1.5f * k, -7f * k), Size(3f * k, 7f * k))
            val glow = 0.6f + 0.4f * sin(t * 2.5f)
            drawCircle(b.accent.a(0.3f * glow), radius = 9f * k, center = Offset(0f, -8f * k))
            drawArc(b.accent.a(), 180f, 180f, true, topLeft = Offset(-6f * k, -13f * k), size = Size(12f * k, 11f * k))
            drawCircle(Color.White.a(), radius = 1f * k, center = Offset(-2.5f * k, -10f * k))
            drawCircle(Color.White.a(), radius = 0.8f * k, center = Offset(2f * k, -11f * k))
        }
        Kind.Crystal -> {
            val h = 16f * k
            val p = Path().apply {
                moveTo(-3f * k, 0f); lineTo(-3.5f * k, -h * 0.65f); lineTo(0f, -h); lineTo(3.5f * k, -h * 0.65f); lineTo(3f * k, 0f); close()
            }
            drawPath(p, Brush.verticalGradient(listOf(Color.White.a(0.9f), b.accent.a(0.9f), b.land.a()), startY = -h, endY = 0f))
            val shine = sin(t * 3f).coerceAtLeast(0f)
            drawLine(Color.White.a(shine), Offset(-1f * k, -h * 0.8f), Offset(-1.8f * k, -h * 0.25f), strokeWidth = 0.8f * k)
        }
        Kind.Volcano -> {
            val p = Path().apply {
                moveTo(-9f * k, 0f); lineTo(-3f * k, -11f * k); lineTo(3f * k, -11f * k); lineTo(9f * k, 0f); close()
            }
            drawPath(p, Color(0xFF3E2723).a())
            drawOval(b.accent.a(0.8f + 0.2f * sin(t * 8f)), Offset(-3f * k, -12.5f * k), Size(6f * k, 3f * k))
            // Lava blobs arcing out.
            for (j in 0 until 3) {
                val ph = ((t * 0.9f + j / 3f) % 1f)
                val x = sin(j * 2.1f + 1f) * ph * 9f * k
                val y = -12f * k - (ph * 18f - ph * ph * 10f) * k
                drawCircle(lerp(Color(0xFFFFEB3B), b.accent, ph).a(1f - ph), radius = 1.6f * k * (1f - ph * 0.5f), center = Offset(x, y))
            }
            // Smoke.
            val s = (t * 0.4f) % 1f
            drawCircle(Color(0xFF757575).a(0.5f * (1f - s)), radius = (2f + s * 6f) * k, center = Offset(s * 4f * k, -14f * k - s * 16f * k))
        }
        Kind.Flame -> {
            val flick = 0.75f + 0.25f * sin(t * 11f) + 0.15f * sin(t * 17f + 1f)
            val h = 12f * k * flick
            val w = 3.5f * k
            val p = Path().apply {
                moveTo(-w, 0f); quadraticBezierTo(-w, -h * 0.55f, sin(t * 7f) * k, -h); quadraticBezierTo(w, -h * 0.55f, w, 0f); close()
            }
            drawPath(p, Brush.verticalGradient(listOf(Color(0xFFFFF59D).a(0.95f), Color(0xFFFF9800).a(0.9f), Color(0xFFD84315).a(0.8f)), startY = 0f, endY = -h))
        }
        Kind.Lighthouse -> {
            drawOval(Color(0xFF455A64).a(), Offset(-7f * k, -2f * k), Size(14f * k, 5f * k))
            val tower = Path().apply {
                moveTo(-3f * k, 0f); lineTo(-2f * k, -16f * k); lineTo(2f * k, -16f * k); lineTo(3f * k, 0f); close()
            }
            drawPath(tower, Color.White.a())
            drawRect(Color(0xFFE53935).a(), Offset(-2.6f * k, -8f * k), Size(5.2f * k, 3f * k))
            drawCircle(Color(0xFFFFF59D).a(), radius = 1.8f * k, center = Offset(0f, -17.5f * k))
            // Sweeping beam.
            val beam = sin(t * 1.4f)
            val tip = Offset(beam * 30f * k, -22f * k)
            val p = Path().apply {
                moveTo(0f, -17.5f * k); lineTo(tip.x - 5f * k, tip.y); lineTo(tip.x + 5f * k, tip.y); close()
            }
            drawPath(p, Color(0xFFFFF59D).a(0.35f))
        }
        Kind.Rock -> {
            val p = Path().apply {
                moveTo(-4f * k, 0f); lineTo(-3f * k, -3.5f * k); lineTo(0f, -5f * k); lineTo(3.5f * k, -2.5f * k); lineTo(4f * k, 0f); close()
            }
            drawPath(p, lerp(b.land, Color.Black, 0.2f).a())
        }
        Kind.Bird -> Unit
    }
}

/** Things behind the globe: the sun, far side of rings, the moon's back half of its orbit. */
private fun DrawScope.skyBehind(c: Offset, r: Float, b: Biome, t: Float, opacity: Float) {
    fun Color.a(x: Float = 1f) = copy(alpha = (opacity * x * this.alpha).coerceIn(0f, 1f))
    when (b) {
        Biome.Meadow -> {
            val sun = c + Offset(-1.25f * r, -1.1f * r)
            rotate(t * 12f, pivot = sun) {
                for (i in 0 until 10) {
                    val a = i * 2f * PI.toFloat() / 10f
                    drawLine(Color(0xFFFFD54F).a(0.7f), sun + Offset(cos(a), sin(a)) * (r * 0.3f), sun + Offset(cos(a), sin(a)) * (r * 0.42f), strokeWidth = r * 0.03f, cap = StrokeCap.Round)
                }
            }
            drawCircle(Color(0xFFFFE082).a(), radius = r * 0.24f, center = sun)
        }
        Biome.Crystal -> ring(c, r, b, opacity, back = true)
        else -> Unit
    }
}

/** Weather and moons in front of the globe. */
private fun DrawScope.skyFront(c: Offset, r: Float, b: Biome, t: Float, opacity: Float) {
    fun Color.a(x: Float = 1f) = copy(alpha = (opacity * x * this.alpha).coerceIn(0f, 1f))
    when (b) {
        Biome.Meadow -> for (i in 0 until 3) {
            // Birds circling.
            val ang = t * 0.5f + i * 2.1f
            val p = c + Offset(cos(ang) * r * 1.4f, sin(ang) * r * 0.5f - r * 0.95f)
            val flap = sin(t * 9f + i) * r * 0.04f
            val s = r * 0.07f
            drawLine(Color(0xFF263238).a(0.8f), p, p + Offset(-s, -flap - s * 0.3f), strokeWidth = r * 0.015f, cap = StrokeCap.Round)
            drawLine(Color(0xFF263238).a(0.8f), p, p + Offset(s, -flap - s * 0.3f), strokeWidth = r * 0.015f, cap = StrokeCap.Round)
        }
        Biome.Lagoon -> {
            for (i in 0 until 3) {
                val ang = t * 0.15f + i * 2.2f
                val p = c + Offset(cos(ang) * r * 1.2f, sin(ang) * r * 0.35f - r * 0.2f)
                if (sin(ang) < -0.2f) continue
                drawOval(Color.White.a(0.75f), p - Offset(r * 0.16f, r * 0.05f), Size(r * 0.32f, r * 0.1f))
                drawOval(Color.White.a(0.75f), p - Offset(r * 0.07f, r * 0.1f), Size(r * 0.16f, r * 0.12f))
            }
            val ma = t * 0.3f
            val moon = c + Offset(cos(ma) * r * 1.5f, sin(ma) * r * 0.45f)
            drawCircle(Color(0xFFFFF8E1).a(), radius = r * 0.1f, center = moon)
            drawCircle(Color(0xFFE0D6B9).a(), radius = r * 0.03f, center = moon + Offset(r * 0.03f, -r * 0.02f))
        }
        Biome.Forest -> for (i in 0 until 7) {
            val ang = t * (0.3f + i * 0.05f) + i * 1.3f
            val p = c + Offset(cos(ang) * r * (1.15f + 0.1f * sin(t + i)), sin(ang * 1.3f) * r * 0.9f)
            val glow = 0.5f + 0.5f * sin(t * 4f + i * 2f)
            drawCircle(Color(0xFFCCFF90).a(0.35f * glow), radius = r * 0.06f, center = p)
            drawCircle(Color(0xFFF4FF81).a(glow), radius = r * 0.022f, center = p)
        }
        Biome.Crystal -> {
            ring(c, r, b, opacity, back = false)
            // Aurora ribbons dancing over the north.
            for (i in 0 until 3) {
                val path = Path()
                val steps = 24
                for (s in 0..steps) {
                    val u = s / steps.toFloat()
                    val a = PI.toFloat() * (1.15f + 0.7f * u)
                    val wave = sin(u * 9f + t * 2f + i) * r * 0.05f
                    val rr = r * (1.08f + i * 0.07f) + wave
                    val p = c + Offset(cos(a) * rr, sin(a) * rr)
                    if (s == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                val col = listOf(Color(0xFF69F0AE), Color(0xFF7DF9FF), Color(0xFFEA80FC))[i]
                drawPath(path, col.a(0.45f + 0.25f * sin(t * 1.5f + i)), style = Stroke(r * 0.05f, cap = StrokeCap.Round))
            }
            // A shooting star now and then.
            val s = (t * 0.35f) % 1f
            if (s < 0.25f) {
                val u = s / 0.25f
                val start = c + Offset(r * 1.6f, -r * 1.4f)
                val head = start + Offset(-u * r * 2f, u * r * 0.9f)
                drawLine(Color.White.a(1f - u), head, head + Offset(r * 0.35f, -r * 0.16f), strokeWidth = r * 0.02f, cap = StrokeCap.Round)
            }
        }
        Biome.Volcano -> for (i in 0 until 10) {
            // Embers rising all around.
            val ph = ((t * 0.5f + i * 0.13f) % 1f)
            val ang = i * 0.63f + sin(t * 0.3f + i) * 0.2f - PI.toFloat() / 2f
            val d = r * (1.05f + ph * 0.7f)
            val p = c + Offset(cos(ang) * d, sin(ang) * d)
            drawCircle(Color(0xFFFFAB40).a(1f - ph), radius = r * 0.018f * (1f - ph * 0.5f), center = p)
        }
        Biome.Storm -> {
            // Dark clouds, rain, and the odd lightning strike.
            val flashCycle = (t * 0.45f) % 1f
            val flash = if (flashCycle < 0.06f) 1f - flashCycle / 0.06f else 0f
            for (i in 0 until 4) {
                val ang = t * 0.2f + i * (PI.toFloat() / 2f)
                val p = c + Offset(cos(ang) * r * 0.9f, sin(ang) * r * 0.25f - r * 0.95f)
                if (sin(ang) < -0.3f) continue
                val cloud = lerp(Color(0xFF37474F), Color.White, flash * 0.6f)
                for (dx in listOf(-0.12f, 0f, 0.12f)) drawCircle(cloud.a(0.9f), radius = r * 0.11f, center = p + Offset(dx * r, if (dx == 0f) -r * 0.05f else 0f))
                for (j in 0 until 4) {
                    val fall = ((t * 1.8f + j * 0.25f + i * 0.1f) % 1f)
                    val start = p + Offset((j - 1.5f) * r * 0.07f, r * 0.08f + fall * r * 0.4f)
                    drawLine(Color(0xFF90CAF9).a(0.7f * (1f - fall)), start, start + Offset(-r * 0.02f, r * 0.08f), strokeWidth = r * 0.012f, cap = StrokeCap.Round)
                }
                if (flash > 0f && i == ((t * 0.45f).toInt() % 4)) {
                    val bolt = Path().apply {
                        moveTo(p.x, p.y + r * 0.08f)
                        lineTo(p.x - r * 0.06f, p.y + r * 0.25f)
                        lineTo(p.x + r * 0.03f, p.y + r * 0.27f)
                        lineTo(p.x - r * 0.05f, p.y + r * 0.48f)
                    }
                    drawPath(bolt, b.accent.a(flash), style = Stroke(r * 0.025f, cap = StrokeCap.Round))
                }
            }
            if (flash > 0f) drawCircle(Color.White.a(0.15f * flash), radius = r * 1.5f, center = c)
        }
        Biome.Stardust -> for (i in 0 until 5) {
            val ang = t * 0.2f + i * 1.25f
            val p = c + Offset(cos(ang) * r * 1.3f, sin(ang) * r * 0.3f)
            drawCircle(b.accent.a(0.6f), radius = r * 0.015f, center = p)
        }
        else -> Unit
    }
}

private fun DrawScope.ring(c: Offset, r: Float, b: Biome, opacity: Float, back: Boolean) {
    val w = r * 1.75f
    val h = r * 0.42f
    val bounds = Rect(c.x - w, c.y - h, c.x + w, c.y + h)
    val clip = if (back) Rect(bounds.left - 2f, bounds.top - 2f, bounds.right + 2f, c.y) else Rect(bounds.left - 2f, c.y, bounds.right + 2f, bounds.bottom + 2f)
    clipRect(clip.left, clip.top, clip.right, clip.bottom) {
        drawOval(b.accent.copy(alpha = 0.55f * opacity), bounds.topLeft, bounds.size, style = Stroke(r * 0.06f))
        drawOval(b.land.copy(alpha = 0.4f * opacity), Offset(bounds.left + r * 0.12f, bounds.top + r * 0.04f), Size(bounds.width - r * 0.24f, bounds.height - r * 0.08f), style = Stroke(r * 0.03f))
    }
}
