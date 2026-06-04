package com.example.cheapestoilfinder.station.api

import com.example.cheapestoilfinder.BuildConfig

object BackendApiConfig {
    val DEFAULT_BASE_URL: String = BuildConfig.BACKEND_BASE_URL

    fun normalizeBaseUrl(baseUrl: String?): String {
        require(!baseUrl.isNullOrBlank()) { "baseUrl must not be null or empty" }
        val trimmed = baseUrl.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}
