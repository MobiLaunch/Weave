package com.mobilaunch.weave.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobilaunch.weave.data.Category
import com.mobilaunch.weave.data.Mood
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class EditorExit { IntoWeb, Dismiss }

/**
 * Floating card for writing or editing a thought. On save it shrinks and flies into its spot
 * in the web while the camera pulls back.
 */
@Composable
fun NoteEditor(
    isNew: Boolean,
    initialText: String,
    createdAt: Long?,
    updatedAt: Long?,
    branchFrom: String?,
    initialMood: Mood?,
    initialCategory: Category?,
    flyTarget: () -> Offset?,
    onSave: (text: String, mood: Mood?, category: Category?) -> Unit,
    onDelete: () -> Unit,
    onClosed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    var mood by rememberSaveable { mutableStateOf(initialMood) }
    var category by rememberSaveable { mutableStateOf(initialCategory) }
    val accent by animateColorAsState(
        category?.let { Color(it.argb) } ?: MaterialTheme.colorScheme.outlineVariant,
        label = "editorAccent",
    )
    val saveInteraction = remember { MutableInteractionSource() }
    val enter = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    var exitKind by remember { mutableStateOf<EditorExit?>(null) }
    var cardCenter by remember { mutableStateOf(Offset.Unspecified) }
    var confirmDelete by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        enter.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 420f))
    }
    LaunchedEffect(Unit) {
        if (isNew) {
            delay(150)
            focusRequester.requestFocus()
        }
    }
    LaunchedEffect(exitKind) {
        val kind = exitKind ?: return@LaunchedEffect
        keyboard?.hide()
        exit.animateTo(
            1f,
            tween(durationMillis = if (kind == EditorExit.IntoWeb) 720 else 240, easing = FastOutSlowInEasing),
        )
        onClosed()
    }
    BackHandler(enabled = exitKind == null) { exitKind = EditorExit.Dismiss }

    fun close(kind: EditorExit) {
        if (exitKind == null) exitKind = kind
    }

    Box(
        modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
            .padding(16.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .onGloballyPositioned { cardCenter = it.boundsInRoot().center }
                .graphicsLayer {
                    val e = enter.value
                    val x = exit.value
                    var scale = 0.9f + 0.1f * e
                    var a = e.coerceIn(0f, 1f)
                    translationY = (1f - e) * 80.dp.toPx()
                    when (exitKind) {
                        EditorExit.IntoWeb -> {
                            val target = flyTarget()
                            if (target != null && cardCenter.isSpecified) {
                                translationX = (target.x - cardCenter.x) * x
                                translationY += (target.y - cardCenter.y) * x
                            }
                            scale *= 1f - 0.97f * x
                            a *= 1f - (x * x * x)
                        }
                        EditorExit.Dismiss -> {
                            scale *= 1f - 0.06f * x
                            a *= 1f - x
                            translationY += 40.dp.toPx() * x
                        }
                        null -> Unit
                    }
                    scaleX = scale
                    scaleY = scale
                    alpha = a
                },
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(1.5.dp, accent.copy(alpha = 0.6f)),
        ) {
            Column(Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (isNew) "New thought" else "Thought",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            stampLine(isNew, createdAt, updatedAt),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { close(EditorExit.Dismiss) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }
                if (isNew && branchFrom != null) {
                    Text(
                        "Branching from “$branchFrom”",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("What's on your mind?") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 12.dp, top = 4.dp)
                        .heightIn(min = 110.dp, max = 260.dp)
                        .focusRequester(focusRequester),
                )
                MoodPicker(mood, { mood = it }, Modifier.padding(top = 4.dp, end = 12.dp))
                CategoryPicker(category, { category = it }, Modifier.padding(top = 10.dp, end = 12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp, end = 12.dp),
                ) {
                    if (!isNew) {
                        TextButton(
                            onClick = { confirmDelete = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Delete")
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            if (exitKind == null) {
                                onSave(text.trim(), mood, category)
                                close(EditorExit.IntoWeb)
                            }
                        },
                        enabled = text.isNotBlank() && exitKind == null,
                        interactionSource = saveInteraction,
                        modifier = Modifier.pressBounce(saveInteraction),
                    ) {
                        Text(if (isNew) "Weave it in" else "Save")
                    }
                }
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
                    close(EditorExit.Dismiss)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Keep") }
            },
        )
    }
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
