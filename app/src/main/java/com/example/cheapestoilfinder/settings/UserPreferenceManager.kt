package com.example.cheapestoilfinder.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

data class UserSettings(
    val fuelType: UserFuelType = UserFuelType.GAS_LOW,
    val fuelEfficiencyKmPerLiter: Double = 12.0,
    val refuelAmountLiter: Double = 30.0
)

class UserPreferenceManager internal constructor(
    private val preferences: SharedPreferences
) {
    fun loadSettings(): UserSettings {
        return UserSettings(
            fuelType = UserFuelType.fromStoredValue(preferences.getString(KEY_FUEL_TYPE, null)),
            fuelEfficiencyKmPerLiter = preferences.getString(KEY_FUEL_EFFICIENCY, null)
                ?.toDoubleOrNull()
                ?.takeIf { it > 0.0 }
                ?: DEFAULT_FUEL_EFFICIENCY_KM_PER_LITER,
            refuelAmountLiter = preferences.getString(KEY_REFUEL_AMOUNT, null)
                ?.toDoubleOrNull()
                ?.takeIf { it > 0.0 }
                ?: DEFAULT_REFUEL_AMOUNT_LITER
        )
    }

    fun saveSettings(settings: UserSettings) {
        val committed = preferences.edit()
            .putString(KEY_FUEL_TYPE, settings.fuelType.name)
            .putString(KEY_FUEL_EFFICIENCY, settings.fuelEfficiencyKmPerLiter.toString())
            .putString(KEY_REFUEL_AMOUNT, settings.refuelAmountLiter.toString())
            .commit()
        Log.d(TAG, "saveSettings committed=$committed fuelType=${settings.fuelType} efficiency=${settings.fuelEfficiencyKmPerLiter} refuelAmount=${settings.refuelAmountLiter}")
    }

    fun saveSettings(
        fuelType: UserFuelType,
        fuelEfficiencyKmPerLiter: Double,
        refuelAmountLiter: Double
    ) {
        saveSettings(
            UserSettings(
                fuelType = fuelType,
                fuelEfficiencyKmPerLiter = fuelEfficiencyKmPerLiter,
                refuelAmountLiter = refuelAmountLiter
            )
        )
    }

    companion object {
        private const val TAG = "UserPreferenceManager"
        private const val PREF_NAME = "user_settings"
        const val KEY_FUEL_TYPE = "fuel_type"
        const val KEY_FUEL_EFFICIENCY = "fuel_efficiency_km_per_l"
        const val KEY_REFUEL_AMOUNT = "refuel_amount_liter"
        const val DEFAULT_FUEL_EFFICIENCY_KM_PER_LITER = 12.0
        const val DEFAULT_REFUEL_AMOUNT_LITER = 30.0

        fun create(context: Context): UserPreferenceManager {
            return UserPreferenceManager(
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            )
        }
    }
}
