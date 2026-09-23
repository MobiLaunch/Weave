package com.mobilaunch.weave.ui

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobilaunch.weave.data.Category
import com.mobilaunch.weave.data.Mood
import com.mobilaunch.weave.data.MoodBlend
import com.mobilaunch.weave.ui.theme.Twilight
import com.mobilaunch.weave.data.MoodSense
import com.mobilaunch.weave.world.Biome
import com.mobilaunch.weave.world.drawPlanet
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.ui.geometry.lerp as lerpGeometry

private enum class WorldExit { IntoWeb, Dismiss }

/**
 * Full-screen writing space. It grows out of wherever it was opened from ([origin]); a little
 * world at the top reshapes itself around the mood of what's being written. Saving drops the
 * world into the web as a new orb.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorldEditor(
    isNew: Boolean,
    initialText: String,
    createdAt: Long?,
    updatedAt: Long?,
    branchFrom: String?,
    initialBlend: MoodBlend?,
    initialCategory: Category?,
    origin: Rect?,
    flyTarget: () -> Offset?,
    orbRadiusPx: Float,
    onSave: (text: String, blend: MoodBlend?, category: Category?) -> Unit,
    onDelete: () -> Unit,
    onClosed: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    // Hand-picked moods (up to two), saved as keys so they survive rotation.
    var chosenKeys by rememberSaveable {
        mutableStateOf(listOfNotNull(initialBlend?.primary, initialBlend?.secondary).joinToString(",") { it.key })
    }
    val chosen = remember(chosenKeys) { chosenKeys.split(",").mapNotNull { Mood.fromKey(it) } }
    var auto by rememberSaveable { mutableStateOf(initialBlend == null) }
    var category by rememberSaveable { mutableStateOf(initialCategory) }
    var exit by remember { mutableStateOf<WorldExit?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var planetArea by remember { mutableStateOf<Rect?>(null) }

    val sensed = remember(text) { MoodSense.sense(text) }
    val blend = if (auto) sensed else chosen.firstOrNull()?.let { MoodBlend(it, chosen.getOrNull(1), if (chosen.size > 1) 0.42f else 0f) }
    val world = World(Biome.of(blend?.primary), blend?.secondary?.let { Biome.of(it) })
    val mix by animateFloatAsState(blend?.mix ?: 0f, tween(700), label = "mix")

    val view = LocalView.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val open = remember { Animatable(0f) }
    val fly = remember { Animatable(0f) }
    val morph = remember { Animatable(1f) }
    var shownWorld by remember { mutableStateOf(world) }
    var fromWorld by remember { mutableStateOf<World?>(null) }
    val time = remember { mutableFloatStateOf(0f) }
    val growth by animateFloatAsState((text.length / 180f).coerceIn(0f, 1f), tween(600), label = "growth")
    val stars = remember {
        val rnd = Random(11)
        List(90) { Star(rnd.nextFloat(), rnd.nextFloat(), 0.5f + rnd.nextFloat() * 1.4f, rnd.nextFloat() * 6.28f) }
    }

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time.floatValue = (it - start) / 1e9f }
    }
    LaunchedEffect(Unit) {
        open.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        if (isNew) {
            delay(380)
            focusRequester.requestFocus()
        }
    }
    // The world reshapes itself whenever the feelings change.
    LaunchedEffect(world) {
        if (world != shownWorld) {
            fromWorld = shownWorld
            shownWorld = world
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            morph.snapTo(0f)
            morph.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
            fromWorld = null
        }
    }
    LaunchedEffect(exit) {
        when (exit) {
            WorldExit.IntoWeb -> {
                keyboard?.hide()
                fly.animateTo(1f, tween(1050, easing = FastOutSlowInEasing))
                onClosed()
            }
            WorldExit.Dismiss -> {
                keyboard?.hide()
                open.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
                onClosed()
            }
            null -> Unit
        }
    }
    BackHandler(enabled = exit == null) { exit = WorldExit.Dismiss }

    val seed = MaterialTheme.colorScheme.primaryContainer
    val saveInteraction = remember { MutableInteractionSource() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val full = Rect(0f, 0f, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val start = origin ?: Rect(full.center, 1f)

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val o = open.value
                    clip = true
                    shape = RevealShape(lerpGeometry(start, full, o), (1f - o) * 28.dp.toPx())
                }
                .drawBehind {
                    val o = open.value
                    val f = fly.value
                    val fade = 1f - smoothstep(0.12f, 0.6f, f)
                    // A bright twilight tinted by the world's own atmosphere.
                    val tint = shownWorld.primary.atmosphere
                    drawRect(
                        Brush.verticalGradient(
                            listOf(
                                lerp(Twilight.top, tint, 0.35f).copy(alpha = fade),
                                Twilight.bottom.copy(alpha = fade),
                            ),
                        ),
                    )
                    drawRect(seed.copy(alpha = fade * (1f - smoothstep(0f, 0.55f, o))))
                    val sky = smoothstep(0.3f, 1f, o) * fade
                    if (sky > 0f) {
                        val c = planetArea?.center ?: center
                        drawCircle(
                            Brush.radialGradient(listOf(tint.copy(alpha = 0.45f * sky), Color.Transparent), center = c, radius = size.maxDimension * 0.6f),
                            radius = size.maxDimension * 0.6f,
                            center = c,
                        )
                        val t = time.floatValue
                        for (s in stars) {
                            val tw = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * 1.6f + s.phase))
                            drawCircle(Color.White.copy(alpha = 0.7f * tw * sky), radius = s.size, center = Offset(s.x * size.width, s.y * size.height))
                        }
                    }
                },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = smoothstep(0.4f, 1f, open.value) * (1f - smoothstep(0f, 0.3f, fly.value))
                    }
                    .systemBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    IconButton(onClick = { if (exit == null) exit = WorldExit.Dismiss }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text(if (isNew) "New thought" else "Thought", style = MaterialTheme.typography.titleLarge)
                        Text(
                            stampLine(isNew, createdAt, updatedAt),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                // Room for the world; it's painted on the layer above so it can fly off on save.
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onGloballyPositioned { planetArea = it.boundsInRoot() },
                )

                AnimatedContent(
                    targetState = blend to world,
                    transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(250)) },
                    contentKey = { it.second },
                    label = "worldTitle",
                    modifier = Modifier.fillMaxWidth(),
                ) { (b, w) ->
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (b != null) {
                            Text(
                                "${b.emoji}  ${b.label}",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                            )
                        }
                        Text(
                            w.primary.titleWith(w.secondary),
                            style = MaterialTheme.typography.titleSmall,
                            color = lerp(w.primary.atmosphere, Color.White, 0.55f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                if (isNew && branchFrom != null) {
                    Text(
                        "Branching from “$branchFrom”",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    )
                }

                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("What's on your mind? Your world is listening…") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .heightIn(min = 96.dp, max = 200.dp)
                        .focusRequester(focusRequester),
                )
                MoodPicker(
                    selected = chosen,
                    onSelect = { picked ->
                        chosenKeys = picked.joinToString(",") { it.key }
                        auto = picked.isEmpty()
                    },
                    auto = auto,
                    sensed = sensed,
                    onAuto = { auto = true },
                    modifier = Modifier.padding(top = 10.dp),
                )
                if (!WindowInsets.isImeVisible) {
                    CategoryPicker(category, { category = it }, Modifier.padding(top = 8.dp))
                }
                Button(
                    onClick = {
                        if (exit == null) {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onSave(text.trim(), blend, category)
                            exit = WorldExit.IntoWeb
                        }
                    },
                    enabled = text.isNotBlank() && exit == null,
                    interactionSource = saveInteraction,
                    modifier = Modifier
                        .pressBounce(saveInteraction)
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 12.dp),
                ) {
                    Text(if (isNew) "Weave it into the web" else "Save", modifier = Modifier.padding(vertical = 6.dp))
                }
            }

            // The world itself.
            Canvas(Modifier.fillMaxSize()) {
                val area = planetArea ?: return@Canvas
                val t = time.floatValue
                val o = open.value
                val f = fly.value
                val appear = smoothstep(0.25f, 1f, o)
                if (appear <= 0f) return@Canvas
                val m = morph.value
                val bump = 1f + 0.12f * sin(m * PI.toFloat())
                var r = min(area.width, area.height) * 0.34f * (0.62f + 0.38f * growth) * (0.4f + 0.6f * appear) * bump
                var c = area.center
                if (f > 0f) {
                    val target = flyTarget() ?: full.center
                    c = lerpGeometry(c, target, f)
                    r = r + (orbRadiusPx - r) * f
                }
                val opacity = appear * (1f - smoothstep(0.88f, 1f, f))
                val detail = (0.2f + growth).coerceAtMost(1f) * (1f - f)
                val sky = r > 40f
                fromWorld?.let { drawPlanet(c, r, it.primary, t, detail, opacity, sky, it.secondary, mix) }
                val w = shownWorld
                drawPlanet(c, r, w.primary, t, detail, opacity * if (fromWorld != null) m else 1f, sky, w.secondary, mix)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this thought?") },
            text = { Text("Anything branching from it will reconnect to the thought it grew from.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                    exit = WorldExit.Dismiss
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Keep") }
            },
        )
    }
}

/** Which biomes make up the world: its main feeling and, for mixed feelings, a second. */
private data class World(val primary: Biome, val secondary: Biome?)

private class Star(val x: Float, val y: Float, val size: Float, val phase: Float)

/** A rounded window onto the full-screen editor, used for the grow-from-button reveal. */
private class RevealShape(private val rect: Rect, private val corner: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(rect, CornerRadius(corner)))
}

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private val stampFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.getDefault())

internal fun formatStamp(millis: Long): String =
    stampFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private fun stampLine(isNew: Boolean, createdAt: Long?, updatedAt: Long?): String = when {
    isNew || createdAt == null -> "Now · ${formatStamp(System.currentTimeMillis())}"
    updatedAt != null && updatedAt - createdAt > 60_000 ->
        "Created ${formatStamp(createdAt)} · Edited ${formatStamp(updatedAt)}"
    else -> "Created ${formatStamp(createdAt)}"
}
