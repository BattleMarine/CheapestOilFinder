package com.example.cheapestoilfinder.entry

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.cheapestoilfinder.R
import com.example.cheapestoilfinder.map.model.GasStation
import com.example.cheapestoilfinder.station.BrandLogoResolver
import com.example.cheapestoilfinder.station.api.FuelType
import java.util.Locale

class StationListAdapter(
    private val onStationClick: (GasStation) -> Unit
) : ListAdapter<GasStation, StationListAdapter.StationViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_station_list, parent, false)
        return StationViewHolder(view)
    }

    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class StationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val brandLogoView: ImageView = itemView.findViewById(R.id.image_brand_logo)
        private val nameView: TextView = itemView.findViewById(R.id.text_station_name)
        private val priceView: TextView = itemView.findViewById(R.id.text_station_price)
        private val distanceValueView: TextView = itemView.findViewById(R.id.text_distance_value)

        fun bind(item: GasStation) {
            brandLogoView.setImageResource(BrandLogoResolver.fullLogoResId(item.brand))
            brandLogoView.contentDescription = itemView.context.getString(
                R.string.station_brand_logo_content_description
            )
            nameView.text = shortenStationName(item.name)
            priceView.text = buildPriceText(item)
            distanceValueView.text = formatDistance(item.distanceMeters)
            itemView.setOnClickListener {
                onStationClick(item)
            }
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

        private fun shortenStationName(name: String): String {
            return name
                .replace("직영", "")
                .replace("주유소", "")
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
