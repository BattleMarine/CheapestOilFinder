# 프론트엔드-백엔드 API 가이드

이 문서는 안드로이드 프론트엔드가 백엔드에 요청을 보내는 방법을 정리한 문서입니다.

핵심 흐름은 다음과 같습니다.

1. 프론트엔드가 현재 위치와 목적지 좌표를 백엔드에 보냅니다.
2. 백엔드가 네이버 길찾기 API를 대신 호출해 경로를 계산합니다.
3. 백엔드가 경로 정보와 주변 주유소 정보를 함께 반환합니다.
4. 프론트엔드는 받은 결과를 지도와 리스트에 표시합니다.

---

## 1. 공통 규칙

- 좌표는 모두 `WGS84` 기준의 `위도(latitude) / 경도(longitude)`를 사용합니다.
- 백엔드는 내부적으로 네이버 길찾기 API를 호출할 때만 `경도,위도` 순서로 바꿉니다.
- 프론트엔드는 백엔드 주소를 하드코딩하지 말고 설정값으로 관리하는 것을 권장합니다.
- Android Emulator 기본 주소는 `http://10.0.2.2:8080/`입니다.
- 같은 Wi-Fi의 실기기 테스트에서는 `secrets/backend_base_url.txt`에 적힌 주소를 우선 사용합니다.
- release 빌드에서는 HTTP가 막힐 수 있으므로, 운영 환경은 HTTPS를 권장합니다.

### 백엔드 주소 예시

- 에뮬레이터: `http://10.0.2.2:8080/`
- 같은 Wi-Fi 실기기: `http://192.168.0.10:8080/`
- Cloudflare Tunnel: `https://xxxx.trycloudflare.com/`

---

## 2. 프론트엔드가 주로 쓰는 API

### 2-1. 주변 주유소 조회

현재 위치 기준으로 반경 안의 주유소를 가져옵니다.

#### 요청

```http
GET /api/stations/nearby
```

#### 쿼리 파라미터

- `latitude` : 현재 위치 위도
- `longitude` : 현재 위치 경도
- `radiusMeters` : 조회 반경 미터 단위
- `fuelAmountLiters` : 예상 주유량
- `fuelEfficiencyKmPerLiter` : 차량 연비
- `fuelTypes` : 조회할 유종 목록
- `sortOrder` : 정렬 방식 (`DISTANCE_ASC`, `CHEAPEST_FUEL_ASC`, `ESTIMATED_TOTAL_COST_ASC`)
- 예전 요청에서 쓰던 `PRICE_ASC` 는 하위 호환 별칭으로 백엔드가 `CHEAPEST_FUEL_ASC` 로 받아줍니다.
- `referenceLabel` : 화면에 표시할 기준 위치 이름

#### 예시

```text
GET /api/stations/nearby?latitude=37.4979&longitude=127.0276&radiusMeters=5000&fuelAmountLiters=45.0&fuelEfficiencyKmPerLiter=12.3&fuelTypes=REGULAR_GASOLINE&fuelTypes=PREMIUM_GASOLINE&fuelTypes=DIESEL&sortOrder=DISTANCE_ASC&referenceLabel=%ED%98%84%EC%9E%AC%20%EC%9C%84%EC%B9%98
```

#### cURL 예시

```bash
curl -X GET "http://localhost:8080/api/stations/nearby?latitude=37.4979&longitude=127.0276&radiusMeters=5000&fuelAmountLiters=45.0&fuelEfficiencyKmPerLiter=12.3&fuelTypes=REGULAR_GASOLINE&fuelTypes=PREMIUM_GASOLINE&fuelTypes=DIESEL&sortOrder=DISTANCE_ASC&referenceLabel=%ED%98%84%EC%9E%AC%20%EC%9C%84%EC%B9%98"
```

#### 응답 개요

- `searchMode`: `NEARBY`
- `coordinateSystem`: `WGS84`
- `radiusKm`: 조회 반경 km
- `resultCount`: 주유소 개수
- `referenceLabel`: 기준 위치 이름
- `stations[]`: 주유소 목록

