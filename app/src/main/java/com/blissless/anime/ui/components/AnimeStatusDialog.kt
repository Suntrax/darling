package com.blissless.anime.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.blissless.anime.data.models.AnimeMedia

@Composable
fun AnimeStatusDialog(
    anime: AnimeMedia,
    onDismiss: () -> Unit,
    onUpdateProgress: (Int) -> Unit,
    onUpdateStatus: (String) -> Unit,
    onDelete: () -> Unit
) {
    var selectedStatus by remember { mutableStateOf(anime.listStatus) }
    var episodeInput by remember { mutableStateOf(anime.progress.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = anime.title,
                maxLines = 2,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column {
                // Episode progress
                Text("Episode Progress", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = episodeInput,
                    onValueChange = { episodeInput = it.filter { c -> c.isDigit() } },
                    label = {
                        val total = if (anime.totalEpisodes > 0) " / ${anime.totalEpisodes}" else ""
                        Text("Episodes watched$total")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                // Status selection
                Text("List Status", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))

                val statuses = listOf(
                    "CURRENT" to "Watching",
                    "PLANNING" to "Plan to Watch",
                    "COMPLETED" to "Completed",
                    "DROPPED" to "Dropped",
                    "PAUSED" to "On Hold"
                )

                statuses.forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedStatus = value }
                    ) {
                        RadioButton(
                            selected = selectedStatus == value,
                            onClick = { selectedStatus = value }
                        )
                        Text(label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val episodes = episodeInput.toIntOrNull() ?: 0
                    // Update progress first, then status
                    onUpdateProgress(episodes)
                    onUpdateStatus(selectedStatus)
                    onDismiss()
                }
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        onDelete()
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Remove")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}