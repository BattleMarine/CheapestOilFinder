package com.example.cheapestoilfinder.settings

import kotlin.math.roundToInt

object CostCalculator {
    fun calculateMoveCost(
        distanceKm: Double,
        fuelEfficiencyKmPerLiter: Double,
        fuelPricePerLiter: Int
    ): Int? {
        if (distanceKm <= 0.0 || fuelEfficiencyKmPerLiter <= 0.0 || fuelPricePerLiter <= 0) {
            return null
        }

        val litersNeededForRoundTrip = (distanceKm / fuelEfficiencyKmPerLiter) * 2.0
        return (litersNeededForRoundTrip * fuelPricePerLiter).roundToInt()
    }

    fun calculateMoveCost(
        distanceMeters: Int?,
        fuelEfficiencyKmPerLiter: Double,
        fuelPricePerLiter: Int
    ): Int? {
        if (distanceMeters == null || distanceMeters <= 0) {
            return null
        }

        return calculateMoveCost(distanceMeters / 1000.0, fuelEfficiencyKmPerLiter, fuelPricePerLiter)
    }

    fun calculateRefuelCost(
        refuelAmountLiter: Double,
        fuelPricePerLiter: Int
    ): Int? {
        if (refuelAmountLiter <= 0.0 || fuelPricePerLiter <= 0) {
            return null
        }

        return (refuelAmountLiter * fuelPricePerLiter).roundToInt()
    }

    fun calculateTotalExpectedCost(
        moveCostWon: Int?,
        refuelCostWon: Int?,
        discountAmountWon: Int = 0
    ): Int? {
        if (moveCostWon == null || refuelCostWon == null) {
            return null
        }

        return (moveCostWon + refuelCostWon - discountAmountWon).coerceAtLeast(0)
    }
}
