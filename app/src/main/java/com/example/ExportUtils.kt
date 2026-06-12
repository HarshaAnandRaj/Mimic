package com.example

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun shareMocapFile(context: Context, file: File, format: String, onStart: () -> Unit = {}, onComplete: () -> Unit = {}) {
    if (file.length() == 0L) {
        android.widget.Toast.makeText(context, "Error: File is empty or corrupted.", android.widget.Toast.LENGTH_LONG).show()
        onComplete()
        return
    }

    val activity = context as? Activity
    if (activity != null) {
        AlertDialog.Builder(activity)
            .setTitle("Supporting Independent Software \uD83D\uDE4F")
            .setMessage("Mind the inconvenience. We want to remain independent from corporations and keep this app free for public use. A short ad will play now so we can continue to serve you. Thank you!")
            .setPositiveButton("Continue") { _, _ ->
                loadAndShowAd(context, file, format, onStart, onComplete)
            }
            .setNegativeButton("Cancel") { _, _ ->
                // Do nothing
            }
            .setCancelable(false)
            .show()
    } else {
        loadAndShowAd(context, file, format, onStart, onComplete)
    }
}

private fun loadAndShowAd(context: Context, file: File, format: String, onStart: () -> Unit, onComplete: () -> Unit) {
    onStart()
    
    val adRequest = AdRequest.Builder().build()
    // Test Rewarded Ad Unit ID
    RewardedAd.load(context, "ca-app-pub-3940256099942544/5224354917", adRequest, object : RewardedAdLoadCallback() {
        override fun onAdFailedToLoad(adError: LoadAdError) {
            Log.d("AdMob", "Ad Failed to load: ${adError.message}")
            // Proceed to export if ad fails to load
            performExport(context, file, format, onComplete)
        }

        override fun onAdLoaded(rewardedAd: RewardedAd) {
            val activity = context as? Activity
            if (activity != null) {
                rewardedAd.fullScreenContentCallback = object: FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        // User dismissed the ad, now process export
                        performExport(context, file, format, onComplete)
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        performExport(context, file, format, onComplete)
                    }
                }
                
                // Show the ad
                rewardedAd.show(activity) { rewardItem ->
                    // User earned the reward, we process export when ad is dismissed
                    Log.d("AdMob", "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                }
            } else {
                performExport(context, file, format, onComplete)
            }
        }
    })
}

private fun performExport(context: Context, file: File, format: String, onComplete: () -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        val urisToShare = ArrayList<android.net.Uri>()
        val providerAuth = "${context.packageName}.fileprovider"
        // 1. Add JSON if requested
        if (format == "json" || format == "both") {
            urisToShare.add(FileProvider.getUriForFile(context, providerAuth, file))
        }
        // 2. Generate and Add BVH if requested
        if ((format == "bvh" || format == "both") && file.extension == "json") {
            val exportDir = File(context.cacheDir, "exports")
            if (!exportDir.exists()) exportDir.mkdirs()
            val bvhFile = File(exportDir, file.nameWithoutExtension + ".bvh")
            
            if (!bvhFile.exists()) {
                try {
                    BvhExporter().export(file, bvhFile)
                } catch (e: Throwable) {
                    bvhFile.writeText("ERROR parsing JSON for BVH: ${e.message}")
                }
            }
            urisToShare.add(FileProvider.getUriForFile(context, providerAuth, bvhFile))
        }
        // 3. Trigger the Share Sheet
        withContext(Dispatchers.Main) {
            val shareIntent = if (urisToShare.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_STREAM, urisToShare.first())
                    type = if (format == "json") "application/json" else "application/octet-stream"
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply { // Allows multiple files
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, urisToShare)
                    type = "*/*"
                }
            }
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(shareIntent, "Share MoCap Data"))
            onComplete()
        }
    }
}


