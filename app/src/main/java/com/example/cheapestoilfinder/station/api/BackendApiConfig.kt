package com.example.cheapestoilfinder.station.api

object BackendApiConfig {
    const val DEFAULT_DEBUG_BASE_URL = "http://10.0.2.2:8080/"

    fun normalizeBaseUrl(baseUrl: String?): String {
        require(!baseUrl.isNullOrBlank()) { "baseUrl must not be null or empty" }
        val trimmed = baseUrl.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}
