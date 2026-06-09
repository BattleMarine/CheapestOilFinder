package com.example.cheapestoilfinder.station.dto

import com.example.cheapestoilfinder.station.api.FuelType
import com.example.cheapestoilfinder.station.api.StationSearchSortOrder

data class RouteStationSearchRequest(
    var originLatitude: Double = 0.0,
    var originLongitude: Double = 0.0,
    var destinationLatitude: Double = 0.0,
    var destinationLongitude: Double = 0.0,
    var routePolyline: String? = null,
    var radiusKm: Double = 5.0,
    var fuelAmountLiters: Double = 30.0,
    var fuelEfficiencyKmPerLiter: Double = 10.0,
    var fuelTypes: List<FuelType> = defaultFuelTypes(),
    var sortOrder: StationSearchSortOrder = StationSearchSortOrder.DISTANCE_ASC,
    var routeResultMode: RouteResultMode = RouteResultMode.ROUTE_ONLY,
    var originLabel: String? = null,
    var destinationLabel: String? = null
) {
    constructor(
        originLatitude: Double,
        originLongitude: Double,
        destinationLatitude: Double,
        destinationLongitude: Double,
        routePolyline: String?,
        radiusKm: Double,
        fuelAmountLiters: Double,
        fuelEfficiencyKmPerLiter: Double,
        fuelTypes: List<FuelType>?,
        routeResultMode: RouteResultMode,
        originLabel: String?,
        destinationLabel: String?
    ) : this(
        originLatitude,
        originLongitude,
        destinationLatitude,
        destinationLongitude,
        routePolyline,
        radiusKm,
        fuelAmountLiters,
        fuelEfficiencyKmPerLiter,
        fuelTypes ?: defaultFuelTypes(),
        StationSearchSortOrder.DISTANCE_ASC,
        routeResultMode,
        originLabel,
        destinationLabel
    )

    companion object {
        private fun defaultFuelTypes(): List<FuelType> = listOf(
            FuelType.REGULAR_GASOLINE,
            FuelType.PREMIUM_GASOLINE,
            FuelType.DIESEL
        )
    }
}

enum class RouteResultMode {
    ROUTE_ONLY,
    ROUTE_WITH_STATIONS
}
