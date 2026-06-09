package com.example.cheapestoilfinder.station

import androidx.annotation.DrawableRes
import com.example.cheapestoilfinder.R
import java.util.Locale

enum class BrandLogoKey {
    GS,
    HD,
    SOIL,
    SK,
    ALTTUL,
    UNKNOWN
}

object BrandLogoResolver {
    fun resolve(brand: String): BrandLogoKey {
        val normalized = brand.trim()
        if (normalized.isBlank()) {
            return BrandLogoKey.UNKNOWN
        }

        val compact = normalized
            .replace(" ", "")
            .replace("-", "")
            .replace("_", "")
        val brandCode = compact.uppercase(Locale.ROOT)

        return when {
            brandCode in ALTTUL_BRAND_CODES ||
            compact.contains("\uC54C\uB730", ignoreCase = true) ||
                compact.contains("ALTTEUL", ignoreCase = true) ||
                compact.contains("ALTTUL", ignoreCase = true) -> BrandLogoKey.ALTTUL
            brandCode in SOIL_BRAND_CODES ||
            normalized.contains("S-OIL", ignoreCase = true) ||
                compact.contains("SOIL", ignoreCase = true) ||
                compact.contains("\uC5D0\uC2A4\uC624\uC77C", ignoreCase = true) -> BrandLogoKey.SOIL
            brandCode in HD_BRAND_CODES ||
            compact.contains("HD", ignoreCase = true) ||
                compact.contains("\uD604\uB300", ignoreCase = true) ||
                compact.contains("HDO", ignoreCase = true) ||
                compact.contains("\uC624\uC77C\uBC45\uD06C", ignoreCase = true) -> BrandLogoKey.HD
            brandCode in GS_BRAND_CODES ||
            compact.contains("GS", ignoreCase = true) -> BrandLogoKey.GS
            brandCode in SK_BRAND_CODES ||
            compact.contains("SK", ignoreCase = true) -> BrandLogoKey.SK
            else -> BrandLogoKey.UNKNOWN
        }
    }

    @DrawableRes
    fun fullLogoResId(brand: String): Int {
        return when (resolve(brand)) {
            BrandLogoKey.GS -> R.drawable.brand_gs_full
            BrandLogoKey.HD -> R.drawable.brand_hd_full
            BrandLogoKey.SOIL -> R.drawable.brand_soil_full
            BrandLogoKey.SK -> R.drawable.brand_sk_full
            BrandLogoKey.ALTTUL -> R.drawable.brand_alttul_full
            BrandLogoKey.UNKNOWN -> R.drawable.ic_marker_station
        }
    }

    @DrawableRes
    fun shortLogoResId(brand: String): Int {
        return when (resolve(brand)) {
            BrandLogoKey.GS -> R.drawable.brand_gs_short
            BrandLogoKey.HD -> R.drawable.brand_hd_short
            BrandLogoKey.SOIL -> R.drawable.brand_soil_short
            BrandLogoKey.SK -> R.drawable.brand_sk_short
            BrandLogoKey.ALTTUL -> R.drawable.brand_alttul_short
            BrandLogoKey.UNKNOWN -> R.drawable.ic_marker_station
        }
    }

    private val SK_BRAND_CODES = setOf("SKE")
    private val GS_BRAND_CODES = setOf("GSC")
    private val HD_BRAND_CODES = setOf("HDO")
    private val SOIL_BRAND_CODES = setOf("SOL")
    private val ALTTUL_BRAND_CODES = setOf("RTO", "RTX", "NHO")
}
