package com.example.cheapestoilfinder.destination.api

data class DestinationAutocompleteResponse(
    var query: String = "",
    var resultCount: Int = 0,
    var items: MutableList<DestinationAutocompleteItem> = mutableListOf()
)
