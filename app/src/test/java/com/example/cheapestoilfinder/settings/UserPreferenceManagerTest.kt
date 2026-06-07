package com.example.cheapestoilfinder.settings

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class UserPreferenceManagerTest {
    @Test
    fun saveAndLoadSettings_roundTrip() {
        val preferences = InMemorySharedPreferences()
        val manager = UserPreferenceManager(preferences)

        manager.saveSettings(
            fuelType = UserFuelType.GAS_HIGH,
            fuelEfficiencyKmPerLiter = 14.2,
            refuelAmountLiter = 38.5
        )

        val loaded = manager.loadSettings()

        assertEquals(UserFuelType.GAS_HIGH, loaded.fuelType)
        assertEquals(14.2, loaded.fuelEfficiencyKmPerLiter, 0.0001)
        assertEquals(38.5, loaded.refuelAmountLiter, 0.0001)
    }

    @Test
    fun loadSettings_usesDefaults_whenValuesMissing() {
        val preferences = InMemorySharedPreferences()
        val manager = UserPreferenceManager(preferences)

        val loaded = manager.loadSettings()

        assertEquals(UserFuelType.GAS_LOW, loaded.fuelType)
        assertEquals(12.0, loaded.fuelEfficiencyKmPerLiter, 0.0001)
        assertEquals(30.0, loaded.refuelAmountLiter, 0.0001)
    }
}

private class InMemorySharedPreferences : SharedPreferences {
    private val values = linkedMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = (values[key] as? MutableSet<String>) ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = (values[key] as? Int) ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = (values[key] as? Long) ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = (values[key] as? Float) ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = (values[key] as? Boolean) ?: defValue
    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = InMemoryEditor(values)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}

private class InMemoryEditor(
    private val values: MutableMap<String, Any?>
) : SharedPreferences.Editor {
    override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { if (key != null) values[key] = value }
    override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply { if (key != null) this.values[key] = values }
    override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply { if (key != null) values[key] = value }
    override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { if (key != null) values[key] = value }
    override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply { if (key != null) values[key] = value }
    override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { if (key != null) values[key] = value }
    override fun remove(key: String?): SharedPreferences.Editor = apply { if (key != null) values.remove(key) }
    override fun clear(): SharedPreferences.Editor = apply { values.clear() }
    override fun commit(): Boolean = true
    override fun apply() = Unit
}
