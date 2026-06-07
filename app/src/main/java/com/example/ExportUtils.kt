package com.example

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

fun shareMocapFile(context: Context, file: File, format: String) {
    val fileToShare = if (format == "bvh" && file.extension == "json") {
        val bvhFile = File(file.parentFile, file.nameWithoutExtension + ".bvh")
        if (!bvhFile.exists()) {
            try {
                BvhExporter().export(file, bvhFile)
            } catch (e: Exception) {
                bvhFile.writeText("ERROR parsing JSON for BVH: ${e.message}")
            }
        }
        bvhFile
    } else {
        file
    }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fileToShare)
    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_STREAM, uri)
        type = if (fileToShare.extension == "json") "application/json" else "application/octet-stream"
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share MoCap Data"))
}

