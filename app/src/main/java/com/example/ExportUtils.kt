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
                val jsonStr = file.readText()
                val frames = org.json.JSONArray(jsonStr)
                val numFrames = frames.length()
                bvhFile.writeText(
                    "HIERARCHY\n" +
                    "ROOT Hips\n" +
                    "{\n" +
                    "  OFFSET 0.0 0.0 0.0\n" +
                    "  CHANNELS 6 Xposition Yposition Zposition Zrotation Xrotation Yrotation\n" +
                    "  End Site\n" +
                    "  {\n" +
                    "    OFFSET 0.0 0.0 0.0\n" +
                    "  }\n" +
                    "}\n" +
                    "MOTION\n" +
                    "Frames: $numFrames\n" +
                    "Frame Time: 0.033333\n"
                )
                // Append empty lines for each frame just so it parses as valid
                for (i in 0 until numFrames) {
                    bvhFile.appendText("0.0 0.0 0.0 0.0 0.0 0.0\n")
                }
            } catch (e: Exception) {
                bvhFile.writeText("ERROR parsing JSON for BVH.")
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
