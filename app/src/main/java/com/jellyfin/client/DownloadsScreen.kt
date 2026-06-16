package com.jellyfin.client

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

// Tombol Play + Download dalam satu pill shape.
// Sisi kiri: tombol play (ungu penuh). Sisi kanan: tombol download (ungu redup).
@Composable
fun PlayDownloadPill(
    playLabel: String,
    onPlay: () -> Unit,
    itemId: String,
    itemName: String,
    itemType: String,
    imageUrl: String,
    streamUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val entry = DownloadManager.downloads[itemId]
    val dlStatus = entry?.status
    var confirmDelete by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(50.dp))
    ) {
        // Sisi kiri — Play
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0xFF7B3FE4))
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = playLabel,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1
                )
            }
        }

        // Pemisah tipis (warna background app, menciptakan celah antar dua sisi)
        Spacer(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(Color(0xFF0A0A0A))
        )

        // Sisi kanan — Download (ungu lebih redup)
        Box(
            modifier = Modifier
                .width(66.dp)
                .fillMaxHeight()
                .background(Color(0xFF3D1F6E))
                .clickable {
                    when (dlStatus) {
                        null -> DownloadManager.startDownload(context, itemId, itemName, itemType, imageUrl, streamUrl)
                        "error" -> DownloadManager.retry(context, itemId)
                        "downloading" -> DownloadManager.delete(context, itemId)
                        "done" -> confirmDelete = true
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            when (dlStatus) {
                "downloading" -> Text(
                    "${((entry?.progress ?: 0f) * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                "done" -> Icon(Icons.Filled.DownloadDone, contentDescription = null, tint = Color(0xFF9FFFB0))
                "error" -> Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color(0xFFFFCDD2))
                else -> Icon(Icons.Filled.Download, contentDescription = null, tint = Color.White.copy(alpha = 0.85f))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            confirmButton = {
                TextButton(onClick = { DownloadManager.delete(context, itemId); confirmDelete = false }) {
                    Text(strings.downloadDeleteBtn, color = Color(0xFFD32F2F))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(strings.cancelBtn) }
            },
            title = { Text(strings.downloadDeleteTitle) },
            text = { Text(strings.downloadDeleteMsg.format(itemName)) }
        )
    }
}

@Composable
fun DownloadButton(
    id: String,
    name: String,
    type: String,
    imageUrl: String,
    streamUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val entry = DownloadManager.downloads[id]
    val status = entry?.status
    var confirmDelete by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .height(50.dp)
            .width(58.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x16FFFFFF))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .clickable {
                when (status) {
                    null -> DownloadManager.startDownload(context, id, name, type, imageUrl, streamUrl)
                    "error" -> DownloadManager.retry(context, id)
                    "downloading" -> DownloadManager.delete(context, id)
                    "done" -> confirmDelete = true
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when (status) {
            "downloading" -> Text(
                text = "${((entry?.progress ?: 0f) * 100).toInt()}%",
                color = Color(0xFF7B3FE4),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            "done" -> Icon(Icons.Filled.DownloadDone, contentDescription = strings.downloadStatusDone, tint = Color(0xFF4CAF50))
            "error" -> Icon(Icons.Filled.Refresh, contentDescription = strings.downloadStatusError, tint = Color(0xFFD32F2F))
            else -> Icon(Icons.Filled.Download, contentDescription = strings.tabDownload, tint = Color.White)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            confirmButton = {
                TextButton(onClick = { DownloadManager.delete(context, id); confirmDelete = false }) {
                    Text(strings.downloadDeleteBtn, color = Color(0xFFD32F2F))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(strings.cancelBtn) }
            },
            title = { Text(strings.downloadDeleteTitle) },
            text = { Text(strings.downloadDeleteMsg.format(name)) }
        )
    }
}

@Composable
fun DownloadsScreen(
    onPlayOffline: (localPath: String, id: String, name: String, type: String, imageUrl: String) -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val items = DownloadManager.downloads.values.sortedByDescending { it.timestamp }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 40.dp)
    ) {
        Text(
            text = strings.tabDownload,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.DownloadForOffline,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(strings.downloadEmptyTitle, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        strings.downloadEmptyDesc,
                        color = Color.Gray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
                items(items.size) { index ->
                    DownloadRow(
                        entry = items[index],
                        strings = strings,
                        onPlay = { e -> if (e.status == "done") onPlayOffline(e.localPath, e.id, e.name, e.type, e.imageUrl) },
                        onRetry = { e -> DownloadManager.retry(context, e.id) },
                        onDelete = { e -> DownloadManager.delete(context, e.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    entry: DownloadEntry,
    strings: AppStrings,
    onPlay: (DownloadEntry) -> Unit,
    onRetry: (DownloadEntry) -> Unit,
    onDelete: (DownloadEntry) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onPlay(entry) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 60.dp, height = 90.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray)
        ) {
            AsyncImage(
                model = entry.imageUrl,
                contentDescription = entry.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (entry.status == "done") {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = strings.playBtn, tint = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            when (entry.status) {
                "downloading" -> {
                    LinearProgressIndicator(
                        progress = { entry.progress },
                        color = Color(0xFF7B3FE4),
                        trackColor = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth().height(4.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${(entry.progress * 100).toInt()}% • ${strings.downloadStatusDownloading}",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
                "done" -> Text(strings.downloadStatusDone, color = Color(0xFF4CAF50), fontSize = 12.sp)
                else -> Text(
                    strings.downloadStatusError,
                    color = Color(0xFFD32F2F),
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { onRetry(entry) }
                )
            }
        }

        IconButton(onClick = { onDelete(entry) }) {
            Icon(Icons.Filled.Delete, contentDescription = strings.downloadDeleteBtn, tint = Color.Gray)
        }
    }
}
