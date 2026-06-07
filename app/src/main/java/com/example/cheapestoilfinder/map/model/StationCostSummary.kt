package com.example.cheapestoilfinder.map.model

import com.example.cheapestoilfinder.settings.UserFuelType

data class StationCostSummary(
    val selectedFuelType: UserFuelType,
    val selectedFuelPricePerLiter: Int?,
    val distanceMeters: Int?,
    val distanceKm: Double?,
    val moveCostWon: Int?,
    val refuelCostWon: Int?,
    val totalExpectedCostWon: Int?,
    val unavailableReason: String? = null
)
