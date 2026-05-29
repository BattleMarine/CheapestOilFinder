package com.example.cheapestoilfinder.recommendation

import com.example.cheapestoilfinder.map.model.GasStation
import com.example.cheapestoilfinder.map.model.RecommendationResult
import com.example.cheapestoilfinder.map.model.RouteInfo

class RecommendationCalculator {
    fun rankStations(
        stations: List<GasStation>,
        routeInfo: RouteInfo,
        fuelEfficiencyKmPerLiter: Double,
        expectedFuelLiters: Double
    ): List<RecommendationResult> {
        return emptyList()
    }
}
