package com.example.cheapestoilfinder.map.model

import com.example.cheapestoilfinder.station.dto.FuelPriceSummary

data class GasStation(
    val id: String,
    val name: String,
    val brand: String,
    val fuelType: String,
    val pricePerLiter: Int,
    val distanceMeters: Int,
    val locationPoint: LocationPoint,
    val fuelPrices: FuelPriceSummary? = null,
    val phone: String = ""
)
