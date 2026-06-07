package com.example.cheapestoilfinder.settings

import android.content.Context
import com.example.cheapestoilfinder.R
import com.example.cheapestoilfinder.station.api.FuelType

enum class UserFuelType(
    val backendFuelType: FuelType,
    val labelResId: Int
) {
    GAS_HIGH(FuelType.PREMIUM_GASOLINE, R.string.fuel_type_gas_high),
    GAS_LOW(FuelType.REGULAR_GASOLINE, R.string.fuel_type_gas_low),
    DIESEL(FuelType.DIESEL, R.string.fuel_type_diesel),
    LPG(FuelType.LPG, R.string.fuel_type_lpg);

    fun displayLabel(context: Context): String {
        return context.getString(labelResId)
    }

    companion object {
        const val DEFAULT_STORAGE_VALUE = "GAS_LOW"

        fun fromStoredValue(value: String?): UserFuelType {
            return when (value?.uppercase()) {
                "GAS_HIGH" -> GAS_HIGH
                "GAS_LOW" -> GAS_LOW
                "DIESEL", "DISL" -> DIESEL
                "LPG" -> LPG
                else -> GAS_LOW
            }
        }

        fun fromSpinnerIndex(index: Int): UserFuelType {
            return values().getOrElse(index) { GAS_LOW }
        }

        fun spinnerLabels(context: Context): List<String> {
            return values().map { it.displayLabel(context) }
        }
    }
}
