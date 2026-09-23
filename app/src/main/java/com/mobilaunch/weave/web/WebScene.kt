package com.mobilaunch.weave.web

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.rotate
import com.mobilaunch.weave.data.Category
import com.mobilaunch.weave.data.Mood
import com.mobilaunch.weave.data.Note
import com.mobilaunch.weave.world.Biome
import com.mobilaunch.weave.world.drawPlanet
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

@Immutable
data class WebStyle(
    val background: Color,
    val glow: Color,
    val dust: Color,
    val palette: List<Color>,
    val focus: Color,
    val label: Color,
    val labelBackground: Color,
    val labelStyle: TextStyle,
)

/** A finished drag: [id] and its branches moved by [delta] and now hang from [newParentId]. */
data class MoveResult(val id: String, val newParentId: String, val delta: Vec3, val subtree: Set<String>)

/**
 * Everything the 3D web needs between frames: a snapshot of the notes, the camera, and the
 * state of every running animation. Drawing reads [frameNanos], so the canvas redraws each tick.
 */
@Stable
class WebScene {
    val camera = Camera()

    var frameNanos by mutableLongStateOf(0L)
        private set
    var focusedId by mutableStateOf<String?>(null)
    var isDragging by mutableStateOf(false)
        private set

    /** Only thoughts in this category stay lit; the rest fade back. */
    var filter by mutableStateOf<Category?>(null)
        private set

    /** Fires when a dragged thought starts pointing at a different snap target. */
    var onCandidateChange: (() -> Unit)? = null

    // Snapshot of the web.
    private var synced: List<Note>? = null
    private var notes: List<Note> = emptyList()
    private val base = HashMap<String, Vec3>()
    private val parentOf = HashMap<String, String?>()
    private val generation = HashMap<String, Int>()
    private val snippets = HashMap<String, String>()
    private val phases = HashMap<String, Float>()
    private val moods = HashMap<String, Mood>()
    private val categories = HashMap<String, Category>()
    private val dim = HashMap<String, Float>()
    private var clusters: Map<Category?, List<String>> = emptyMap()
    private var silk: List<Pair<String, String>> = emptyList()
    private var centroid = Vec3.ZERO
    private var radius = 1f

    // Running effects.
    private val spawns = HashMap<String, Long>()
    private var snap: SnapFx? = null
    private val ripples = ArrayList<Ripple>()
    private val pops = HashMap<String, Long>()
    private var drag: DragState? = null

    // Clock.
    private var epoch = 0L
    private var lastTick = 0L
    private var lastInteraction = 0L
    private var touching = false

    // Per-frame caches, reused for hit testing.
    private val projected = HashMap<String, Projection>()

    private val dust: List<Dust> = Random(7).let { rnd ->
        List(DUST_COUNT) {
            Dust(
                pos = Vec3.randomUnit(rnd) * (3f + rnd.nextFloat() * 13f),
                size = 0.4f + rnd.nextFloat() * 1.3f,
                twinkle = rnd.nextFloat() * 6.28f,
            )
        }
    }

    // ---------------------------------------------------------------- model

    fun sync(next: List<Note>) {
        if (next === synced) return
        synced = next
        notes = next
        val ids = next.mapTo(HashSet()) { it.id }
        base.clear()
        parentOf.clear()
        snippets.clear()
        moods.clear()
        categories.clear()
        for (n in next) {
            base[n.id] = n.pos
            parentOf[n.id] = n.parentId?.takeIf { it in ids }
            snippets[n.id] = n.mood?.let { "${it.emoji}  ${n.snippet}" } ?: n.snippet
            n.mood?.let { moods[n.id] = it }
            n.category?.let { categories[n.id] = it }
            phases.getOrPut(n.id) { (n.id.hashCode() and 0xffff) / 65535f * 6.283f }
        }
        generation.clear()
        generation.putAll(WebLayout.generations(parentOf))
        silk = WebLayout.silkLinks(base, parentOf)
        clusters = next.groupBy({ it.category }, { it.id }).filterValues { it.size >= 2 }
        centroid = WebLayout.centroid(base.values)
        radius = base.values.maxOfOrNull { it.distanceTo(centroid) } ?: 0f
        dim.keys.retainAll(ids)
        if (focusedId != null && focusedId !in ids) focusedId = null
    }

    /** A thought's orb colour: its category's, or a theme colour by how deep in its branch it sits. */
    fun colorFor(id: String, style: WebStyle): Color =
        categories[id]?.let { Color(it.argb) } ?: style.palette[(generation[id] ?: 0) % style.palette.size]

    private fun dimOf(id: String) = dim[id] ?: 0f

    private fun dimTarget(id: String): Float {
        val f = filter ?: return 0f
        return if (categories[id] == f) 0f else 1f
    }

