package com.mobilaunch.weave.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.graphics.Color
import com.mobilaunch.weave.data.Category
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
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
    /** Where the full-screen editor grows out of. */
    val origin: Rect?

    data class New(val parentId: String?, override val origin: Rect?) : EditorTarget
    data class Existing(val id: String, override val origin: Rect?) : EditorTarget
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
    var fabBounds by remember { mutableStateOf<Rect?>(null) }
    val density = LocalDensity.current
    val orbRadiusPx = with(density) { 14.dp.toPx() }

    fun openExisting(id: String) {
        val origin = scene.screenPos(id)?.let { Rect(it, with(density) { 24.dp.toPx() }) }
        scene.focusOn(id, editing = false)
        editor = EditorTarget.Existing(id, origin)
    }

    fun openNew() {
        val parent = scene.focusedId ?: notes.maxByOrNull { it.createdAt }?.id
        editor = EditorTarget.New(parent, fabBounds)
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
            onTick = { view.haptic(HapticFeedbackConstants.CLOCK_TICK) },
            onDoubleTapEmpty = {
                view.haptic(HapticFeedbackConstants.CONTEXT_CLICK)
                scene.recenter()
            },
            modifier = Modifier.fillMaxSize(),
        )

        Column(Modifier.align(Alignment.TopCenter).statusBarsPadding()) {
            TopBar(
                count = notes.size,
                onList = { showList = true },
                onRecenter = { scene.recenter() },
            )
            val counts = remember(notes) { notes.mapNotNull { it.category }.groupingBy { it }.eachCount() }
            AnimatedVisibility(visible = counts.isNotEmpty() && editor == null) {
                CategoryFilterBar(
                    counts = counts,
                    total = notes.size,
                    selected = scene.filter,
                    onSelect = { category ->
                        view.haptic(HapticFeedbackConstants.CLOCK_TICK)
                        scene.applyFilter(category)
                    },
                )
            }
        }

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
            val fabInteraction = remember { MutableInteractionSource() }
            ExtendedFloatingActionButton(
                onClick = { openNew() },
                interactionSource = fabInteraction,
                modifier = Modifier
                    .pressBounce(fabInteraction)
                    .onGloballyPositioned { fabBounds = it.boundsInRoot() },
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
                    val parent = (target as? EditorTarget.New)?.parentId
                        ?.let { pid -> notes.firstOrNull { it.id == pid } }
                    WorldEditor(
                        isNew = target is EditorTarget.New,
                        initialText = existing?.text.orEmpty(),
                        createdAt = existing?.createdAt,
                        updatedAt = existing?.updatedAt,
                        branchFrom = parent?.snippet,
                        initialMood = existing?.mood,
                        // New thoughts inherit the category being viewed, or their parent's.
                        initialCategory = existing?.category ?: scene.filter ?: parent?.category,
                        origin = target.origin,
                        flyTarget = {
                            if (existing == null) scene.viewCenter() else scene.screenPos(existing.id)
                        },
                        orbRadiusPx = orbRadiusPx,
                        onSave = { text, mood, category ->
                            when (target) {
                                is EditorTarget.New -> {
                                    val note = vm.createNote(text, target.parentId, mood, category)
                                    scene.onNoteCreated(note, WebScene.SPAWN_DELAY_MS)
                                    scope.launch {
                                        delay(WebScene.SPAWN_DELAY_MS)
                                        sounds.playWeave()
                                        view.confirmHaptic()
                                    }
                                }
                                is EditorTarget.Existing -> vm.updateNote(target.id, text, mood, category)
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
            colorFor = { id -> scene.colorFor(id, style) },
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
            .padding(start = 24.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Weave", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            // The count rolls up or down as thoughts come and go.
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith
                        (slideOutVertically { if (up) -it else it } + fadeOut())
                },
                label = "count",
            ) { n ->
                Text(
                    when (n) {
                        0 -> "A web of thoughts"
                        1 -> "1 thought"
                        else -> "$n thoughts"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val listInteraction = remember { MutableInteractionSource() }
        FilledTonalIconButton(onClick = onList, interactionSource = listInteraction, modifier = Modifier.pressBounce(listInteraction)) {
            Icon(Icons.AutoMirrored.Rounded.List, contentDescription = "All thoughts")
        }
        val homeInteraction = remember { MutableInteractionSource() }
        FilledTonalIconButton(onClick = onRecenter, interactionSource = homeInteraction, modifier = Modifier.pressBounce(homeInteraction)) {
            Icon(Icons.Rounded.Home, contentDescription = "Recenter the web")
        }
    }
}

/** "All" plus every category in use, with counts. Picking one lights only those thoughts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterBar(
    counts: Map<Category, Int>,
    total: Int,
    selected: Category?,
    onSelect: (Category?) -> Unit,
) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("All · $total") },
        )
        for (category in Category.entries) {
            val n = counts[category] ?: continue
            key(category) {
            val interaction = remember { MutableInteractionSource() }
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(if (selected == category) null else category) },
                label = { Text("${category.label} · $n") },
                leadingIcon = { CategoryDot(category, 8.dp) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
                    selectedContainerColor = Color(category.argb).copy(alpha = 0.3f),
                ),
                interactionSource = interaction,
                modifier = Modifier.pressBounce(interaction),
            )
            }
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
