package com.example.cheapestoilfinder.station;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.cheapestoilfinder.station.api.ApiCallback;
import com.example.cheapestoilfinder.station.api.DistanceBasis;
import com.example.cheapestoilfinder.station.api.FuelType;
import com.example.cheapestoilfinder.station.api.SearchMode;
import com.example.cheapestoilfinder.station.dto.FuelPriceSummary;
import com.example.cheapestoilfinder.station.dto.NearbyStationSearchRequest;
import com.example.cheapestoilfinder.station.dto.RouteStationSearchRequest;
import com.example.cheapestoilfinder.station.dto.StationDetailResponse;
import com.example.cheapestoilfinder.station.dto.StationSearchItem;
import com.example.cheapestoilfinder.station.dto.StationSearchResponse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public class BackendStationRepositoryTest {
    private MockWebServer server;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    public void searchNearbyStations_postsRequestAndParsesResponse() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{"
                        + "\"searchMode\":\"NEARBY\","
                        + "\"coordinateSystem\":\"WGS84\","
                        + "\"radiusKm\":5.0,"
                        + "\"resultCount\":2,"
                        + "\"referenceLabel\":\"현재 위치\","
                        + "\"stations\":["
                        + "{"
                        + "\"stationId\":\"S-001\","
                        + "\"stationName\":\"테스트 주유소 1\","
                        + "\"brandName\":\"SK\","
                        + "\"address\":\"서울시 중구\","
                        + "\"latitude\":37.5665,"
                        + "\"longitude\":126.9780,"
                        + "\"coordinateSystem\":\"WGS84\","
                        + "\"distanceMeters\":820,"
                        + "\"distanceBasis\":\"REFERENCE_POINT\","
                        + "\"fuelPrices\":{"
                        + "\"regularGasolineWon\":1678,"
                        + "\"premiumGasolineWon\":1789,"
                        + "\"dieselWon\":1544,"
                        + "\"lpgWon\":null,"
                        + "\"updatedAt\":\"2026-05-28T10:00:00\""
                        + "},"
                        + "\"cheapestFuelType\":\"DIESEL\","
                        + "\"cheapestFuelPriceWon\":1544,"
                        + "\"estimatedTravelFuelCostWon\":15440,"
                        + "\"estimatedTotalCostWon\":16960,"
                        + "\"routeExtraDistanceMeters\":null,"
                        + "\"updatedAt\":\"2026-05-28T10:00:00\""
                        + "},"
                        + "{"
                        + "\"stationId\":\"S-002\","
                        + "\"stationName\":\"테스트 주유소 2\","
                        + "\"brandName\":\"GS\","
                        + "\"address\":\"서울시 종로구\","
                        + "\"latitude\":37.57,"
                        + "\"longitude\":126.99,"
                        + "\"coordinateSystem\":\"WGS84\","
                        + "\"distanceMeters\":1240,"
                        + "\"distanceBasis\":\"REFERENCE_POINT\","
                        + "\"fuelPrices\":{"
                        + "\"regularGasolineWon\":1688,"
                        + "\"premiumGasolineWon\":1799,"
                        + "\"dieselWon\":1559,"
                        + "\"lpgWon\":null,"
                        + "\"updatedAt\":\"2026-05-28T10:00:00\""
                        + "},"
                        + "\"cheapestFuelType\":\"DIESEL\","
                        + "\"cheapestFuelPriceWon\":1559,"
                        + "\"estimatedTravelFuelCostWon\":15590,"
                        + "\"estimatedTotalCostWon\":17100,"
                        + "\"routeExtraDistanceMeters\":null,"
                        + "\"updatedAt\":\"2026-05-28T10:00:00\""
                        + "}"
                        + "]"
                        + "}"));

        BackendStationRepository repository = BackendStationRepository.create(server.url("/").toString());
        NearbyStationSearchRequest request = new NearbyStationSearchRequest(
                37.5665,
                126.9780,
                5.0,
                30.0,
                10.0,
                Arrays.asList(FuelType.REGULAR_GASOLINE, FuelType.PREMIUM_GASOLINE, FuelType.DIESEL),
                "현재 위치"
        );

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<StationSearchResponse> success = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        repository.searchNearbyStations(request, new ApiCallback<StationSearchResponse>() {
            @Override
            public void onSuccess(StationSearchResponse result) {
                success.set(result);
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                failure.set(error);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(failure.get());

        StationSearchResponse response = success.get();
        assertNotNull(response);
        assertEquals(SearchMode.NEARBY, response.searchMode);
        assertEquals(2, response.resultCount);
        assertEquals(2, response.stations.size());
        assertEquals("S-001", response.stations.get(0).stationId);
        assertEquals(DistanceBasis.REFERENCE_POINT, response.stations.get(0).distanceBasis);
        assertEquals(Integer.valueOf(1544), response.stations.get(0).cheapestFuelPriceWon);

        RecordedRequest recordedRequest = server.takeRequest();
        assertEquals("GET", recordedRequest.getMethod());
        assertTrue(recordedRequest.getPath().startsWith("/api/stations/nearby?"));
        String path = recordedRequest.getPath();
        assertTrue(path.contains("latitude=37.5665"));
        assertTrue(path.contains("longitude=126.978"));
        assertTrue(path.contains("radiusKm=5.0"));
        assertTrue(path.contains("fuelAmountLiters=30.0"));
        assertTrue(path.contains("fuelEfficiencyKmPerLiter=10.0"));
        assertTrue(path.contains("fuelTypes=REGULAR_GASOLINE"));
        assertTrue(path.contains("fuelTypes=PREMIUM_GASOLINE"));
        assertTrue(path.contains("fuelTypes=DIESEL"));
        assertTrue(path.contains("sortOrder=DISTANCE_ASC"));
    }

    @Test
    public void searchRouteStations_postsRouteContract() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{"
                        + "\"searchMode\":\"ROUTE\","
                        + "\"coordinateSystem\":\"WGS84\","
                        + "\"radiusKm\":5.0,"
                        + "\"resultCount\":1,"
                        + "\"referenceLabel\":\"서울역 -> 강남역\","
                        + "\"stations\":[]"
                        + "}"));

        BackendStationRepository repository = BackendStationRepository.create(server.url("/").toString());
        RouteStationSearchRequest request = new RouteStationSearchRequest(
                37.5563,
                126.9723,
                37.4979,
                127.0276,
                "37.5563,126.9723;37.4979,127.0276",
                5.0,
                30.0,
                10.0,
                Arrays.asList(FuelType.REGULAR_GASOLINE, FuelType.DIESEL),
                "서울역",
                "강남역"
        );

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<StationSearchResponse> success = new AtomicReference<>();

        repository.searchRouteStations(request, new ApiCallback<StationSearchResponse>() {
            @Override
            public void onSuccess(StationSearchResponse result) {
                success.set(result);
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(success.get());
        assertEquals(SearchMode.ROUTE, success.get().searchMode);

        RecordedRequest recordedRequest = server.takeRequest();
        assertEquals("POST", recordedRequest.getMethod());
        assertEquals("/api/stations/route", recordedRequest.getPath());
        String body = recordedRequest.getBody().readUtf8();
        assertTrue(body.contains("\"originLatitude\":37.5563"));
        assertTrue(body.contains("\"destinationLatitude\":37.4979"));
        assertTrue(body.contains("\"routePolyline\":\"37.5563,126.9723;37.4979,127.0276\""));
    }

    @Test
    public void getStationDetail_getsStationPath() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{"
                        + "\"coordinateSystem\":\"WGS84\","
                        + "\"station\":{"
                        + "\"stationId\":\"S-100\","
                        + "\"stationName\":\"디테일 주유소\","
                        + "\"brandName\":\"HD\","
                        + "\"address\":\"부산시\","
                        + "\"latitude\":35.1796,"
                        + "\"longitude\":129.0756,"
                        + "\"coordinateSystem\":\"WGS84\","
                        + "\"distanceMeters\":0,"
                        + "\"distanceBasis\":\"REFERENCE_POINT\","
                        + "\"fuelPrices\":{"
                        + "\"regularGasolineWon\":1660,"
                        + "\"premiumGasolineWon\":1770,"
                        + "\"dieselWon\":1530,"
                        + "\"lpgWon\":null,"
                        + "\"updatedAt\":\"2026-05-28T10:00:00\""
                        + "},"
                        + "\"cheapestFuelType\":\"DIESEL\","
                        + "\"cheapestFuelPriceWon\":1530,"
                        + "\"estimatedTravelFuelCostWon\":15300,"
                        + "\"estimatedTotalCostWon\":16800,"
                        + "\"routeExtraDistanceMeters\":null,"
                        + "\"updatedAt\":\"2026-05-28T10:00:00\""
                        + "}"
                        + "}"));

        BackendStationRepository repository = BackendStationRepository.create(server.url("/").toString());
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<StationDetailResponse> success = new AtomicReference<>();

        repository.getStationDetail("S-100", new ApiCallback<StationDetailResponse>() {
            @Override
            public void onSuccess(StationDetailResponse result) {
                success.set(result);
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(success.get());
        assertEquals("WGS84", success.get().coordinateSystem);
        assertEquals("S-100", success.get().station.stationId);

        RecordedRequest recordedRequest = server.takeRequest();
        assertEquals("GET", recordedRequest.getMethod());
        assertEquals("/api/stations/S-100", recordedRequest.getPath());
    }
}
