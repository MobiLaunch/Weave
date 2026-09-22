package com.mobilaunch.weave.web

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.PI

private enum class GestureMode { Pending, Rotate, Transform, DragNode }

/**
 * The interactive 3D web. One finger spins it, two fingers pinch-zoom and twist it,
 * a tap opens a thought and a long press picks a thought up so it can be re-attached elsewhere.
 */
@Composable
fun WebCanvas(
    scene: WebScene,
    style: WebStyle,
    interactive: Boolean,
    onTapNote: (String?) -> Unit,
    onPickUp: () -> Unit,
    onMoved: (MoveResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val maxLabelWidth = with(LocalDensity.current) { 180.dp.roundToPx() }
    val labels = remember(measurer, style.labelStyle, maxLabelWidth) {
        LabelCache(measurer, style, maxLabelWidth)
    }
    val canInteract by rememberUpdatedState(interactive)
    val tap by rememberUpdatedState(onTapNote)
    val pickUp by rememberUpdatedState(onPickUp)
    val moved by rememberUpdatedState(onMoved)

    LaunchedEffect(scene) {
        while (true) withFrameNanos { scene.tick(it) }
    }

    Canvas(
        modifier.pointerInput(scene) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                scene.touchStart()
                try {
                    val hit = if (canInteract) scene.hitTest(down.position) else null
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    val longPressAt = down.uptimeMillis + viewConfiguration.longPressTimeoutMillis
                    var mode = GestureMode.Pending
                    var travelled = 0f

                    while (true) {
                        val event: PointerEvent? = if (mode == GestureMode.Pending && hit != null) {
                            val elapsed = currentEvent.changes.firstOrNull()?.uptimeMillis ?: down.uptimeMillis
                            withTimeoutOrNull((longPressAt - elapsed).coerceAtLeast(1L)) { awaitPointerEvent() }
                        } else {
                            awaitPointerEvent()
                        }

                        if (event == null) {
                            // Held still on a thought: pick it up.
                            if (hit != null && scene.beginDrag(hit, down.position)) {
                                mode = GestureMode.DragNode
                                pickUp()
                            } else {
                                mode = GestureMode.Rotate
                            }
                            continue
                        }

                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) {
                            when (mode) {
                                GestureMode.Pending -> if (canInteract) tap(hit)
                                GestureMode.Rotate -> {
                                    val v = tracker.calculateVelocity()
                                    scene.fling(v.x, v.y)
                                }
                                GestureMode.DragNode -> scene.endDrag()?.let { moved(it) }
                                GestureMode.Transform -> Unit
                            }
                            event.changes.forEach { it.consume() }
                            break
                        }

                        if (pressed.size >= 2 && mode != GestureMode.DragNode) {
                            mode = GestureMode.Transform
                            scene.camera.zoomBy(event.calculateZoom())
                            scene.camera.roll(-event.calculateRotation() * (PI.toFloat() / 180f))
                            val pan = event.calculatePan()
                            scene.rotateByDrag(pan.x * 0.5f, pan.y * 0.5f)
                        } else {
                            val change = pressed.first()
                            val delta: Offset = change.position - change.previousPosition
                            when (mode) {
                                GestureMode.Pending -> {
                                    travelled += delta.getDistance()
                                    if (travelled > viewConfiguration.touchSlop) mode = GestureMode.Rotate
                                }
                                GestureMode.Rotate -> {
                                    scene.rotateByDrag(delta.x, delta.y)
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                }
                                GestureMode.DragNode -> scene.dragTo(change.position)
                                GestureMode.Transform -> Unit
                            }
                        }
                        event.changes.forEach { it.consume() }
                    }
                } finally {
                    // A cancelled gesture must not leave a thought hanging mid-air.
                    if (scene.isDragging) scene.endDrag()?.let { moved(it) }
                    scene.touchEnd()
                }
            }
        },
    ) {
        scene.draw(this, style) { id, text -> labels.get(id, text) }
    }
}

private class LabelCache(
    private val measurer: TextMeasurer,
    private val style: WebStyle,
    private val maxWidth: Int,
) {
    private val cache = HashMap<String, LabelLayout>()

    fun get(id: String, text: String): LabelLayout {
        cache[id]?.let { if (it.text == text) return it }
        val result = measurer.measure(
            text = AnnotatedString(text),
            style = style.labelStyle,
            overflow = TextOverflow.Ellipsis,
            maxLines = 2,
            constraints = Constraints(maxWidth = maxWidth),
        )
        return LabelLayout(text, result).also { cache[id] = it }
    }
}
