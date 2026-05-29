package com.example.cheapestoilfinder.map.model

data class GasStation(
    val id: String,
    val name: String,
    val brand: String,
    val fuelType: String,
    val pricePerLiter: Int,
    val locationPoint: LocationPoint
)
