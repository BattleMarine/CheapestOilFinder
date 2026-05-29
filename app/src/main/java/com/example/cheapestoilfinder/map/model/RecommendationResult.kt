package com.example.cheapestoilfinder.map.model

data class RecommendationResult(
    val station: GasStation,
    val routeExtraDistanceMeters: Int,
    val estimatedTravelFuelCostWon: Int,
    val estimatedTotalCostWon: Int
)
