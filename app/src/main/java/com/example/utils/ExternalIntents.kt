package com.example.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

object ExternalIntents {
    fun openGoogleMaps(context: Context, venueName: String, mapsUrl: String?) {
        val targetUri = when {
            !mapsUrl.isNullOrBlank() && (mapsUrl.startsWith("http://") || mapsUrl.startsWith("https://") || mapsUrl.startsWith("geo:")) ->
                Uri.parse(mapsUrl)
            else -> {
                val encoded = URLEncoder.encode(venueName, "UTF-8")
                Uri.parse("geo:0,0?q=$encoded")
            }
        }
        val mapIntent = Intent(Intent.ACTION_VIEW, targetUri)
        runCatching { context.startActivity(mapIntent) }
    }

    fun shareToWhatsApp(context: Context, message: String, phoneNumber: String? = null) {
        val cleanPhone = phoneNumber?.replace(Regex("[^0-9]"), "")
        if (!cleanPhone.isNullOrBlank()) {
            val encodedMsg = URLEncoder.encode(message, "UTF-8")
            val waUrl = Uri.parse("https://wa.me/$cleanPhone?text=$encodedMsg")
            val intent = Intent(Intent.ACTION_VIEW, waUrl)
            runCatching { context.startActivity(intent); return }
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            setPackage("com.whatsapp")
        }
        runCatching {
            context.startActivity(shareIntent)
        }.onFailure {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
            }
            context.startActivity(Intent.createChooser(fallback, "Share to WhatsApp"))
        }
    }
}
