package com.example.cheapestoilfinder.destination

import com.example.cheapestoilfinder.station.api.ApiCallback

interface DestinationAutocompleteRepository {
    fun search(query: String, callback: ApiCallback<List<DestinationSearchSuggestion>>)
}
