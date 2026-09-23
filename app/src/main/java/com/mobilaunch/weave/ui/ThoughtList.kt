package com.mobilaunch.weave.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobilaunch.weave.data.Category
import com.mobilaunch.weave.data.Note

/** Every thought in the web, grouped by category, most recently touched first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThoughtList(
    notes: List<Note>,
    colorFor: (String) -> Color,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val groups: List<Pair<Category?, List<Note>>> = remember(notes) {
        val byCategory = notes.sortedByDescending { it.updatedAt }.groupBy { it.category }
        Category.entries.mapNotNull { c -> byCategory[c]?.let { c to it } } +
            listOfNotNull(byCategory[null]?.let { null to it })
    }
    val now = remember(notes) { System.currentTimeMillis() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "All thoughts",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (notes.isEmpty()) {
            Text(
                "Nothing here yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            )
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            for ((category, group) in groups) {
                // Only label the groups once there's more than one kind of thought.
                if (groups.size > 1 || category != null) {
                    item(key = "header-${category?.key ?: "none"}") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
                        ) {
                            if (category != null) {
                                CategoryDot(category, 10.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                "${category?.label ?: "Uncategorized"} · ${group.size}",
                                style = MaterialTheme.typography.titleSmall,
                                color = category?.let { Color(it.argb) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(group, key = { it.id }) { note ->
                    val rest = note.text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.drop(1).joinToString(" ")
                    ListItem(
                        overlineContent = {
                            Text(
                                DateUtils.getRelativeTimeSpanString(note.updatedAt, now, DateUtils.MINUTE_IN_MILLIS).toString() +
                                    " · " + formatStamp(note.createdAt),
                            )
                        },
                        headlineContent = { Text(note.snippet, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = if (rest.isNotEmpty()) {
                            { Text(rest, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        } else {
                            null
                        },
                        leadingContent = {
                            val color = colorFor(note.id)
                            Box(
                                Modifier.size(36.dp).background(color.copy(alpha = 0.22f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                val mood = note.mood
                                if (mood != null) {
                                    Text(mood.emoji, fontSize = 18.sp)
                                } else {
                                    Box(Modifier.size(12.dp).background(color, CircleShape))
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .clickable { onPick(note.id) }
                            .padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}
