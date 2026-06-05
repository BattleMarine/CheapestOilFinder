package com.example.cheapestoilfinder.station

import androidx.annotation.DrawableRes
import com.example.cheapestoilfinder.R

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

        return when {
            normalized.contains("GS", ignoreCase = true) -> BrandLogoKey.GS
            normalized.contains("SK", ignoreCase = true) -> BrandLogoKey.SK
            normalized.contains("S-OIL", ignoreCase = true) ||
                normalized.contains("SOIL", ignoreCase = true) ||
                normalized.contains("\uC5D0\uC2A4\uC624\uC77C", ignoreCase = true) -> BrandLogoKey.SOIL
            normalized.contains("HD", ignoreCase = true) ||
                normalized.contains("\uD604\uB300", ignoreCase = true) ||
                normalized.contains("HDO", ignoreCase = true) ||
                normalized.contains("\uC624\uC77C\uBC45\uD06C", ignoreCase = true) -> BrandLogoKey.HD
            normalized.contains("\uC54C\uB73B", ignoreCase = true) -> BrandLogoKey.ALTTUL
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
            BrandLogoKey.UNKNOWN -> R.drawable.brand_sk_full
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
            BrandLogoKey.UNKNOWN -> R.drawable.brand_sk_short
        }
    }
}
