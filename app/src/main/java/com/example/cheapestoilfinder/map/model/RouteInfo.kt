package com.example.cheapestoilfinder.map.model

data class RouteInfo(
    val origin: LocationPoint,
    val destination: LocationPoint,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val tollFeeWon: Int,
    val polyline: String
)
