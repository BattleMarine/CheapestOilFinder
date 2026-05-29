package com.example.cheapestoilfinder.station.dto

data class StationDetailResponse(
    var coordinateSystem: String? = null,
    var station: StationSearchItem? = null
)
