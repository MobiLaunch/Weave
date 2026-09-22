package com.mobilaunch.weave.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobilaunch.weave.WeaveViewModel
import com.mobilaunch.weave.audio.WebSounds
import com.mobilaunch.weave.web.WebCanvas
import com.mobilaunch.weave.web.WebScene
import com.mobilaunch.weave.web.WebStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface EditorTarget {
    data class New(val parentId: String?) : EditorTarget
    data class Existing(val id: String) : EditorTarget
}

@Composable
fun WeaveApp(vm: WeaveViewModel = viewModel()) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    val scene = remember { WebScene() }
    SideEffect { scene.sync(notes) }

    val sounds = remember { WebSounds() }
    DisposableEffect(sounds) { onDispose { sounds.release() } }

    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val style = rememberWebStyle()
    var editor by remember { mutableStateOf<EditorTarget?>(null) }
    var showList by rememberSaveable { mutableStateOf(false) }

    fun openExisting(id: String) {
        scene.focusOn(id, editing = true)
        editor = EditorTarget.Existing(id)
    }

    fun openNew() {
        val parent = scene.focusedId ?: notes.maxByOrNull { it.createdAt }?.id
        parent?.let { scene.focusOn(it, editing = true) }
        editor = EditorTarget.New(parent)
    }

    Box(Modifier.fillMaxSize().background(style.background)) {
        WebCanvas(
            scene = scene,
            style = style,
            interactive = editor == null,
            onTapNote = { id -> if (id == null) scene.clearFocus() else openExisting(id) },
            onPickUp = { view.haptic(HapticFeedbackConstants.LONG_PRESS) },
            onMoved = { move ->
                vm.moveNote(move)
                sounds.playSnap()
                view.confirmHaptic()
            },
            modifier = Modifier.fillMaxSize(),
        )

        TopBar(
            count = notes.size,
            onList = { showList = true },
            onRecenter = { scene.recenter() },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        if (notes.isEmpty() && editor == null) {
            EmptyState(Modifier.align(Alignment.Center).padding(32.dp))
        } else if (notes.size in 1..4 && editor == null) {
            Text(
                "Drag to spin · Pinch to zoom · Hold a thought to move it",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, bottom = 96.dp),
            )
        }

        AnimatedVisibility(
            visible = editor == null && !scene.isDragging,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(20.dp),
        ) {
            ExtendedFloatingActionButton(
                onClick = { openNew() },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("New thought") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        editor?.let { target ->
            key(target) {
                val existing = (target as? EditorTarget.Existing)?.let { t -> notes.firstOrNull { it.id == t.id } }
                if (target is EditorTarget.Existing && existing == null) {
                    // The thought vanished underneath us (e.g. deleted); close quietly.
                    LaunchedEffect(Unit) {
                        editor = null
                        scene.releaseEditingView()
                    }
                } else {
                    val parentSnippet = (target as? EditorTarget.New)?.parentId
                        ?.let { pid -> notes.firstOrNull { it.id == pid }?.snippet }
                    NoteEditor(
                        isNew = target is EditorTarget.New,
                        initialText = existing?.text.orEmpty(),
                        createdAt = existing?.createdAt,
                        updatedAt = existing?.updatedAt,
                        branchFrom = parentSnippet,
                        flyTarget = {
                            if (existing == null) scene.viewCenter() else scene.screenPos(existing.id)
                        },
                        onSave = { text ->
                            when (target) {
                                is EditorTarget.New -> {
                                    val note = vm.createNote(text, target.parentId)
                                    scene.onNoteCreated(note, WebScene.SPAWN_DELAY_MS)
                                    scope.launch {
                                        delay(WebScene.SPAWN_DELAY_MS)
                                        sounds.playWeave()
                                        view.confirmHaptic()
                                    }
                                }
                                is EditorTarget.Existing -> vm.updateNote(target.id, text)
                            }
                        },
                        onDelete = {
                            if (target is EditorTarget.Existing) {
                                vm.deleteNote(target.id)
                                scene.clearFocus()
                            }
                        },
                        onClosed = {
                            if (editor == target) editor = null
                            scene.releaseEditingView()
                        },
                    )
                }
            }
        }
    }

    if (showList) {
        ThoughtList(
            notes = notes,
            colorFor = { id -> style.palette[scene.colorIndexOf(id) % style.palette.size] },
            onPick = { id ->
                showList = false
                openExisting(id)
            },
            onDismiss = { showList = false },
        )
    }
}

@Composable
private fun rememberWebStyle(): WebStyle {
    val cs = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
    return remember(cs, labelStyle) {
        WebStyle(
            background = cs.surfaceContainerLowest,
            glow = cs.primary.copy(alpha = 0.12f),
            dust = cs.onSurface,
            palette = listOf(cs.primary, cs.tertiary, cs.secondary, lerp(cs.primary, cs.tertiary, 0.5f)),
            focus = cs.primary,
            label = cs.onSurface,
            labelBackground = cs.surfaceContainerHigh.copy(alpha = 0.78f),
            labelStyle = labelStyle,
        )
    }
}

@Composable
private fun TopBar(count: Int, onList: () -> Unit, onRecenter: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 24.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Weave", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                when (count) {
                    0 -> "A web of thoughts"
                    1 -> "1 thought"
                    else -> "$count thoughts"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalIconButton(onClick = onList) {
            Icon(Icons.AutoMirrored.Rounded.List, contentDescription = "All thoughts")
        }
        FilledTonalIconButton(onClick = onRecenter) {
            Icon(Icons.Rounded.Home, contentDescription = "Recenter the web")
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Your web is waiting",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Every thought you add becomes a strand. Keep adding and watch it grow.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private fun View.haptic(constant: Int) {
    performHapticFeedback(constant)
}

private fun View.confirmHaptic() {
    haptic(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
}
