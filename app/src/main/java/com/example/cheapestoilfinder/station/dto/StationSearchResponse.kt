package com.example.cheapestoilfinder.station.dto

import com.example.cheapestoilfinder.station.api.SearchMode

data class StationSearchResponse(
    var searchMode: SearchMode? = null,
    var coordinateSystem: String? = null,
    var radiusKm: Double = 0.0,
    var resultCount: Int = 0,
    var referenceLabel: String? = null,
    var stations: MutableList<StationSearchItem> = mutableListOf(),
    var route: RouteSummaryResponse? = null
)
