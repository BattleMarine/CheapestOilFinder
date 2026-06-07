package com.example.cheapestoilfinder.station.dto

import com.example.cheapestoilfinder.station.api.DistanceBasis
import com.example.cheapestoilfinder.station.api.FuelType

data class StationSearchItem(
    var stationId: String? = null,
    var stationName: String? = null,
    var brandName: String? = null,
    var address: String? = null,
    var phone: String? = null,
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var coordinateSystem: String? = null,
    var distanceMeters: Int = 0,
    var distanceBasis: DistanceBasis? = null,
    var fuelPrices: FuelPriceSummary? = null,
    var cheapestFuelType: FuelType? = null,
    var cheapestFuelPriceWon: Int? = null,
    var estimatedTravelFuelCostWon: Int? = null,
    var estimatedTotalCostWon: Int? = null,
    var routeExtraDistanceMeters: Int? = null,
    var notes: List<String>? = null,
    var updatedAt: String? = null
)
