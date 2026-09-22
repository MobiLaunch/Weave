package com.mobilaunch.weave.web

import kotlin.math.min
import kotlin.random.Random

/** Decides where thoughts live in 3D space and which silk strands tie neighbours together. */
object WebLayout {
    const val STRAND = 1.5f
    private const val SILK_RANGE = 2.7f
    private const val SILK_PER_NODE = 2

    /**
     * Finds a spot one strand away from [parent], preferring directions that grow the web outward
     * (or toward [preferred], when the user dropped the thought somewhere specific) and that keep
     * clear of existing thoughts.
     */
    fun placeChild(
        parent: Vec3?,
        others: List<Vec3>,
        preferred: Vec3? = null,
        rnd: Random = Random.Default,
    ): Vec3 {
        if (parent == null) {
            if (others.isEmpty()) return Vec3.ZERO
            val c = centroid(others)
            val r = others.maxOf { it.distanceTo(c) }
            return c + Vec3.randomUnit(rnd) * (r + STRAND)
        }
        val c = if (others.isEmpty()) parent else centroid(others)
        val outward = (parent - c).normalized(Vec3.randomUnit(rnd))
        val bias = preferred?.normalized(outward) ?: outward
        val jitter = if (preferred != null) 0.45f else 1f

        var best = parent + bias * STRAND
        var bestScore = Float.NEGATIVE_INFINITY
        repeat(40) {
            val dir = (Vec3.randomUnit(rnd) * jitter + bias * 0.9f).normalized(bias)
            val candidate = parent + dir * (STRAND * (0.85f + rnd.nextFloat() * 0.35f))
            val clearance = others.minOfOrNull { it.distanceTo(candidate) } ?: STRAND
            val score = min(clearance, STRAND * 1.4f) + dir.dot(bias) * 0.6f
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best
    }

    fun centroid(points: Collection<Vec3>): Vec3 {
        if (points.isEmpty()) return Vec3.ZERO
        var sum = Vec3.ZERO
        for (p in points) sum += p
        return sum / points.size.toFloat()
    }

    /** The thought plus everything that branches off it. */
    fun subtree(root: String, parentOf: Map<String, String?>): Set<String> {
        val children = HashMap<String, MutableList<String>>()
        for ((id, parent) in parentOf) if (parent != null) children.getOrPut(parent) { mutableListOf() } += id
        val out = LinkedHashSet<String>()
        val queue = ArrayDeque(listOf(root))
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (out.add(id)) children[id]?.let { queue.addAll(it) }
        }
        return out
    }

    /** How many strands separate each thought from the start of its branch. */
    fun generations(parentOf: Map<String, String?>): Map<String, Int> {
        val out = HashMap<String, Int>()
        for (id in parentOf.keys) {
            var depth = 0
            var cursor = parentOf[id]
            val seen = HashSet<String>().apply { add(id) }
            while (cursor != null && seen.add(cursor)) {
                depth++
                cursor = parentOf[cursor]
            }
            out[id] = depth
        }
        return out
    }

    /** Faint cross-strands between nearby thoughts that aren't directly related. */
    fun silkLinks(positions: Map<String, Vec3>, parentOf: Map<String, String?>): List<Pair<String, String>> {
        val ids = positions.keys.toList()
        val seen = HashSet<String>()
        val out = ArrayList<Pair<String, String>>()
        for (a in ids) {
            val pa = positions.getValue(a)
            ids.asSequence()
                .filter { b -> b != a && parentOf[a] != b && parentOf[b] != a }
                .map { b -> b to positions.getValue(b).distanceTo(pa) }
                .filter { it.second < SILK_RANGE }
                .sortedBy { it.second }
                .take(SILK_PER_NODE)
                .forEach { (b, _) ->
                    val key = if (a < b) "$a|$b" else "$b|$a"
                    if (seen.add(key)) out += a to b
                }
        }
        return out
    }
}
