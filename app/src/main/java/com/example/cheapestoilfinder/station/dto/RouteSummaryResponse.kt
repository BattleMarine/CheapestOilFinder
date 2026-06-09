package com.example.cheapestoilfinder.station.dto

data class RouteSummaryResponse(
    var routePolyline: String? = null,
    var distanceMeters: Int = 0,
    var durationSeconds: Int = 0,
    var tollFeeWon: Int = 0,
    var fuelPriceWon: Int = 0,
    var routeOption: String? = null,
    var currentDateTime: String? = null
)
