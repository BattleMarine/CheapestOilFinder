package com.example.cheapestoilfinder.station.dto

import com.example.cheapestoilfinder.station.api.FuelType
import com.example.cheapestoilfinder.station.api.StationSearchSortOrder

data class NearbyStationSearchRequest(
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var radiusKm: Double = 5.0,
    var fuelAmountLiters: Double = 30.0,
    var fuelEfficiencyKmPerLiter: Double = 10.0,
    var fuelTypes: List<FuelType> = defaultFuelTypes(),
    var sortOrder: StationSearchSortOrder = StationSearchSortOrder.DISTANCE_ASC,
    var referenceLabel: String? = null
) {
    constructor(
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
        fuelAmountLiters: Double,
        fuelEfficiencyKmPerLiter: Double,
        fuelTypes: List<FuelType>?,
        referenceLabel: String?
    ) : this(
        latitude,
        longitude,
        radiusKm,
        fuelAmountLiters,
        fuelEfficiencyKmPerLiter,
        fuelTypes ?: defaultFuelTypes(),
        StationSearchSortOrder.DISTANCE_ASC,
        referenceLabel
    )

    companion object {
        private fun defaultFuelTypes(): List<FuelType> = listOf(
            FuelType.REGULAR_GASOLINE,
            FuelType.PREMIUM_GASOLINE,
            FuelType.DIESEL
        )
    }
}
