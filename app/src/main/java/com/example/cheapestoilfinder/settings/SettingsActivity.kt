package com.example.cheapestoilfinder.settings

import android.app.Activity
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.Toast
import com.example.cheapestoilfinder.R

class SettingsActivity : Activity() {
    private lateinit var preferenceManager: UserPreferenceManager
    private lateinit var fuelTypeSpinner: Spinner
    private lateinit var fuelEfficiencyEditText: EditText
    private lateinit var refuelAmountEditText: EditText
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        preferenceManager = UserPreferenceManager.create(this)
        bindViews()
        setupFuelTypeSpinner()
        populateSettings()

        findViewById<ImageButton>(R.id.button_back).setOnClickListener { finish() }
        saveButton.setOnClickListener { saveSettings() }
    }

    private fun bindViews() {
        fuelTypeSpinner = findViewById(R.id.spinner_fuel_type)
        fuelEfficiencyEditText = findViewById(R.id.edit_fuel_efficiency)
        refuelAmountEditText = findViewById(R.id.edit_refuel_amount)
        saveButton = findViewById(R.id.button_save_settings)
    }

    private fun setupFuelTypeSpinner() {
        val labels = UserFuelType.spinnerLabels(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        fuelTypeSpinner.adapter = adapter
    }

    private fun populateSettings() {
        val settings = preferenceManager.loadSettings()
        fuelTypeSpinner.setSelection(UserFuelType.spinnerIndexFor(settings.fuelType))
        fuelEfficiencyEditText.setText(formatDouble(settings.fuelEfficiencyKmPerLiter))
        refuelAmountEditText.setText(formatDouble(settings.refuelAmountLiter))
    }

    private fun saveSettings() {
        clearErrors()

        val selectedFuelType = UserFuelType.fromSpinnerIndex(fuelTypeSpinner.selectedItemPosition)
        val fuelEfficiency = fuelEfficiencyEditText.text?.toString()?.trim()?.toDoubleOrNull()
        val refuelAmount = refuelAmountEditText.text?.toString()?.trim()?.toDoubleOrNull()

        var isValid = true
        if (fuelTypeSpinner.selectedItemPosition !in 0 until UserFuelType.spinnerLabels(this).size) {
            Toast.makeText(this, R.string.settings_validation_fuel_type_required, Toast.LENGTH_SHORT).show()
            isValid = false
        }
        if (fuelEfficiency == null || fuelEfficiency <= 0.0) {
            fuelEfficiencyEditText.error = getString(R.string.settings_validation_efficiency_positive)
            isValid = false
        }
        if (refuelAmount == null || refuelAmount <= 0.0) {
            refuelAmountEditText.error = getString(R.string.settings_validation_refuel_positive)
            isValid = false
        }
        if (!isValid) {
            return
        }

        preferenceManager.saveSettings(
            fuelType = selectedFuelType,
            fuelEfficiencyKmPerLiter = fuelEfficiency ?: UserPreferenceManager.DEFAULT_FUEL_EFFICIENCY_KM_PER_LITER,
            refuelAmountLiter = refuelAmount ?: UserPreferenceManager.DEFAULT_REFUEL_AMOUNT_LITER
        )
        Toast.makeText(this, R.string.settings_saved_message, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun clearErrors() {
        fuelEfficiencyEditText.error = null
        refuelAmountEditText.error = null
    }

    private fun formatDouble(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            value.toString()
        }
    }
}
