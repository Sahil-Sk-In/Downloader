package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.HistoryFilter
import com.example.ui.MainViewModel
import com.example.ui.components.DownloadItemCard
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceCard
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonRose
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val completedDownloads by viewModel.filteredCompletedDownloads.collectAsState()
    val rawCompleted by viewModel.rawCompletedDownloads.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val historyFilter by viewModel.historyFilter.collectAsState()

    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Vault History", color = TextPrimary) },
            text = { Text("Are you sure you want to clear all completed history entries?", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearCompletedHistory()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear All", color = NeonRose, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Media Vault",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${rawCompleted.size} media saved offline",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }

                if (rawCompleted.isNotEmpty()) {
                    IconButton(
                        onClick = { showClearDialog = true },
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurfaceElevated)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear History",
                            tint = NeonRose
                        )
                    }
                }
            }
        }

        if (rawCompleted.isNotEmpty()) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_history_field"),
                    placeholder = { Text("Search title, channel, format...", color = TextMuted, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = NeonCyan)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = DarkSurfaceBorder,
                        focusedContainerColor = DarkSurfaceCard,
                        unfocusedContainerColor = DarkSurfaceCard,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        text = "All (${rawCompleted.size})",
                        isSelected = historyFilter == HistoryFilter.ALL,
                        onClick = { viewModel.setHistoryFilter(HistoryFilter.ALL) }
                    )
                    FilterChip(
                        text = "🎬 Videos (${rawCompleted.count { !it.isAudioOnly }})",
                        isSelected = historyFilter == HistoryFilter.VIDEO,
                        onClick = { viewModel.setHistoryFilter(HistoryFilter.VIDEO) }
                    )
                    FilterChip(
                        text = "🎵 Audio (${rawCompleted.count { it.isAudioOnly }})",
                        isSelected = historyFilter == HistoryFilter.AUDIO,
                        onClick = { viewModel.setHistoryFilter(HistoryFilter.AUDIO) }
                    )
                }
            }
        }

        if (completedDownloads.isEmpty()) {
            item {
                Spacer(modifier = Modifier.height(60.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = NeonPurple,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (rawCompleted.isEmpty()) "No Media in Vault Yet" else "No matching files",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (rawCompleted.isEmpty()) "Completed downloads will appear here with instant offline playback" else "Try clearing your search query",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            items(completedDownloads.size) { index ->
                val item = completedDownloads[index]
                DownloadItemCard(
                    item = item,
                    onCancel = { viewModel.cancelDownload(context, item.id) },
                    onRetry = { viewModel.retryDownload(context, item) },
                    onPlay = { viewModel.playMedia(item) },
                    onShare = { viewModel.shareMediaFile(context, item) },
                    onOpenExternal = { viewModel.openInExternalApp(context, item) },
                    onDelete = { viewModel.deleteItem(item, deleteLocalFile = true) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun FilterChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) NeonCyan.copy(alpha = 0.15f) else DarkSurfaceElevated)
            .border(
                1.dp,
                if (isSelected) NeonCyan else DarkSurfaceBorder,
                RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = if (isSelected) NeonCyan else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