    /** Lights up one category (or all, with null) and glides over to it. */
    fun applyFilter(category: Category?) {
        filter = category
        focusedId = null
        val points = if (category == null) base.values.toList() else base.filterKeys { categories[it] == category }.values.toList()
        if (points.isEmpty()) return
        val c = WebLayout.centroid(points)
        camera.animateTo(center = c, distance = fitDistance(c, points), offsetFrac = 0f, durationMs = 1000)
    }

    /** A quick squash-and-stretch on a tapped orb. */
    fun pop(id: String) {
        pops[id] = frameNanos
    }

    fun screenPos(id: String): Offset? = projected[id]?.let { Offset(it.x, it.y) }

    fun viewCenter(): Offset = Offset(camera.viewW / 2f, camera.viewH / 2f)

    // ---------------------------------------------------------------- camera helpers

    fun focusOn(id: String, editing: Boolean) {
        val p = base[id] ?: return
        focusedId = id
        camera.animateTo(
            center = p,
            distance = camera.distance.coerceIn(3.6f, 6.5f),
            offsetFrac = if (editing) EDIT_OFFSET else 0f,
            durationMs = 750,
        )
    }

    fun releaseEditingView() {
        if (camera.targetOffsetFrac != 0f) camera.animateTo(offsetFrac = 0f, durationMs = 600)
    }

    fun clearFocus() {
        focusedId = null
    }

    fun recenter() {
        if (filter != null) {
            applyFilter(filter)
            return
        }
        focusedId = null
        camera.animateTo(center = centroid, distance = fitDistance(centroid, base.values), offsetFrac = 0f, durationMs = 1100)
    }

    /** New thought saved: pull back to show the web, then let the thought weave itself in. */
    fun onNoteCreated(note: Note, delayMs: Long) {
        spawns[note.id] = frameNanos + delayMs * 1_000_000L
        focusedId = note.id
        val points = base.values + note.pos
        camera.animateTo(
            center = note.pos,
            // Pull back far enough that the web reads as clustered solar systems.
            distance = max(fitDistance(note.pos, points) * 1.3f, camera.distance + 3f),
            offsetFrac = 0f,
            durationMs = 2100,
        )
    }

    private fun fitDistance(around: Vec3, points: Collection<Vec3>): Float {
        val r = points.maxOfOrNull { it.distanceTo(around) } ?: 0f
        return (r * 2.4f + 4.5f).coerceIn(5f, 40f)
    }

    // ---------------------------------------------------------------- touch

    fun touchStart() {
        touching = true
        camera.stopSpin()
        lastInteraction = frameNanos
    }

    fun touchEnd() {
        touching = false
        lastInteraction = frameNanos
    }

    fun rotateByDrag(dx: Float, dy: Float) {
        camera.rotateScreen(-dy * ROT_PER_PX, -dx * ROT_PER_PX)
    }

    fun fling(vx: Float, vy: Float) {
        camera.spin(-vy * ROT_PER_PX, -vx * ROT_PER_PX)
    }

    fun hitTest(pos: Offset): String? {
        var best: String? = null
        var bestD = Float.MAX_VALUE
        for ((id, p) in projected) {
            if (dimOf(id) > 0.5f) continue
            val r = NODE_R * p.ppu
            val d = sqrt((p.x - pos.x) * (p.x - pos.x) + (p.y - pos.y) * (p.y - pos.y))
            if (d <= max(r * 1.9f, MIN_HIT_PX) && d < bestD) {
                bestD = d
                best = id
            }
        }
        return best
    }

    fun beginDrag(id: String, at: Offset): Boolean {
        val p = projected[id] ?: return false
        drag = DragState(id, WebLayout.subtree(id, parentOf), at, p.depth)
        isDragging = true
        focusedId = id
        snap = null
        return true
    }

    fun dragTo(at: Offset) {
        val d = drag ?: return
        d.offset = camera.screenDeltaToWorld(at.x - d.startScreen.x, at.y - d.startScreen.y, d.depth)
        val start = base[d.id] ?: return
        val previous = d.candidate
        d.candidate = camera.project(start + d.offset)?.let { nearestOnScreen(it, d.subtree) }
        if (d.candidate != null && d.candidate != previous) onCandidateChange?.invoke()
    }

