package com.example

import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
fun LibraryScreen(modifier: Modifier = Modifier, onNavigate: (AppScreen) -> Unit, onNavigateBack: () -> Unit = {}, onPlay: (File) -> Unit = {}) {
    val context = LocalContext.current
    val bgDark = androidx.compose.material3.MaterialTheme.colorScheme.background
    val textLight = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
    val accentBlue = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val panelBg = androidx.compose.material3.MaterialTheme.colorScheme.surface
    val btnBg = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant

    val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
    var files by remember { mutableStateOf(downloadsDir?.listFiles()?.toList()?.filter { it.extension == "json" }?.sortedByDescending { it.lastModified() } ?: emptyList()) }

    var showRenameDialog by remember { mutableStateOf<File?>(null) }
    var newName by remember { mutableStateOf("") }
    
    var showExportDialog by remember { mutableStateOf<File?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    
    val refreshFiles = {
        files = downloadsDir?.listFiles()?.toList()?.filter { it.extension == "json" }?.sortedByDescending { it.lastModified() } ?: emptyList()
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
                        val safeName = newName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                        val extension = fileToRename.extension
                        val finalName = if (safeName.endsWith(".$extension")) safeName else "$safeName.$extension"
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

    if (isExporting) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Exporting...", color = textLight) },
            text = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = accentBlue, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Processing MoCap data, please wait...", color = textLight.copy(alpha = 0.8f))
                }
            },
            confirmButton = {},
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textLight)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Library",
                    color = textLight,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.5).sp
                )
            }
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
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(files) { file ->
                    FileItem(
                        file = file,
                        accentBlue = accentBlue,
                        textLight = textLight,
                        panelBg = panelBg,
                        onDelete = { fileToDelete ->
                            val jsonDeleted = fileToDelete.delete()
                            val bvhFile = java.io.File(fileToDelete.parentFile, fileToDelete.nameWithoutExtension + ".bvh")
                            if (bvhFile.exists()) {
                                bvhFile.delete()
                            }
                            if (jsonDeleted) {
                                refreshFiles()
                            }
                        },
                        onRename = {
                            newName = it.nameWithoutExtension
                            showRenameDialog = it
                        },
                        onPlay = { f -> onPlay(f) },
                        onExportJson = { f -> 
                            shareMocapFile(context, f, "json", onStart = { isExporting = true }, onComplete = { isExporting = false })
                        },
                        onExportBvh = { f ->
                            shareMocapFile(context, f, "bvh", onStart = { isExporting = true }, onComplete = { isExporting = false })
                        }
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
    onPlay: (File) -> Unit,
    onExportJson: (File) -> Unit,
    onExportBvh: (File) -> Unit
) {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    val date = sdf.format(Date(file.lastModified()))
    val sizeKb = file.length() / 1024
    
    val isFaceTracking = file.name.startsWith("face_")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(panelBg)
            .border(
                androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color.White.copy(alpha=0.2f), Color.White.copy(alpha=0.02f)))),
                RoundedCornerShape(16.dp)
            )
            .clickable { onPlay(file) }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).background(accentBlue.copy(alpha=0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFaceTracking) Icons.Default.Face else Icons.Default.Accessibility, 
                    contentDescription = null, 
                    tint = accentBlue,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.nameWithoutExtension,
                    color = textLight,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
                IconButton(onClick = { onRename(file) }) {
                    Icon(Icons.Default.Edit, contentDescription = "Rename", tint = textLight.copy(alpha = 0.7f))
                }
                IconButton(onClick = { onDelete(file) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.8f))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val compactPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            val compactText = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            // 1. Play Button (Always visible)
            Button(
                onClick = { onPlay(file) },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = accentBlue),
                shape = RoundedCornerShape(8.dp),
                contentPadding = compactPadding
            ) {
                Text("Play", style = compactText, color = Color.Black)
            }
            
            // 2. Dynamic Export Button
            if (isFaceTracking) {
                // Face Tracking relies on JSON for the 52 Blendshapes
                androidx.compose.material3.OutlinedButton(
                    onClick = { onExportJson(file) },
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = accentBlue),
                    border = androidx.compose.foundation.BorderStroke(1.dp, accentBlue.copy(alpha=0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = compactPadding
                ) {
                    Text("Export Blendshapes (.JSON)", style = compactText)
                }
            } else {
                // Body Tracking gives them the industry standard
                androidx.compose.material3.OutlinedButton(
                    onClick = { onExportBvh(file) },
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = accentBlue),
                    border = androidx.compose.foundation.BorderStroke(1.dp, accentBlue.copy(alpha=0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = compactPadding
                ) {
                    Text("Export MoCap (.BVH)", style = compactText)
                }
            }
        }
    }
}
