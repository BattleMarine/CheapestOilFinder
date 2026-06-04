package com.example.cheapestoilfinder.entry

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.cheapestoilfinder.R
import com.example.cheapestoilfinder.map.model.GasStation
import com.example.cheapestoilfinder.station.api.FuelType
import java.util.Locale

class StationListAdapter :
    ListAdapter<GasStation, StationListAdapter.StationViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_station_list, parent, false)
        return StationViewHolder(view)
    }

    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class StationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val brandView: TextView = itemView.findViewById(R.id.text_brand)
        private val nameView: TextView = itemView.findViewById(R.id.text_station_name)
        private val priceView: TextView = itemView.findViewById(R.id.text_station_price)
        private val distanceValueView: TextView = itemView.findViewById(R.id.text_distance_value)

        fun bind(item: GasStation) {
            brandView.text = normalizeBrandLabel(item.brand)
            nameView.text = shortenStationName(item.name)
            priceView.text = buildPriceText(item)
            distanceValueView.text = formatDistance(item.distanceMeters)
        }

        private fun buildPriceText(item: GasStation): String {
            val prices = item.fuelPrices
            val regularPrice = prices?.regularGasolineWon?.takeIf { it > 0 }
            val premiumPrice = prices?.premiumGasolineWon?.takeIf { it > 0 }
            val dieselPrice = prices?.dieselWon?.takeIf { it > 0 }
            val fallbackPrice = item.pricePerLiter.takeIf { it > 0 }

            return when {
                regularPrice != null && premiumPrice != null ->
                    "휘발유(고급) ${formatWon(regularPrice)}(${formatWon(premiumPrice)})"
                regularPrice != null ->
                    "휘발유 ${formatWon(regularPrice)}"
                premiumPrice != null ->
                    "고급휘발유 ${formatWon(premiumPrice)}"
                dieselPrice != null ->
                    "디젤 ${formatWon(dieselPrice)}"
                fallbackPrice != null && item.fuelType.isNotBlank() ->
                    "${item.fuelType.toFuelLabel()} ${formatWon(fallbackPrice)}"
                fallbackPrice != null ->
                    formatWon(fallbackPrice)
                else ->
                    "가격 정보 없음"
            }
        }

        private fun String.toFuelLabel(): String {
            return when (this) {
                FuelType.REGULAR_GASOLINE.name -> "휘발유"
                FuelType.PREMIUM_GASOLINE.name -> "고급휘발유"
                FuelType.DIESEL.name -> "디젤"
                FuelType.LPG.name -> "LPG"
                else -> this
            }
        }

        private fun formatWon(value: Int): String {
            return String.format(Locale.KOREA, "%,d원", value)
        }

        private fun formatDistance(distanceMeters: Int): String {
            return if (distanceMeters < 1000) {
                "${distanceMeters}m"
            } else {
                String.format(Locale.KOREA, "%.2fkm", distanceMeters / 1000.0)
            }
        }

        private fun normalizeBrandLabel(brand: String): String {
            val normalized = brand.trim()
            return when {
                normalized.isBlank() -> "브랜드"
                normalized.contains("S-OIL", ignoreCase = true) -> "S-OIL"
                normalized.contains("SK", ignoreCase = true) -> "SK"
                normalized.contains("GS", ignoreCase = true) -> "GS"
                normalized.contains("HD", ignoreCase = true) ||
                    normalized.contains("현대", ignoreCase = true) -> "HD"
                normalized.contains("알뜰", ignoreCase = true) -> "알뜰"
                else -> normalized.take(6)
            }
        }

        private fun shortenStationName(name: String): String {
            return name
                .replace("직영", "")
                .replace("셀프", "")
                .replace("  ", " ")
                .trim()
                .ifBlank { name }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<GasStation>() {
        override fun areItemsTheSame(oldItem: GasStation, newItem: GasStation): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: GasStation, newItem: GasStation): Boolean {
            return oldItem == newItem
        }
    }
}
