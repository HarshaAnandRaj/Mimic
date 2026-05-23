package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

@Composable
fun PrivacyScreen(modifier: Modifier = Modifier, onAccept: () -> Unit) {
    val bgDark = Color(0xFF1A1C1E)
    val textLight = Color(0xFFE2E2E6)
    val accentBlue = Color(0xFFD0E4FF)
    val panelBg = Color(0xFF2D2F31)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bgDark)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.PrivacyTip,
            contentDescription = "Privacy",
            tint = accentBlue,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Privacy & Safety",
            color = textLight,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PrivacyItem(
                icon = Icons.Default.Security,
                title = "On-Device Processing",
                description = "All motion tracking is processed directly on your device. We do not upload your camera feed to servers."
            )
            PrivacyItem(
                icon = Icons.Default.Storage,
                title = "Local Storage",
                description = "Your motion capture data (JSON/BVH) is saved strictly to your device's local storage unless you explicitly share it."
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "By continuing, you acknowledge that Mimic needs Camera access to function, and you control your data.",
            color = textLight.copy(alpha = 0.6f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onAccept,
            colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("I Understand & Accept", color = bgDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PrivacyItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String) {
    val panelBg = Color(0xFF2D2F31)
    val textLight = Color(0xFFE2E2E6)
    val accentBlue = Color(0xFFD0E4FF)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(panelBg)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentBlue,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, color = textLight, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = description, color = textLight.copy(alpha = 0.7f), fontSize = 14.sp)
        }
    }
}