    /** Drops the dragged thought onto the nearest strand, snapping it into place. */
    fun endDrag(): MoveResult? {
        val d = drag ?: return null
        drag = null
        isDragging = false
        lastInteraction = frameNanos
        val start = base[d.id] ?: return null
        val dropPos = start + d.offset
        val targetId = camera.project(dropPos)?.let { nearestOnScreen(it, d.subtree) }
        if (targetId == null) {
            // Nothing else to hold on to – spring back home.
            snap = SnapFx(d.id, null, null, d.offset, d.subtree, frameNanos)
            return null
        }
        val target = base.getValue(targetId)
        val others = base.filterKeys { it !in d.subtree }.values.toList()
        val newPos = WebLayout.placeChild(target, others, preferred = dropPos - target)
        val delta = newPos - start
        val oldParent = parentOf[d.id]
        for (id in d.subtree) base[id]?.let { base[id] = it + delta }
        parentOf[d.id] = targetId
        snap = SnapFx(
            nodeId = d.id,
            newParentId = targetId,
            broken = if (oldParent != null && oldParent != targetId) BrokenStrand(oldParent, dropPos) else null,
            dropOffset = dropPos - newPos,
            subtree = d.subtree,
            start = frameNanos,
        )
        ripples += Ripple(targetId, frameNanos)
        ripples += Ripple(targetId, frameNanos + 140_000_000L)
        return MoveResult(d.id, targetId, delta, d.subtree)
    }

    private fun nearestOnScreen(p: Projection, exclude: Set<String>): String? {
        var best: String? = null
        var bestD = Float.MAX_VALUE
        for ((id, q) in projected) {
            if (id in exclude || dimOf(id) > 0.5f) continue
            val d = (q.x - p.x) * (q.x - p.x) + (q.y - p.y) * (q.y - p.y)
            if (d < bestD) {
                bestD = d
                best = id
            }
        }
        return best
    }

    // ---------------------------------------------------------------- clock

    fun tick(now: Long) {
        if (epoch == 0L) {
            epoch = now
            lastInteraction = now
        }
        val dt = if (lastTick == 0L) 0f else ((now - lastTick) / 1e9f).coerceIn(0f, 0.05f)
        lastTick = now
        val idle = !touching && drag == null && snap == null && now - lastInteraction > IDLE_AFTER
        camera.update(now, dt, idle)

        snap?.let { if (now - it.start > SNAP_LIFE) snap = null }
        spawns.entries.removeAll { now - it.value > SPAWN_LIFE }
        ripples.removeAll { now - it.start > RIPPLE_LIFE }
        pops.entries.removeAll { now - it.value > 1_000_000_000L }

        // Ease each thought toward lit or dimmed as the category filter changes.
        val k = 1f - exp(-7f * dt)
        for (id in base.keys) {
            val target = dimTarget(id)
            val cur = dim[id] ?: target
            dim[id] = cur + (target - cur) * k
        }
        frameNanos = now
    }

    // ---------------------------------------------------------------- animation

    private fun displayPos(id: String, now: Long, t: Float): Vec3? {
        var p = base[id] ?: return null
        spawns[id]?.let { start ->
            val age = (now - start) / 1e9f
            if (age < 0f) return null
            val from = parentOf[id]?.let { base[it] } ?: p
            p = Vec3.lerp(from, p, easeOutBack(age / SPAWN_TIME))
        }
        drag?.let { if (id in it.subtree) p += it.offset }
        snap?.let { s ->
            val age = (now - s.start) / 1e9f
            if (id in s.subtree) {
                // Damped spring from where the finger let go into the snapped position.
                p += s.dropOffset * (exp(-7f * age) * cos(19f * age))
            } else if (s.newParentId != null) {
                // A shock wave rippling out through the rest of the web.
                val anchor = base[s.newParentId] ?: p
                val away = p - anchor
                val d = away.length()
                val amp = 0.16f * exp(-3.4f * age) * sin(20f * age - d * 3f) / (1f + d * 0.6f)
                p += away.normalized() * amp
            }
        }
        val ph = phases[id] ?: 0f
        p += Vec3(sin(t * 0.7f + ph), cos(t * 0.55f + ph * 1.3f), sin(t * 0.45f + ph * 0.7f)) * BREATHE
        return p
    }

    private fun spawnAge(id: String, now: Long): Float? = spawns[id]?.let { (now - it) / 1e9f }

    // ---------------------------------------------------------------- drawing

