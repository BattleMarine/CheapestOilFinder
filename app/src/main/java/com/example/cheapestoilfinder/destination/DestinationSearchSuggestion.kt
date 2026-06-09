package com.example.cheapestoilfinder.destination

data class DestinationSearchSuggestion(
    val displayText: String,
    val description: String,
    val searchTokens: List<String> = emptyList(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val sourceRef: String? = null
)
