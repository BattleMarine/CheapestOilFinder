package com.example.cheapestoilfinder.station

import com.example.cheapestoilfinder.map.model.GasStation
import com.example.cheapestoilfinder.map.model.LocationPoint
import com.example.cheapestoilfinder.station.dto.StationSearchItem
import com.example.cheapestoilfinder.station.dto.StationSearchResponse

object StationDisplayMapper {
    fun toGasStations(response: StationSearchResponse?): List<GasStation> {
        val items = response?.stations ?: return emptyList()
        if (items.isEmpty()) return emptyList()
        return items.map { toGasStation(it) }
    }

    fun toGasStation(item: StationSearchItem?): GasStation {
        if (item == null) {
            return GasStation("", "", "", "", 0, 0, LocationPoint(0.0, 0.0, "", ""), null, "", null)
        }

        return GasStation(
            item.stationId.orEmpty(),
            item.stationName.orEmpty(),
            item.brandName.orEmpty(),
            item.cheapestFuelType?.name.orEmpty(),
            item.cheapestFuelPriceWon ?: 0,
            item.distanceMeters,
            LocationPoint(
                item.latitude,
                item.longitude,
                item.stationName.orEmpty(),
                item.address.orEmpty()
            ),
            item.fuelPrices,
            item.phone.orEmpty(),
            null,
            item.routeExtraDistanceMeters,
            item.detourRoute
        )
    }
}
