package com.mobilaunch.weave.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobilaunch.weave.data.Category
import com.mobilaunch.weave.data.Mood
import kotlinx.coroutines.launch

/** Squishes a little while pressed and springs back on release. */
fun Modifier.pressBounce(interactionSource: MutableInteractionSource): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "pressBounce",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** A row of emoji moods. Picking one makes it hop; tapping it again clears it. */
@Composable
fun MoodPicker(selected: Mood?, onSelect: (Mood?) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            selected?.let { "Feeling ${it.label.lowercase()}" } ?: "How does this feel?",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (mood in Mood.entries) {
                MoodBubble(mood, mood == selected) { onSelect(if (mood == selected) null else mood) }
            }
        }
    }
}

@Composable
private fun MoodBubble(mood: Mood, selected: Boolean, onClick: () -> Unit) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val hop = remember { Animatable(0f) }
    val spin = remember { Animatable(0f) }
    val scale by animateFloatAsState(
        if (selected) 1.15f else 1f,
        spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow),
        label = "moodScale",
    )
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "moodBg",
    )
    val ring by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "moodRing",
    )
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressBounce(interaction)
            .size(46.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = -hop.value * 10.dp.toPx()
                rotationZ = spin.value
            }
            .background(background, CircleShape)
            .border(2.dp, ring, CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                scope.launch {
                    hop.snapTo(0f)
                    hop.animateTo(1f, spring(stiffness = Spring.StiffnessHigh))
                    hop.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
                }
                scope.launch {
                    spin.snapTo(-14f)
                    spin.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessLow))
                }
                onClick()
            }
            .semantics { contentDescription = mood.label },
        contentAlignment = Alignment.Center,
    ) {
        Text(mood.emoji, fontSize = 22.sp)
    }
}

/** Category chips, each with its colour dot. Tapping the selected one clears it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPicker(selected: Category?, onSelect: (Category?) -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    Column(modifier) {
        Text(
            "Category",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (category in Category.entries) {
                val isSelected = category == selected
                val interaction = remember { MutableInteractionSource() }
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onSelect(if (isSelected) null else category)
                    },
                    label = { Text(category.label) },
                    leadingIcon = { CategoryDot(category, if (isSelected) 10.dp else 8.dp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(category.argb).copy(alpha = 0.28f),
                    ),
                    interactionSource = interaction,
                    modifier = Modifier.pressBounce(interaction),
                )
            }
        }
    }
}

@Composable
fun CategoryDot(category: Category, size: androidx.compose.ui.unit.Dp = 10.dp) {
    Box(Modifier.size(size).background(Color(category.argb), CircleShape))
}
