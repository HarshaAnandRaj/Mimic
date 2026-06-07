package com.example

import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(modifier: Modifier = Modifier, onNavigate: (AppScreen) -> Unit, onPlay: (File) -> Unit = {}) {
    val context = LocalContext.current
    val bgDark = Color(0xFF1A1C1E)
    val textLight = Color(0xFFE2E2E6)
    val accentBlue = Color(0xFFD0E4FF)
    val panelBg = Color(0xFF2D2F31)
    val btnBg = Color(0xFF3D4758)

    val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
    var files by remember { mutableStateOf(downloadsDir?.listFiles()?.toList()?.filter { it.extension == "json" || it.extension == "bvh" }?.sortedByDescending { it.lastModified() } ?: emptyList()) }

    var showRenameDialog by remember { mutableStateOf<File?>(null) }
    var newName by remember { mutableStateOf("") }
    
    var showExportDialog by remember { mutableStateOf<File?>(null) }
    
    val refreshFiles = {
        files = downloadsDir?.listFiles()?.toList()?.filter { it.extension == "json" || it.extension == "bvh" }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    LaunchedEffect(Unit) {
        refreshFiles()
    }

    if (showRenameDialog != null) {
        val fileToRename = showRenameDialog!!
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Rename File", color = textLight) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentBlue,
                        unfocusedBorderColor = textLight.copy(alpha = 0.5f),
                        focusedTextColor = textLight,
                        unfocusedTextColor = textLight
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        val extension = fileToRename.extension
                        val finalName = if (newName.endsWith(".$extension")) newName else "$newName.$extension"
                        val newFile = File(fileToRename.parentFile, finalName)
                        if (fileToRename.renameTo(newFile)) {
                            refreshFiles()
                        } else {
                            Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                    showRenameDialog = null
                }) {
                    Text("Rename", color = accentBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
                    Text("Cancel", color = textLight)
                }
            },
            containerColor = panelBg
        )
    }

    if (showExportDialog != null) {
        val fileToShare = showExportDialog!!
        AlertDialog(
            onDismissRequest = { showExportDialog = null },
            title = { Text("Export Format", color = textLight) },
            text = { Text("Choose the format you want to export:", color = textLight.copy(alpha = 0.8f)) },
            confirmButton = {
                TextButton(onClick = {
                    shareMocapFile(context, fileToShare, "json")
                    showExportDialog = null
                }) {
                    Text("JSON", color = accentBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    shareMocapFile(context, fileToShare, "bvh")
                    showExportDialog = null
                }) {
                    Text("BVH (Beta)", color = accentBlue)
                }
            },
            containerColor = panelBg
        )
    }

    Column(modifier = modifier.fillMaxSize().background(bgDark)) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Library",
                color = textLight,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.5).sp
            )
            IconButton(onClick = refreshFiles) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = textLight)
            }
        }

        if (files.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, tint = textLight.copy(alpha = 0.5f), modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No captures yet.", color = textLight.copy(alpha = 0.5f), fontSize = 16.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(files) { file ->
                    FileItem(
                        file = file,
                        accentBlue = accentBlue,
                        textLight = textLight,
                        panelBg = panelBg,
                        onDelete = {
                            if (it.delete()) {
                                refreshFiles()
                            }
                        },
                        onRename = {
                            newName = it.nameWithoutExtension
                            showRenameDialog = it
                        },
                        onShare = {
                            showExportDialog = it
                        },
                        onPlay = { file -> onPlay(file) }
                    )
                }
            }
        }

    }
}

@Composable
fun FileItem(
    file: File,
    accentBlue: Color,
    textLight: Color,
    panelBg: Color,
    onDelete: (File) -> Unit,
    onRename: (File) -> Unit,
    onShare: (File) -> Unit,
    onPlay: (File) -> Unit = {}
) {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    val date = sdf.format(Date(file.lastModified()))
    val sizeKb = file.length() / 1024

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(panelBg)
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .clickable { onPlay(file) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(Color(0xFF60A5FA).copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = accentBlue)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                color = textLight,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = date, color = textLight.copy(alpha = 0.5f), fontSize = 12.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.size(3.dp).background(textLight.copy(alpha = 0.5f), CircleShape))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "${sizeKb} KB", color = textLight.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }
        
        Row {
            IconButton(onClick = { onShare(file) }) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = textLight.copy(alpha = 0.7f))
            }
            IconButton(onClick = { onRename(file) }) {
                Icon(Icons.Default.Edit, contentDescription = "Rename", tint = textLight.copy(alpha = 0.7f))
            }
            IconButton(onClick = { onDelete(file) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.8f))
            }
        }
    }
}
