# 프론트엔드-백엔드 API 가이드

이 문서는 Android 프론트엔드가 CheapestOilFinder Backend를 호출할 때 사용하는 API 계약을 정리합니다.

## 공통 규칙

- 좌표는 모두 WGS84 기준의 `latitude` / `longitude`를 사용합니다.
- 프론트엔드는 오피넷, 네이버 Directions, 카카오 Local API를 직접 호출하지 않습니다.
- 백엔드 기본 주소는 빌드 시 `secrets/backend_base_url.txt`에서 읽습니다.
- 로컬 에뮬레이터 기본 주소는 `http://10.0.2.2:8080/`입니다.
- 실기기나 Cloudflare Tunnel을 쓸 때는 `secrets/backend_base_url.txt`를 바꾼 뒤 앱을 다시 빌드합니다.

## 1. 주변 주유소 조회

현재 위치 기준 반경 안의 주유소를 조회합니다.

```http
GET /api/stations/nearby
```

### Query

| 이름 | 설명 |
| --- | --- |
| `latitude` | 기준 위치 위도 |
| `longitude` | 기준 위치 경도 |
| `radiusMeters` | 조회 반경, 미터 단위 |
| `radiusKm` | 조회 반경, km 단위. `radiusMeters`가 있으면 `radiusMeters`가 우선 |
| `fuelAmountLiters` | 1회 주유량 |
| `fuelEfficiencyKmPerLiter` | 차량 연비 |
| `fuelTypes` | 조회할 유종. 여러 번 전달 가능 |
| `sortOrder` | `DISTANCE_ASC`, `CHEAPEST_FUEL_ASC`, `ESTIMATED_TOTAL_COST_ASC` |
| `referenceLabel` | 화면 표시용 기준 위치 이름 |

### 예시

```bash
curl "http://localhost:8080/api/stations/nearby?latitude=37.4979&longitude=127.0276&radiusMeters=5000&fuelTypes=REGULAR_GASOLINE&fuelTypes=DIESEL&sortOrder=DISTANCE_ASC"
```

## 2. 경로 조회

현재 위치와 목적지 좌표를 보내면 백엔드가 네이버 Directions 5를 호출해 자동차 경로를 계산합니다.

```http
POST /api/stations/route
Content-Type: application/json
```

### 요청 필드

| 이름 | 설명 |
| --- | --- |
| `originLatitude` | 출발지 위도 |
| `originLongitude` | 출발지 경도 |
| `destinationLatitude` | 목적지 위도 |
| `destinationLongitude` | 목적지 경도 |
| `routePolyline` | 선택값. 이미 경로 polyline이 있으면 전달 가능 |
| `radiusMeters` | 경로 주변 주유소 조회 반경, 미터 단위 |
| `radiusKm` | 경로 주변 주유소 조회 반경, km 단위 |
| `fuelAmountLiters` | 1회 주유량 |
| `fuelEfficiencyKmPerLiter` | 차량 연비 |
| `fuelTypes` | 조회할 유종 목록 |
| `sortOrder` | 정렬 방식 |
| `routeResultMode` | 경로 응답 모드 |
| `originLabel` | 출발지 표시 이름 |
| `destinationLabel` | 목적지 표시 이름 |

### `routeResultMode`

`routeResultMode`는 경로 API가 주유소 목록까지 같이 내려줄지 결정합니다.

| 값 | 동작 |
| --- | --- |
| `ROUTE_ONLY` | 경로만 계산해 `route`를 반환하고 `stations[]`는 빈 배열로 반환 |
| `ROUTE_WITH_STATIONS` | 경로를 계산한 뒤 경로 주변 주유소 top 5를 추천해 `route`와 `stations[]`를 함께 반환 |

값을 보내지 않으면 기존 호환을 위해 `ROUTE_WITH_STATIONS`로 처리됩니다.

목적지 확정 직후 지도에 경로선만 빠르게 그릴 때는 `ROUTE_ONLY`를 사용하고, 목적지 기반 주유소 탐색 화면에서 경로 주변 주유소까지 필요할 때는 `ROUTE_WITH_STATIONS`를 사용합니다.

`ROUTE_WITH_STATIONS`는 백엔드에서 다음 순서로 후보를 줄입니다.

1. 기본 현위치-목적지 경로를 계산합니다.
2. PostGIS로 경로 주변 주유소를 조회합니다.
3. `경로 이탈거리 기반 이동비 + 예상 주유비`로 계산한 `estimatedTotalCostWon`이 낮은 주유소 top 5를 고릅니다.
4. top 5에 대해서만 네이버 Directions 경유지 API를 호출해 현위치-주유소-목적지 경로를 계산합니다.
5. `경유 경로 거리 - 기본 경로 거리`를 `routeExtraDistanceMeters`로 기록하고, `추가 이동비 + 예상 주유비`로 다시 계산한 `estimatedTotalCostWon`이 낮은 순서로 `stations[]`를 반환합니다.

### 경로만 요청 예시

```json
{
  "originLatitude": 37.4979,
  "originLongitude": 127.0276,
  "destinationLatitude": 37.5100,
  "destinationLongitude": 127.0620,
  "routeResultMode": "ROUTE_ONLY",
  "originLabel": "현재위치",
  "destinationLabel": "목적지"
}
```

### 경로와 주변 주유소 요청 예시

