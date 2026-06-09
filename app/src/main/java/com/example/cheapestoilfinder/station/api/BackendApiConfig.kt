package com.example.cheapestoilfinder.station.api

import android.content.Context
import android.util.Log
import com.example.cheapestoilfinder.BuildConfig
import java.io.IOException

object BackendApiConfig {
    private const val TAG = "BackendApiConfig"
    private const val BACKEND_BASE_URL_ASSET = "backend_base_url.txt"
    val DEFAULT_BASE_URL: String = BuildConfig.BACKEND_BASE_URL

    fun defaultBaseUrl(context: Context): String {
        val assetBaseUrl = readBackendBaseUrlAsset(context)
        return assetBaseUrl ?: DEFAULT_BASE_URL
    }

    fun normalizeBaseUrl(baseUrl: String?): String {
        require(!baseUrl.isNullOrBlank()) { "baseUrl must not be null or empty" }
        val trimmed = baseUrl.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    private fun readBackendBaseUrlAsset(context: Context): String? {
        return try {
            context.assets.open(BACKEND_BASE_URL_ASSET).bufferedReader().use { reader ->
                reader.readText().trim().takeIf { it.isNotBlank() }
            }
        } catch (exception: IOException) {
            Log.w(TAG, "backend_base_url asset not found. Falling back to BuildConfig value.")
            null
        }
    }
}
