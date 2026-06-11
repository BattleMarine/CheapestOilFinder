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
        private val costSummaryView: TextView = itemView.findViewById(R.id.text_station_cost_summary)
        private val distanceValueView: TextView = itemView.findViewById(R.id.text_distance_value)

        fun bind(item: GasStation) {
            brandLogoView.setImageResource(BrandLogoResolver.fullLogoResId(item.brand))
            brandLogoView.contentDescription = itemView.context.getString(
                R.string.station_brand_logo_content_description
            )
            nameView.text = shortenStationName(item.name)
            priceView.text = buildSelectedFuelPriceText(item)
            costSummaryView.text = buildCostSummaryText(item)
            distanceValueView.text = formatDistance(item.distanceMeters)
            itemView.setOnClickListener {
                onStationClick(item)
            }
        }

        private fun buildSelectedFuelPriceText(item: GasStation): String {
            val summary = item.costSummary
            val label = summary?.selectedFuelType?.displayLabel(itemView.context)
                ?: resolveFallbackFuelLabel(item)
            val price = summary?.selectedFuelPricePerLiter?.takeIf { it > 0 }

            return if (price != null) {
                itemView.context.getString(
                    R.string.station_list_fuel_price_format,
                    label,
                    formatWon(price)
                )
            } else {
                "$label ${itemView.context.getString(R.string.station_info_cost_unavailable)}"
            }
        }

        private fun buildCostSummaryText(item: GasStation): String {
            val summary = item.costSummary ?: return itemView.context.getString(R.string.station_list_cost_summary_unavailable)
            val moveCostText = formatCostText(summary.moveCostWon)
            val totalCostText = formatCostText(summary.totalExpectedCostWon)

            if (moveCostText == itemView.context.getString(R.string.station_info_cost_unavailable) &&
                totalCostText == itemView.context.getString(R.string.station_info_cost_unavailable)
            ) {
                return itemView.context.getString(R.string.station_list_cost_summary_unavailable)
            }

            return itemView.context.getString(
                R.string.station_list_cost_summary_format,
                moveCostText,
                totalCostText
            )
        }

        private fun formatCostText(cost: Int?): String {
            return cost?.takeIf { it >= 0 }?.let { "약 ${formatWon(it)}" }
                ?: itemView.context.getString(R.string.station_info_cost_unavailable)
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

        private fun resolveFallbackFuelLabel(item: GasStation): String {
            return when (item.fuelType) {
                "PREMIUM_GASOLINE" -> itemView.context.getString(R.string.fuel_type_gas_high)
                "REGULAR_GASOLINE" -> itemView.context.getString(R.string.fuel_type_gas_low)
                "DIESEL" -> itemView.context.getString(R.string.fuel_type_diesel)
                "LPG" -> itemView.context.getString(R.string.fuel_type_lpg)
                else -> item.fuelType.ifBlank { itemView.context.getString(R.string.fuel_type_gas_low) }
            }
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
