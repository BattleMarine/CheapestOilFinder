package com.example.cheapestoilfinder.station.dto

data class FuelPriceSummary(
    var regularGasolineWon: Int? = null,
    var premiumGasolineWon: Int? = null,
    var dieselWon: Int? = null,
    var lpgWon: Int? = null,
    var updatedAt: String? = null
)
