package com.example

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip

@Composable
fun AnalyticsScreen(modifier: Modifier = Modifier, onNavigate: (AppScreen) -> Unit, onNavigateBack: () -> Unit = {}) {
    val context = LocalContext.current
    val bgDark = Color(0xFF1A1C1E)
    val textLight = Color(0xFFE2E2E6)
    val accentBlue = Color(0xFFD0E4FF)
    val panelBg = Color(0xFF2D2F31)

    val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
    val files = downloadsDir?.listFiles()?.toList()?.filter { it.extension == "json" } ?: emptyList()

    val totalFiles = files.size
    val totalSizeKb = files.sumOf { it.length() } / 1024

    Column(modifier = modifier.fillMaxSize().background(bgDark)) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textLight)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Analytics",
                color = textLight,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.5).sp
            )
        }

        Column(modifier = Modifier.weight(1f).padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AnalyticsCard(
                    modifier = Modifier.weight(1f),
                    title = "Total Captures",
                    value = totalFiles.toString(),
                    icon = Icons.Default.Folder,
                    accentBlue = accentBlue,
                    textLight = textLight,
                    panelBg = panelBg
                )
                AnalyticsCard(
                    modifier = Modifier.weight(1f),
                    title = "Total Storage",
                    value = "${totalSizeKb / 1024} MB",
                    icon = Icons.Default.Analytics,
                    accentBlue = accentBlue,
                    textLight = textLight,
                    panelBg = panelBg
                )
            }
        }

    }
}

@Composable
fun AnalyticsCard(modifier: Modifier = Modifier, title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accentBlue: Color, textLight: Color, panelBg: Color) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(panelBg)
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Icon(imageVector = icon, contentDescription = title, tint = accentBlue, modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = title, color = textLight.copy(alpha = 0.6f), fontSize = 14.sp)
        Text(text = value, color = textLight, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}