```json
{
  "originLatitude": 37.4979,
  "originLongitude": 127.0276,
  "destinationLatitude": 37.5100,
  "destinationLongitude": 127.0620,
  "radiusKm": 5,
  "fuelAmountLiters": 45.0,
  "fuelEfficiencyKmPerLiter": 12.3,
  "fuelTypes": ["REGULAR_GASOLINE", "PREMIUM_GASOLINE", "DIESEL"],
  "sortOrder": "CHEAPEST_FUEL_ASC",
  "routeResultMode": "ROUTE_WITH_STATIONS",
  "originLabel": "현재위치",
  "destinationLabel": "목적지"
}
```

### cURL

```bash
curl -X POST "http://localhost:8080/api/stations/route" \
  -H "Content-Type: application/json" \
  -d '{
    "originLatitude": 37.4979,
    "originLongitude": 127.0276,
    "destinationLatitude": 37.5100,
    "destinationLongitude": 127.0620,
    "routeResultMode": "ROUTE_ONLY",
    "originLabel": "현재위치",
    "destinationLabel": "목적지"
  }'
```

### 응답 예시

```json
{
  "searchMode": "ROUTE",
  "coordinateSystem": "WGS84",
  "radiusKm": 5.0,
  "resultCount": 0,
  "referenceLabel": "현재위치 -> 목적지",
  "stations": [],
  "route": {
    "routePolyline": "37.4979,127.0276;37.5041,127.0412;37.5100,127.0620",
    "distanceMeters": 4820,
    "durationSeconds": 612,
    "tollFeeWon": 0,
    "fuelPriceWon": 1240,
    "routeOption": "traoptimal",
    "currentDateTime": "2026-06-08T10:30:00"
  }
}
```

`route.routePolyline`은 Kakao Map `RouteLine` 렌더링에 사용합니다. 백엔드는 네이버 Directions 응답을 프론트가 바로 그릴 수 있는 WGS84 좌표열로 변환해 반환합니다.

`ROUTE_WITH_STATIONS` 응답의 각 `stations[]` 항목에는 추가로 다음 값이 들어갈 수 있습니다.

- `distanceMeters`: 경로선에서 주유소까지의 최단 직선거리
- `routeExtraDistanceMeters`: 주유소를 경유했을 때 추가되는 실제 이동거리. 경로 위 주유소는 0이 될 수 있습니다.
- `estimatedTravelFuelCostWon`: 추가 이동거리에 대한 예상 이동 연료비
- `estimatedTotalCostWon`: 예상 총비용. 경로 추천에서는 `추가 이동비 + 예상 주유비` 기준이며 이 값이 낮은 순서로 반환됩니다.
- `detourRoute`: 현위치-주유소-목적지 경유 경로 정보

`detourRoute.routePolyline`은 특정 추천 주유소를 선택했을 때 지도에 경유 경로선을 그리는 데 사용할 수 있습니다.

## 3. 주유소 상세 조회

주유소 코드로 상세 정보를 조회합니다.

```http
GET /api/stations/{stationId}
```

### 응답 주요 필드

- `station.stationId`: 주유소 코드
- `station.stationName`: 상호명
- `station.address`: 도로명주소
- `station.phone`: 전화번호
- `station.latitude`, `station.longitude`: WGS84 좌표
- `station.fuelPrices`: 유종별 가격
- `station.updatedAt`: 최신 갱신 시각
- `station.notes`: 보조 안내

## 4. 목적지 자동완성

입력 중인 검색어로 자동완성 후보를 조회합니다. 이 API는 외부 카카오 API를 직접 호출하지 않고 백엔드 DB 후보를 우선 사용합니다.

```http
GET /api/places/autocomplete?query=관악&limit=10
```

## 5. 목적지 검색

사용자가 검색 버튼이나 키보드 검색키로 검색을 확정했을 때 호출합니다. 백엔드는 캐시를 먼저 확인하고, 캐시가 없을 때만 카카오 Local API를 호출합니다.

```http
POST /api/places/search
Content-Type: application/json
```

### 예시

```json
{
  "query": "관악산",
  "page": 1,
  "size": 10
}
```

응답의 `items[]`에서 `placeName`, `roadAddressName`, `addressName`, `latitude`, `longitude`를 우선 사용합니다.

## 6. Retrofit 예시

```kotlin
interface StationApiService {
    @GET("api/stations/nearby")
    fun searchNearbyStations(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radiusMeters") radiusMeters: Int,
        @Query("fuelAmountLiters") fuelAmountLiters: Double,
        @Query("fuelEfficiencyKmPerLiter") fuelEfficiencyKmPerLiter: Double,
        @Query("fuelTypes") fuelTypes: MutableList<FuelType>,
        @Query("sortOrder") sortOrder: StationSearchSortOrder,
        @Query("referenceLabel") referenceLabel: String
    ): Call<StationSearchResponse>

    @POST("api/stations/route")
    fun searchRouteStations(@Body request: RouteStationSearchRequest): Call<StationSearchResponse>

    @GET("api/stations/{stationId}")
    fun getStationDetail(@Path("stationId") stationId: String): Call<StationDetailResponse>
}
```

## 7. 주의사항

- 프론트는 `routeResultMode`를 목적에 맞게 명시하는 것을 권장합니다.
- `ROUTE_ONLY` 응답에서는 `stations[]`가 빈 배열일 수 있으므로 null-safe가 아니라 empty-safe로 처리합니다.
- `route`가 없거나 `routePolyline`이 비어 있으면 프론트는 기존 직선 fallback을 사용할 수 있습니다.
- HTTP가 막히는 환경에서는 Cloudflare Tunnel 같은 HTTPS 주소를 `secrets/backend_base_url.txt`에 반영합니다.