#### 주유소 항목 예시

```json
{
  "stationId": "A0012345",
  "stationName": "예시주유소",
  "brandName": "SK",
  "address": "서울특별시 강남구 ...",
  "phone": "02-123-4567",
  "latitude": 37.4979,
  "longitude": 127.0276,
  "coordinateSystem": "WGS84",
  "distanceMeters": 820,
  "distanceBasis": "REFERENCE_POINT",
  "fuelPrices": {
    "regularGasolineWon": 1678,
    "premiumGasolineWon": 1789,
    "dieselWon": 1544,
    "lpgWon": null,
    "updatedAt": "2026-06-08T10:00:00"
  },
  "cheapestFuelType": "DIESEL",
  "cheapestFuelPriceWon": 1544,
  "estimatedTravelFuelCostWon": 16960,
  "estimatedTotalCostWon": 18480,
  "routeExtraDistanceMeters": null,
  "notes": [
    "좌표계: WGS84",
    "거리 기준: REFERENCE_POINT",
    "유가 최신 시각: 2026-06-08T10:00:00",
    "전화번호 등록됨"
  ],
  "updatedAt": "2026-06-08T10:00:00"
}
```

---

## 3. 경로 요청 API

이 API가 이번에 추가된 핵심입니다.

프론트엔드가 현재 위치와 목적지 좌표를 보내면, 백엔드가 네이버 길찾기 API를 대신 호출해서 경로를 계산하고 반환합니다.

### 3-1. 요청

```http
POST /api/stations/route
Content-Type: application/json
```

### 3-2. 요청 본문

- `originLatitude` : 출발지 위도
- `originLongitude` : 출발지 경도
- `destinationLatitude` : 목적지 위도
- `destinationLongitude` : 목적지 경도
- `routePolyline` : 선택값, 이미 경로가 있으면 전달 가능하고 없어도 됨
- `radiusKm` : 경로 주변에서 검색할 반경 km
- `fuelAmountLiters` : 예상 주유량
- `fuelEfficiencyKmPerLiter` : 차량 연비
- `fuelTypes` : 조회할 유종 목록
- `sortOrder` : 정렬 방식
- `originLabel` : 출발지 이름
- `destinationLabel` : 목적지 이름

### 3-3. 요청 예시

```json
{
  "originLatitude": 37.4979,
  "originLongitude": 127.0276,
  "destinationLatitude": 37.5100,
  "destinationLongitude": 127.0620,
  "routePolyline": null,
  "radiusKm": 5,
  "fuelAmountLiters": 45.0,
  "fuelEfficiencyKmPerLiter": 12.3,
  "fuelTypes": [
    "REGULAR_GASOLINE",
    "PREMIUM_GASOLINE",
    "DIESEL"
  ],
  "sortOrder": "CHEAPEST_FUEL_ASC",
  "originLabel": "현재위치",
  "destinationLabel": "선택한 주유소"
}
```

### 3-4. cURL 예시

```bash
curl.exe -X POST "http://localhost:8080/api/stations/route" `
  -H "Content-Type: application/json" `
  --data-raw '{
    "originLatitude": 37.4979,
    "originLongitude": 127.0276,
    "destinationLatitude": 37.5100,
    "destinationLongitude": 127.0620,
    "routePolyline": null,
    "radiusKm": 5,
    "fuelAmountLiters": 45.0,
    "fuelEfficiencyKmPerLiter": 12.3,
    "fuelTypes": ["REGULAR_GASOLINE", "PREMIUM_GASOLINE", "DIESEL"],
    "sortOrder": "CHEAPEST_FUEL_ASC",
    "originLabel": "현재위치",
    "destinationLabel": "선택한 주유소"
  }'
```

### 3-5. 응답 개요

경로 요청 응답은 주변 주유소 목록과 함께, 경로 요약을 담은 `route` 객체를 함께 돌려줄 수 있습니다.