    fun draw(scope: DrawScope, style: WebStyle, measure: (String, String) -> LabelLayout) = with(scope) {
        val now = frameNanos
        val t = if (epoch == 0L) 0f else (now - epoch) / 1e9f
        camera.setViewport(size.width, size.height)
        val minDim = min(size.width, size.height)

        // Backdrop: a soft nebula behind the web.
        drawRect(style.background)
        val glowCenter = Offset(size.width / 2f, size.height * (0.45f + camera.offsetFrac))
        drawCircle(
            brush = Brush.radialGradient(listOf(style.glow, Color.Transparent), center = glowCenter, radius = minDim * 0.9f),
            radius = minDim * 0.9f,
            center = glowCenter,
        )

        // Floating dust gives depth cues as the web turns.
        for (d in dust) {
            val p = camera.project(d.pos + centroid) ?: continue
            val a = (0.10f + 0.12f * sin(t * 1.3f + d.twinkle)) * (6f / p.depth).coerceIn(0.2f, 1f)
            drawCircle(style.dust.copy(alpha = a.coerceIn(0f, 1f)), radius = max(0.6f, d.size * p.ppu * 0.012f), center = Offset(p.x, p.y))
        }

        projected.clear()
        for (n in notes) {
            val p = displayPos(n.id, now, t) ?: continue
            camera.project(p)?.let { projected[n.id] = it }
        }
        if (projected.isEmpty()) return@with

        val near = camera.distance - radius - 1f
        val far = camera.distance + radius + 1f
        fun fade(depth: Float) = 1f - 0.72f * ((depth - near) / (far - near)).coerceIn(0f, 1f)
        fun colorOf(id: String) = colorFor(id, style)
        fun vis(id: String) = 1f - 0.85f * dimOf(id)

        // Each category is a little solar system: a sun at its heart and faint orbits through its thoughts.
        for ((category, members) in clusters) {
            drawSolarSystem(category, members, style, t) { fade(it) }
        }

        // Silk cross-strands.
        for ((a, b) in silk) {
            val pa = projected[a] ?: continue
            val pb = projected[b] ?: continue
            val ageA = spawnAge(a, now)
            val ageB = spawnAge(b, now)
            // Strands reach from the established thought toward a newly woven one.
            val (from, to, age) = when {
                ageB != null -> Triple(pa, pb, ageB)
                ageA != null -> Triple(pb, pa, ageA)
                else -> Triple(pa, pb, null)
            }
            val progress = age?.let { easeOutCubic((it - 0.35f) / 0.8f) } ?: 1f
            if (progress <= 0f) continue
            val f = min(fade(pa.depth), fade(pb.depth)) * min(vis(a), vis(b))
            val start = Offset(from.x, from.y)
            val end = lerp(start, Offset(to.x, to.y), progress)
            val glowBoost = if (age != null) (1f - smoothstep(0.8f, 2f, age)) * 0.5f else 0f
            drawLine(
                colorOf(a).copy(alpha = (0.17f + glowBoost) * f),
                start, end,
                strokeWidth = max(0.8f, 0.01f * min(pa.ppu, pb.ppu) * (1f + glowBoost * 2f)),
                cap = StrokeCap.Round,
            )
        }

        // Main strands, parent → child.
        val s = snap
        for (n in notes) {
            val pid = parentOf[n.id] ?: continue
            val a = projected[pid] ?: continue
            val b = projected[n.id] ?: continue
            val f = min(fade(a.depth), fade(b.depth)) * min(vis(pid), vis(n.id))
            val width = max(1f, 0.02f * sqrt(a.ppu * b.ppu))
            var bend = 0f
            var boost = 0f
            if (s != null && s.nodeId == n.id && s.newParentId == pid) {
                // The freshly snapped strand twangs like a plucked thread.
                val age = (now - s.start) / 1e9f
                bend = 34f * exp(-5f * age) * sin(42f * age) * (minDim / 1000f)
                boost = exp(-3f * age)
            }
            strand(Offset(a.x, a.y), Offset(b.x, b.y), colorOf(n.id).copy(alpha = ((0.5f + 0.5f * boost) * f).coerceIn(0f, 1f)), width * (1f + boost), bend)
        }

        // Snapped-off strand recoiling away.
        if (s != null) s.broken?.let { br -> drawBrokenStrand(br, s, now, style) }

        // Drag preview: a dashed line to where the thought will snap.
        drag?.let { d ->
            val from = projected[d.id]
            val to = d.candidate?.let { projected[it] }
            if (from != null && to != null) {
                val pulse = 0.5f + 0.5f * sin(t * 8f)
                drawLine(
                    style.focus.copy(alpha = 0.75f),
                    Offset(from.x, from.y), Offset(to.x, to.y),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f), phase = -t * 60f),
                )
                drawCircle(
                    style.focus.copy(alpha = 0.35f + 0.4f * pulse),
                    radius = NODE_R * to.ppu * (2.1f + 0.4f * pulse),
                    center = Offset(to.x, to.y),
                    style = Stroke(2.dp.toPx()),
                )
            }
        }

        // Thoughts, far to near.
        val order = projected.entries.sortedByDescending { it.value.depth }
        val focused = focusedId
        for ((id, p) in order) {
            val grow = spawnAge(id, now)?.let { easeOutBack(it / SPAWN_TIME) } ?: 1f
            val popScale = pops[id]?.let { 1f + 0.45f * exp(-7f * ((now - it) / 1e9f)) * sin(20f * ((now - it) / 1e9f)) } ?: 1f
            val d = dimOf(id)
            var r = NODE_R * p.ppu * grow * popScale * (1f - 0.4f * d)
            if (id == focused) r *= 1.2f
            if (drag?.id == id) r *= 1.35f
            if (drag?.candidate == id) r *= 1.15f + 0.08f * sin(t * 10f)
            drawOrb(
                center = Offset(p.x, p.y),
                r = r,
                color = colorOf(id),
                mood = moods[id],
                t = t,
                ph = phases[id] ?: 0f,
                f = fade(p.depth) * (1f - 0.85f * d),
                focused = id == focused,
                style = style,
            )
        }

        // A little web spun around each newly woven thought.
        for ((id, start) in spawns) {
            val p = projected[id] ?: continue
            val age = (now - start) / 1e9f
            if (age < 0f) continue
            weaveBurst(Offset(p.x, p.y), NODE_R * p.ppu * 6f, age, colorOf(id))
        }

        // Shock rings where a thought snapped on.
        for (rp in ripples) {
            val p = projected[rp.anchorId] ?: continue
            val age = (now - rp.start) / 1e9f
            if (age < 0f) continue
            val k = age / (RIPPLE_LIFE / 1e9f)
            drawCircle(
                style.focus.copy(alpha = (0.8f * (1f - k)).coerceIn(0f, 1f)),
                radius = NODE_R * p.ppu * (1.5f + 7f * easeOutCubic(k)),
                center = Offset(p.x, p.y),
                style = Stroke((3.dp.toPx() * (1f - k)).coerceAtLeast(0.5f)),
            )
        }

        // Labels for the nearest, readable thoughts.
        val readable = order.asReversed().asSequence()
            .filter { NODE_R * it.value.ppu > 6f && dimOf(it.key) < 0.5f }
            .take(MAX_LABELS)
            .toList()
        for ((id, p) in readable) {
            val text = snippets[id] ?: continue
            val layout = measure(id, text)
            val f = fade(p.depth) * vis(id) * (spawnAge(id, now)?.let { smoothstep(0.4f, 1f, it) } ?: 1f)
            if (f <= 0.05f) continue
            val ls = (p.ppu / 170f).coerceIn(0.7f, 1.25f) * if (id == focused) 1.12f else 1f
            val r = NODE_R * p.ppu
            val w = layout.result.size.width * ls
            val h = layout.result.size.height * ls
            val topLeft = Offset(p.x + r * 1.7f, p.y - h / 2f)
            if (topLeft.x > size.width || topLeft.y > size.height || topLeft.x + w < 0f || topLeft.y + h < 0f) continue
            val padX = 10f * ls
            val padY = 5f * ls
            val category = categories[id]
            val dotR = 4f * ls
            val lead = if (category != null) dotR * 2f + 6f * ls else 0f
            drawRoundRect(
                style.labelBackground.copy(alpha = style.labelBackground.alpha * f),
                topLeft = topLeft - Offset(padX + lead, padY),
                size = Size(w + padX * 2 + lead, h + padY * 2),
                cornerRadius = CornerRadius((h + padY * 2) / 2f),
            )
            if (category != null) {
                drawCircle(Color(category.argb).copy(alpha = f), radius = dotR, center = Offset(topLeft.x - lead + dotR, topLeft.y + h / 2f))
            }
            scale(ls, pivot = topLeft) {
                drawText(layout.result, color = style.label, topLeft = topLeft, alpha = f)
            }
        }
    }

    private fun DrawScope.drawSolarSystem(
        category: Category?,
        members: List<String>,
        style: WebStyle,
        t: Float,
        fade: (Float) -> Float,
    ) {
        val points = members.mapNotNull { base[it] }
        if (points.size < 2) return
        val sunPos = WebLayout.centroid(points)
        val sp = camera.project(sunPos) ?: return
        val lit = if (filter == null || filter == category) 1f else 0.12f
        val f = fade(sp.depth) * lit
        if (f <= 0.02f) return
        val col = category?.let { Color(it.argb) } ?: style.focus
        val seed = category?.ordinal?.toFloat() ?: 9f

        for (id in members.take(MAX_ORBITS)) {
            val mp = base[id] ?: continue
            val d = mp - sunPos
            val rad = d.length()
            if (rad < 0.2f) continue
            val u = d / rad
            var n = u.cross(Vec3.UP)
            if (n.length() < 0.2f) n = u.cross(Vec3(1f, 0f, 0f))
            val w = n.normalized().cross(u).normalized()
            val path = Path()
            var started = false
            for (i in 0..ORBIT_STEPS) {
                val a = i / ORBIT_STEPS.toFloat() * 2f * PI.toFloat()
                val q = camera.project(sunPos + (u * cos(a) + w * sin(a)) * rad)
                if (q == null) {
                    started = false
                    continue
                }
                if (!started) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y)
                started = true
            }
            drawPath(
                path,
                col.copy(alpha = 0.16f * f),
                style = Stroke(max(0.8f, 0.006f * sp.ppu), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 9f))),
            )
        }

        val c = Offset(sp.x, sp.y)
        val sr = max(4f, 0.2f * sp.ppu) * (1f + 0.06f * sin(t * 2f + seed))
        drawCircle(
            brush = Brush.radialGradient(
                0f to col.copy(alpha = 0.5f * f),
                0.35f to col.copy(alpha = 0.18f * f),
                1f to Color.Transparent,
                center = c,
                radius = sr * 4.5f,
            ),
            radius = sr * 4.5f,
            center = c,
        )
        rotate(degrees = t * 10f + seed * 20f, pivot = c) {
            for (i in 0 until 12) {
                val a = i * (2f * PI.toFloat() / 12f)
                val len = 1.9f + 0.35f * sin(t * 3f + i)
                drawLine(
                    col.copy(alpha = 0.45f * f),
                    c + Offset(cos(a), sin(a)) * (sr * 1.3f),
                    c + Offset(cos(a), sin(a)) * (sr * len),
                    strokeWidth = max(1f, sr * 0.12f),
                    cap = StrokeCap.Round,
                )
            }
        }
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.White.copy(alpha = f),
                0.6f to lerpColor(col, Color.White, 0.4f).copy(alpha = f),
                1f to col.copy(alpha = f),
                center = c,
                radius = sr,
            ),
            radius = sr,
            center = c,
        )
    }

    /**
     * A living orb: a breathing aura, a roaming highlight over shaded glass, a slow inner swirl
     * and – depending on its mood – orbiting sparkles, rising embers or a falling drop.
     */
    private fun DrawScope.drawOrb(
        center: Offset,
        r: Float,
        color: Color,
        mood: Mood?,
        t: Float,
        ph: Float,
        f: Float,
        focused: Boolean,
        style: WebStyle,
    ) {
        if (r <= 0.3f || f <= 0.01f) return
        val speed = when (mood) {
            Mood.Joyful -> 2.4f
            Mood.Calm -> 0.8f
            Mood.Curious -> 1.6f
            Mood.Inspired -> 1.9f
            Mood.Fired -> 3.2f
            Mood.Blue -> 0.6f
            null -> 1.2f
        }
        val pulseAmount = when (mood) {
            Mood.Joyful -> 0.11f
            Mood.Fired -> 0.08f
            Mood.Inspired -> 0.07f
            Mood.Calm -> 0.06f
            else -> 0.045f
        }
        val beat = when (mood) {
            Mood.Joyful -> abs(sin(t * speed + ph))
            Mood.Fired -> (0.5f + 0.5f * (0.5f * sin(t * 7.3f + ph) + 0.3f * sin(t * 11.1f + ph * 2f) + 0.2f * sin(t * 3.1f))).coerceIn(0f, 1f)
            else -> 0.5f + 0.5f * sin(t * speed + ph)
        }
        var c0 = center
        when (mood) {
            Mood.Curious -> c0 += Offset(sin(t * 3f + ph), cos(t * 2.3f + ph)) * (r * 0.07f)
            Mood.Blue -> c0 += Offset(0f, r * 0.08f * sin(t * 0.8f + ph))
            else -> Unit
        }
        val rr = r * (1f + pulseAmount * (beat - 0.5f) * 2f)
        val tint = when (mood) {
            Mood.Fired -> lerpColor(color, EMBER, 0.6f)
            Mood.Blue -> lerpColor(color, RAIN, 0.5f)
            Mood.Inspired -> lerpColor(color, Color.White, 0.25f)
            else -> color
        }

        // Aura.
        val auraR = rr * (3f + 0.6f * beat)
        drawCircle(
            brush = Brush.radialGradient(
                0f to tint.copy(alpha = 0.5f * f * (0.75f + 0.25f * beat)),
                0.45f to tint.copy(alpha = 0.16f * f),
                1f to Color.Transparent,
                center = c0,
                radius = auraR,
            ),
            radius = auraR,
            center = c0,
        )

        if (mood != null && rr > 7f) {
            // Big enough to see its world: a tiny planet in its mood's biome, ringed in its colour.
            drawPlanet(
                c = c0,
                r = rr,
                biome = Biome.of(mood),
                t = t + ph * 5f,
                detail = ((rr - 16f) / 24f).coerceIn(0f, 1f),
                opacity = 0.3f + 0.7f * f,
                sky = rr > 26f,
            )
            drawCircle(color.copy(alpha = 0.55f * f), radius = rr * 1.22f, center = c0, style = Stroke(max(1f, rr * 0.05f)))
        } else {
            // Glassy body lit from a slowly roaming highlight.
            val la = t * speed * 0.6f + ph
            val light = c0 + Offset(cos(la), sin(la) * 0.6f - 0.5f) * (rr * 0.45f)
            val body = 0.3f + 0.7f * f
            drawCircle(
                brush = Brush.radialGradient(
                    0f to lerpColor(color, Color.White, 0.55f).copy(alpha = body),
                    0.55f to color.copy(alpha = body),
                    1f to lerpColor(color, Color.Black, 0.45f).copy(alpha = body),
                    center = light,
                    radius = rr * 1.4f,
                ),
                radius = rr,
                center = c0,
            )

            if (rr > 5f) {
                // Inner swirl.
                rotate(degrees = (t * speed * 40f + ph * 57f) % 360f, pivot = c0) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.26f * f),
                                Color.Transparent,
                                lerpColor(color, Color.White, 0.6f).copy(alpha = 0.22f * f),
                                Color.Transparent,
                            ),
                            center = c0,
                        ),
                        radius = rr * 0.9f,
                        center = c0,
                    )
                }
                drawCircle(
                    lerpColor(color, Color.White, 0.4f).copy(alpha = 0.35f * f),
                    radius = rr,
                    center = c0,
                    style = Stroke(max(0.8f, rr * 0.07f)),
                )
            }
            // Specular glint.
            drawCircle(Color.White.copy(alpha = 0.65f * f), radius = rr * 0.2f, center = light - Offset(rr * 0.05f, rr * 0.05f))

            if (rr > 6f) moodParticles(c0, rr, color, tint, mood, t, ph, speed, f)
        }

        if (focused) {
            val pulse = 0.5f + 0.5f * sin(t * 3f)
            drawCircle(
                style.focus.copy(alpha = 0.5f + 0.4f * pulse),
                radius = rr * (1.7f + 0.15f * pulse),
                center = c0,
                style = Stroke(1.5.dp.toPx()),
            )
        }
    }

    private fun DrawScope.moodParticles(
        c: Offset,
        rr: Float,
        color: Color,
        tint: Color,
        mood: Mood?,
        t: Float,
        ph: Float,
        speed: Float,
        f: Float,
    ) {
        when (mood) {
            Mood.Fired -> repeat(3) { i ->
                // Embers drifting up and out.
                val k = ((t * 0.9f + i / 3f + ph) % 1f + 1f) % 1f
                val pos = c + Offset(sin(k * 6f + i * 2f + ph) * rr * 0.6f, -rr * (1f + k * 2.4f))
                drawCircle(EMBER.copy(alpha = (1f - k) * f), radius = rr * 0.13f * (1f - k * 0.7f), center = pos)
            }
            Mood.Blue -> {
                // A single drop falling away.
                val k = ((t * 0.45f + ph) % 1f + 1f) % 1f
                val pos = c + Offset(0f, rr * (1.15f + k * 2f))
                drawCircle(RAIN.copy(alpha = (1f - k) * 0.85f * f), radius = rr * 0.12f, center = pos)
            }
            Mood.Joyful, Mood.Inspired, Mood.Curious -> {
                val count = when (mood) {
                    Mood.Inspired -> 5
                    Mood.Joyful -> 3
                    else -> 1
                }
                val tiltC = cos(ph)
                val tiltS = sin(ph)
                for (i in 0 until count) {
                    val ang = t * speed * (0.7f + 0.2f * i) + i * (2f * PI.toFloat() / count) + ph
                    val rx = rr * (1.8f + 0.25f * i)
                    val ry = rx * 0.45f
                    val ex = cos(ang) * rx
                    val ey = sin(ang) * ry
                    val pos = c + Offset(ex * tiltC - ey * tiltS, ex * tiltS + ey * tiltC)
                    val twinkle = 0.5f + 0.5f * sin(t * 5f + i * 1.7f + ph)
                    val sparkColor = if (mood == Mood.Joyful) lerpColor(color, Color.White, 0.5f) else Color.White
                    val size = rr * 0.1f * (0.6f + 0.6f * twinkle)
                    drawCircle(sparkColor.copy(alpha = f * (0.4f + 0.6f * twinkle)), radius = size, center = pos)
                    if (mood == Mood.Inspired) {
                        val arm = size * 2.6f
                        val a = f * 0.7f * twinkle
                        drawLine(tint.copy(alpha = a), pos - Offset(arm, 0f), pos + Offset(arm, 0f), strokeWidth = max(0.8f, size * 0.4f), cap = StrokeCap.Round)
                        drawLine(tint.copy(alpha = a), pos - Offset(0f, arm), pos + Offset(0f, arm), strokeWidth = max(0.8f, size * 0.4f), cap = StrokeCap.Round)
                    }
                }
            }
            Mood.Calm, null -> Unit
        }
    }

    private fun DrawScope.strand(a: Offset, b: Offset, color: Color, width: Float, bend: Float) {
        if (abs(bend) < 0.5f) {
            drawLine(color, a, b, strokeWidth = width, cap = StrokeCap.Round)
            return
        }
        val dir = b - a
        val len = dir.getDistance()
        if (len < 1f) return
        val normal = Offset(-dir.y / len, dir.x / len)
        val ctrl = (a + b) / 2f + normal * (bend * 2f)
        val c1 = a + (ctrl - a) * (2f / 3f)
        val c2 = b + (ctrl - b) * (2f / 3f)
        val path = Path().apply {
            moveTo(a.x, a.y)
            cubicTo(c1.x, c1.y, c2.x, c2.y, b.x, b.y)
        }
        drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round))
    }

    private fun DrawScope.drawBrokenStrand(br: BrokenStrand, s: SnapFx, now: Long, style: WebStyle) {
        val age = (now - s.start) / 1e9f
        if (age > BREAK_TIME) return
        val parent = base[br.oldParentId]?.let { camera.project(it) } ?: return
        val node = camera.project(br.dropPos) ?: return
        val a = Offset(parent.x, parent.y)
        val b = Offset(node.x, node.y)
        val mid = (a + b) / 2f
        val e = easeOutCubic(age / BREAK_TIME)
        val color = colorFor(s.nodeId, style).copy(alpha = 0.8f * (1f - e))
        val width = max(1f, 0.02f * parent.ppu)
        // Both halves whip back toward their anchors, curling as they go.
        strand(a, lerp(mid, a, e), color, width, 18f * (1f - e))
        strand(b, lerp(mid, b, e), color, width, -18f * (1f - e))
        // Spark where it broke.
        val sparkLen = 26f * sin(PI.toFloat() * e.coerceIn(0f, 1f))
        for (i in 0 until 6) {
            val ang = i * (PI.toFloat() / 3f) + 0.4f
            val dir = Offset(cos(ang), sin(ang))
            drawLine(
                style.focus.copy(alpha = 0.9f * (1f - e)),
                mid + dir * (sparkLen * 0.4f),
                mid + dir * sparkLen,
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }

    /** Spokes and a spiral that spin up around a new thought, then fade into the web. */
    private fun DrawScope.weaveBurst(c: Offset, radiusPx: Float, age: Float, color: Color) {
        val alpha = 1f - smoothstep(1.0f, 1.8f, age)
        if (alpha <= 0f) return
        val grow = easeOutCubic(age / 0.9f)
        val r = radiusPx * (0.55f + 0.45f * grow)
        val rot = age * 0.9f
        val spokes = 9
        val step = 2f * PI.toFloat() / spokes
        for (i in 0 until spokes) {
            val ang = rot + i * step
            drawLine(
                color.copy(alpha = 0.5f * alpha),
                c,
                c + Offset(cos(ang), sin(ang)) * (r * grow),
                strokeWidth = 1.2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        val total = spokes * 4
        val shown = (total * ((age - 0.15f) / 0.9f).coerceIn(0f, 1f)).toInt()
        if (shown > 1) {
            val path = Path()
            for (k in 0..shown) {
                val ang = rot + k * step
                val rr = r * (0.18f + 0.82f * k / total)
                val pt = c + Offset(cos(ang), sin(ang)) * rr
                if (k == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
            }
            drawPath(path, color.copy(alpha = 0.45f * alpha), style = Stroke(1.dp.toPx()))
        }
        val ring = (age / 0.7f).coerceIn(0f, 1f)
        if (ring < 1f) {
            drawCircle(
                color.copy(alpha = 0.8f * (1f - ring)),
                radius = r * 1.25f * easeOutCubic(ring),
                center = c,
                style = Stroke(2.5.dp.toPx() * (1f - ring) + 0.5f),
            )
        }
    }

    // ---------------------------------------------------------------- types

    private class DragState(
        val id: String,
        val subtree: Set<String>,
        val startScreen: Offset,
        val depth: Float,
        var offset: Vec3 = Vec3.ZERO,
        var candidate: String? = null,
    )

    private class BrokenStrand(val oldParentId: String, val dropPos: Vec3)

    private class SnapFx(
        val nodeId: String,
        val newParentId: String?,
        val broken: BrokenStrand?,
        val dropOffset: Vec3,
        val subtree: Set<String>,
        val start: Long,
    )

    private class Ripple(val anchorId: String, val start: Long)

    private class Dust(val pos: Vec3, val size: Float, val twinkle: Float)

    companion object {
        const val NODE_R = 0.13f
        const val SPAWN_DELAY_MS = 900L
        private const val SPAWN_TIME = 1.1f
        private const val SPAWN_LIFE = 2_400_000_000L
        private const val SNAP_LIFE = 1_300_000_000L
        private const val RIPPLE_LIFE = 900_000_000L
        private const val BREAK_TIME = 0.55f
        private const val IDLE_AFTER = 5_000_000_000L
        private const val ROT_PER_PX = 0.0075f
        private const val BREATHE = 0.035f
        private const val EDIT_OFFSET = -0.2f
        private const val MIN_HIT_PX = 44f
        private const val MAX_LABELS = 36
        private const val DUST_COUNT = 240
        private const val MAX_ORBITS = 14
        private const val ORBIT_STEPS = 48
        private val EMBER = Color(0xFFFF8A50)
        private val RAIN = Color(0xFF7FA8FF)
    }
}

/** A cached text layout for a thought's label. */
class LabelLayout(val text: String, val result: TextLayoutResult)
