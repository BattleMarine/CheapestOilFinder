package com.example.cheapestoilfinder.destination

import com.example.cheapestoilfinder.station.api.ApiCallback

interface DestinationSearchRepository {
    fun search(query: String, callback: ApiCallback<List<DestinationSearchSuggestion>>)
}