- `searchMode`: `ROUTE`
- `coordinateSystem`: `WGS84`
- `radiusKm`: 검색 반경 km
- `resultCount`: 주유소 개수
- `referenceLabel`: 경로 기준 이름
- `stations[]`: 경로 주변 주유소 목록
- `route`: 네이버 길찾기 응답 요약

### 3-6. 경로 객체 예시

```json
{
  "routePolyline": "37.4979,127.0276;37.5041,127.0412;37.5100,127.0620",
  "distanceMeters": 4820,
  "durationSeconds": 612,
  "tollFeeWon": 0,
  "fuelPriceWon": 1240,
  "routeOption": "traoptimal",
  "currentDateTime": "2026-06-08T10:30:00"
}
```

### 3-7. 응답 전체 예시

```json
{
  "searchMode": "ROUTE",
  "coordinateSystem": "WGS84",
  "radiusKm": 5.0,
  "resultCount": 2,
  "referenceLabel": "현재위치 -> 선택한 주유소",
  "stations": [
    {
      "stationId": "A0012345",
      "stationName": "예시주유소 1",
      "brandName": "SK",
      "address": "서울특별시 강남구 ...",
      "phone": "02-123-4567",
      "latitude": 37.5012,
      "longitude": 127.0311,
      "coordinateSystem": "WGS84",
      "distanceMeters": 320,
      "distanceBasis": "ROUTE_LINE",
      "fuelPrices": {
        "regularGasolineWon": 1678,
        "premiumGasolineWon": 1789,
        "dieselWon": 1544,
        "lpgWon": null,
        "updatedAt": "2026-06-08T10:00:00"
      },
      "cheapestFuelType": "DIESEL",
      "cheapestFuelPriceWon": 1544,
      "estimatedTravelFuelCostWon": 16960,
      "estimatedTotalCostWon": 18480,
      "routeExtraDistanceMeters": 120,
      "notes": [
        "좌표계: WGS84",
        "거리 기준: ROUTE_LINE",
        "유가 최신 시각: 2026-06-08T10:00:00",
        "전화번호 등록됨"
      ],
      "updatedAt": "2026-06-08T10:00:00"
    }
  ],
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

### 3-8. 프론트에서 주의할 점

- `routePolyline`은 선택값입니다. 없어도 백엔드가 경로를 계산합니다.
- 위도/경도 순서는 프론트에서 보낸 그대로 사용하면 됩니다.
- 백엔드 내부에서만 네이버 규격인 `경도,위도` 순서로 바꿉니다.
- `routePolyline`이 오면 프론트는 지도 polyline 렌더링에 바로 쓸 수 있습니다.

---

## 4. 주유소 상세 조회

### 요청

```http
GET /api/stations/{stationId}
```

### 예시

```text
GET /api/stations/A0012345
```

### 응답 개요

- `coordinateSystem`: `WGS84`
- `station`: 주유소 상세 정보

`station` 객체에는 주유소 코드, 상호명, 도로명주소, 전화번호, 좌표, 유가 정보, 최신 갱신 시각, 특이사항(notes)이 들어갑니다.

### `station` 필드에 포함되는 값

- `stationId`: 주유소 코드
- `stationName`: 상호명
- `brandName`: 상표 코드
- `address`: 도로명주소
- `phone`: 전화번호
- `latitude`, `longitude`: WGS84 좌표
- `coordinateSystem`: 좌표계 문자열
- `fuelPrices`: 현재 유가 정보
- `updatedAt`: 최신 갱신 시각
- `notes`: 특이사항 목록

### 응답 예시

```json
{
  "coordinateSystem": "WGS84",
  "station": {
    "stationId": "A0012345",
    "stationName": "샘플주유소",
    "brandName": "HDX",
    "address": "서울특별시 강남구 테헤란로 123",
    "phone": "02-123-4567",
    "latitude": 37.4979,
    "longitude": 127.0276,
    "coordinateSystem": "WGS84",
    "fuelPrices": {
      "gasLow": 1650,
      "gasHign": 1760,
      "disl": 1520,
      "lpg": null,
      "updatedAt": "2026-06-08T10:30:00"
    },
    "notes": [
      "좌표계: WGS84",
      "거리 기준: REFERENCE_POINT",
      "유가 최신 시각: 2026-06-08T10:30:00",
      "전화번호 등록됨"
    ],
    "updatedAt": "2026-06-08T10:30:00"
  }
}
```

### 프론트에서 어떻게 쓰면 되는가

이 API는 주유소 목록이나 지도 마커를 눌렀을 때, 해당 주유소의 상세 패널을 채우는 용도로 쓰면 가장 편합니다.

권장 흐름은 다음과 같습니다.

1. 프론트에서 주유소 목록 또는 지도 마커를 선택합니다.
2. 선택한 항목의 `stationId`를 꺼냅니다.
3. `GET /api/stations/{stationId}` 를 호출합니다.
4. 응답의 `station` 안에서 다음 값을 꺼내 상세 화면에 표시합니다.
   - `stationId`: 주유소 코드
   - `stationName`: 상호명
   - `address`: 도로명주소
   - `phone`: 전화번호
   - `fuelPrices`: 현재 유가
   - `notes`: 특이사항
5. 필요하면 `latitude`, `longitude`로 마커 중심을 다시 맞춥니다.

### 프론트에서 주의할 점

- 이 API는 검색 API가 아니라 **단일 주유소 상세 조회 API**입니다.
- `stationId`는 목록 조회 응답에 들어 있는 값을 그대로 쓰면 됩니다.
- `notes`는 고정 필드가 아니라, 화면 설명을 돕기 위해 백엔드가 덧붙이는 보조 정보입니다.
- `phone`이나 `fuelPrices`가 비어 있을 수 있으니, 프론트에서는 null-safe 처리로 보여주는 편이 좋습니다.
- 상세 패널은 `stationName`, `address`, `phone`을 먼저 보여주고, `notes`는 아래쪽 보조 설명으로 붙이면 읽기 좋습니다.

### 프론트 화면 예시

- 지도 마커 탭
- 주유소 리스트 항목 탭
- 경로 추천 결과에서 특정 주유소 선택

이 세 곳 모두 같은 `GET /api/stations/{stationId}`를 써서 통일할 수 있습니다.

---

## 5. Retrofit 인터페이스 예시

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

---

## 6. 프론트에서 자주 쓰는 유종과 정렬 값

### 유종

- `REGULAR_GASOLINE`
- `PREMIUM_GASOLINE`
- `DIESEL`
- `LPG`

### 정렬

- `DISTANCE_ASC`
- `CHEAPEST_FUEL_ASC`
- `ESTIMATED_TOTAL_COST_ASC`

---

## 7. 에러 처리

- HTTP 2xx가 아니면 백엔드에서 에러 응답을 반환합니다.
- 네트워크 실패, DNS 실패, 타임아웃은 프론트에서 별도 예외로 처리합니다.
- route API 호출 실패 시에도 주변 주유소 목록만 먼저 보여주는 방식으로 UX를 유지할 수 있습니다.

---

## 8. 로컬 개발 메모

- 에뮬레이터는 `http://10.0.2.2:8080/`을 기본으로 사용합니다.
- 실기기에서는 같은 Wi-Fi의 PC IP 또는 터널 주소를 사용합니다.
- 백엔드 주소를 자주 바꿔야 하면 `secrets/backend_base_url.txt`를 갱신하고 앱 설정에서 읽어오도록 합니다.

---

## 9. 요약

프론트엔드가 현재 위치와 목적지를 보내면, 백엔드가 네이버 길찾기 API를 대신 호출해서 경로를 받아오고, 그 경로를 기준으로 주유소 목록과 경로 요약을 함께 돌려줍니다.

즉,

- 프론트엔드: 좌표를 보낸다
- 백엔드: 네이버 길찾기를 호출한다
- 백엔드: 경로와 주유소 목록을 돌려준다
- 프론트엔드: 지도에 polyline과 마커를 그린다
