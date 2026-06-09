package com.example.cheapestoilfinder.destination.api

data class DestinationAutocompleteItem(
    var entryType: String? = null,
    var displayText: String? = null,
    var primaryText: String? = null,
    var secondaryText: String? = null,
    var sourceType: String? = null,
    var sourceRef: String? = null,
    var latitude: Double? = null,
    var longitude: Double? = null
)
