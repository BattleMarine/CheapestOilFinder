# 프론트엔드 - 백엔드 주유소 조회 API 가이드

이 문서는 Android 프론트엔드가 백엔드 서버에 주유소 정보를 요청할 때 사용하는 계약을 정리합니다.

프론트엔드의 기본 역할은 다음과 같습니다.

1. 현재 위치, 목적지, 경로를 좌표로 정리한다.
2. 백엔드에 반경 기반 주유소 조회를 요청한다.
3. 백엔드가 내려준 결과를 지도와 리스트에 렌더링한다.
4. 프론트엔드는 필요 시 받은 결과를 다시 가격 순으로 정렬한다.

---

## 1. 공통 전제

- 좌표계는 `WGS84`를 사용한다.
- Kakao Map은 `WGS84` 좌표를 바로 사용할 수 있다.
- 프론트엔드가 보내는 값은 주소 문자열이 아니라, 주소를 해석한 `latitude`, `longitude`이다.
- 현재 기본 유종 범위는 `REGULAR_GASOLINE`, `PREMIUM_GASOLINE`, `DIESEL`이다.
- `LPG`는 DB에는 남겨두지만, 기본 조회에서는 제외한다.
- 기본 주유량은 `30L`, 기본 연비는 `10km/L`이다.

### 기본 서버 주소 예시

- Android Emulator: `http://10.0.2.2:8080/`
- 실제 단말기: 개발 PC의 LAN IP 예 `http://192.168.0.10:8080/`
- 운영 서버: 배포한 백엔드 주소

---

## 2. 프론트엔드 호출 구조

권장 구조는 다음과 같다.

```text
Activity / Fragment
  -> ViewModel
    -> StationRepository
      -> BackendStationRepository
        -> Retrofit
          -> Spring Boot API
```

### 사용 클래스

- `com.example.cheapestoilfinder.station.StationRepository`
- `com.example.cheapestoilfinder.station.BackendStationRepository`
- `com.example.cheapestoilfinder.station.api.ApiCallback`
- `com.example.cheapestoilfinder.station.dto.NearbyStationSearchRequest`
- `com.example.cheapestoilfinder.station.dto.RouteStationSearchRequest`
- `com.example.cheapestoilfinder.station.dto.StationSearchResponse`
- `com.example.cheapestoilfinder.station.dto.StationDetailResponse`

---

## 3. 메소드 목록

### 3-1. `searchNearbyStations`

현재 위치 같은 단일 기준점을 중심으로 반경 내 주유소를 조회한다.

#### 메소드 시그니처

```java
void searchNearbyStations(
    NearbyStationSearchRequest request,
    ApiCallback<StationSearchResponse> callback
)
```

#### 인자

- `request`
  - `latitude`: 기준 위도
  - `longitude`: 기준 경도
  - `radiusKm`: 검색 반경 km
  - `fuelAmountLiters`: 기본 주유량
  - `fuelEfficiencyKmPerLiter`: 차량 연비
  - `fuelTypes`: 조회할 유종 목록
  - `sortOrder`: 정렬 정책
  - `referenceLabel`: 현재 위치 같은 표시용 라벨
- `callback`
  - 성공 시 `StationSearchResponse`를 받는다.
  - 실패 시 네트워크 오류 또는 `BackendApiException`을 받는다.

#### 요청 HTTP

```http
GET /api/stations/nearby?latitude=37.5665&longitude=126.9780&radiusKm=5.0&fuelAmountLiters=30.0&fuelEfficiencyKmPerLiter=10.0&fuelTypes=REGULAR_GASOLINE&fuelTypes=PREMIUM_GASOLINE&fuelTypes=DIESEL&sortOrder=DISTANCE_ASC&referenceLabel=%ED%98%84%EC%9E%AC%20%EC%9C%84%EC%B9%98
```

Retrofit에서는 `@Query` 반복 전달 방식으로 보낸다.

#### 응답 JSON 예시

```json
{
  "searchMode": "NEARBY",
  "coordinateSystem": "WGS84",
  "radiusKm": 5.0,
  "resultCount": 2,
  "referenceLabel": "현재 위치",
  "stations": [
    {
      "stationId": "S-001",
      "stationName": "테스트 주유소 1",
      "brandName": "SK",
      "address": "서울시 중구",
      "latitude": 37.5665,
      "longitude": 126.978,
      "coordinateSystem": "WGS84",
      "distanceMeters": 820,
      "distanceBasis": "REFERENCE_POINT",
      "fuelPrices": {
        "regularGasolineWon": 1678,
        "premiumGasolineWon": 1789,
        "dieselWon": 1544,
        "lpgWon": null,
        "updatedAt": "2026-05-28T10:00:00"
      },
      "cheapestFuelType": "DIESEL",
      "cheapestFuelPriceWon": 1544,
      "estimatedTravelFuelCostWon": 15440,
      "estimatedTotalCostWon": 16960,
      "routeExtraDistanceMeters": null,
      "updatedAt": "2026-05-28T10:00:00"
    }
  ]
}
```

#### 필드 설명

- `searchMode`: 조회 종류, `NEARBY`
- `coordinateSystem`: 좌표계, 기본값은 `WGS84`
- `radiusKm`: 요청한 반경 km
- `resultCount`: 주유소 개수
- `referenceLabel`: 기준점 표시 문자열
- `stations[]`: 주유소 목록

#### 각 주유소 필드 설명

- `stationId`: 백엔드 기준 주유소 식별자
- `stationName`: 주유소명
- `brandName`: 상표명
- `address`: 주소
- `latitude`, `longitude`: Kakao Map에서 바로 쓸 수 있는 WGS84 좌표
- `distanceMeters`: 기준점에서의 직선거리
- `distanceBasis`: 거리 계산 기준
- `fuelPrices`: 유종별 가격 묶음
- `cheapestFuelType`: 현재 응답 안에서 가장 싼 유종
- `cheapestFuelPriceWon`: 가장 싼 유종 가격
- `estimatedTravelFuelCostWon`: 이동 비용 추정치
- `estimatedTotalCostWon`: 이동 비용 + 주유비 기준 총합
- `updatedAt`: 가격 기준 시각

---

### 3-2. `searchRouteStations`

현재 위치와 목적지 사이의 경로를 기준으로 경로 주변 주유소를 조회한다.

#### 메소드 시그니처

```java
void searchRouteStations(
    RouteStationSearchRequest request,
    ApiCallback<StationSearchResponse> callback
)
```

#### 인자

- `request`
  - `originLatitude`, `originLongitude`
  - `destinationLatitude`, `destinationLongitude`
  - `routePolyline`: 경로 폴리라인 문자열
  - `radiusKm`: 경로 주변 검색 반경
  - `fuelAmountLiters`: 기본 주유량
  - `fuelEfficiencyKmPerLiter`: 차량 연비
  - `fuelTypes`: 조회할 유종 목록
  - `sortOrder`: 정렬 정책
  - `originLabel`, `destinationLabel`: 표시용 라벨
- `callback`
  - 성공 시 `StationSearchResponse`
  - 실패 시 오류 객체

#### 요청 HTTP

```http
POST /api/stations/route
```

```json
{
  "originLatitude": 37.5563,
  "originLongitude": 126.9723,
  "destinationLatitude": 37.4979,
  "destinationLongitude": 127.0276,
  "routePolyline": "37.5563,126.9723;37.4979,127.0276",
  "radiusKm": 5.0,
  "fuelAmountLiters": 30.0,
  "fuelEfficiencyKmPerLiter": 10.0,
  "fuelTypes": ["REGULAR_GASOLINE", "DIESEL"],
  "sortOrder": "DISTANCE_ASC",
  "originLabel": "서울역",
  "destinationLabel": "강남역"
}
```

#### 응답 주의점

- `distanceBasis`는 `ROUTE_LINE`이 된다.
- `distanceMeters`는 경로 선분에 대한 기준 거리로 해석한다.
- 프론트엔드는 이 결과를 받아 가격 순으로 다시 정렬할 수 있다.

---

### 3-3. `getStationDetail`

특정 주유소 1건의 상세 정보를 요청한다.

#### 메소드 시그니처

```java
void getStationDetail(String stationId, ApiCallback<StationDetailResponse> callback)
```

#### 인자

- `stationId`: 조회할 주유소 식별자
- `callback`: 성공/실패 콜백

#### 응답 JSON 예시

```json
{
  "coordinateSystem": "WGS84",
  "station": {
    "stationId": "S-100",
    "stationName": "디테일 주유소",
    "brandName": "HD",
    "address": "부산시",
    "latitude": 35.1796,
    "longitude": 129.0756,
    "coordinateSystem": "WGS84",
    "distanceMeters": 0,
    "distanceBasis": "REFERENCE_POINT",
    "fuelPrices": {
      "regularGasolineWon": 1660,
      "premiumGasolineWon": 1770,
      "dieselWon": 1530,
      "lpgWon": null,
      "updatedAt": "2026-05-28T10:00:00"
    },
    "cheapestFuelType": "DIESEL",
    "cheapestFuelPriceWon": 1530,
    "estimatedTravelFuelCostWon": 15300,
    "estimatedTotalCostWon": 16800,
    "routeExtraDistanceMeters": null,
    "updatedAt": "2026-05-28T10:00:00"
  }
}
```

---

## 4. Java 호출 예시

```java
BackendStationRepository repository = BackendStationRepository.create("http://10.0.2.2:8080/");

NearbyStationSearchRequest request = new NearbyStationSearchRequest(
    37.5665,
    126.9780,
    5.0,
    30.0,
    10.0,
    Arrays.asList(FuelType.REGULAR_GASOLINE, FuelType.PREMIUM_GASOLINE, FuelType.DIESEL),
    "현재 위치"
);

repository.searchNearbyStations(request, new ApiCallback<StationSearchResponse>() {
    @Override
    public void onSuccess(StationSearchResponse result) {
        List<StationSearchItem> stations = new ArrayList<>(result.stations);
        Collections.sort(stations, (a, b) -> {
            int left = a.cheapestFuelPriceWon == null ? Integer.MAX_VALUE : a.cheapestFuelPriceWon;
            int right = b.cheapestFuelPriceWon == null ? Integer.MAX_VALUE : b.cheapestFuelPriceWon;
            return Integer.compare(left, right);
        });

        StationSearchItem cheapest = stations.get(0);
        // cheapest.stationName, cheapest.latitude, cheapest.longitude 로 지도 표시
    }

    @Override
    public void onError(Throwable error) {
        // 네트워크 실패 또는 BackendApiException 처리
    }
});
```

---

## 5. 테스트용 cURL 예시

### 주변 검색

```bash
curl -X GET "http://localhost:8080/api/stations/nearby?latitude=37.5665&longitude=126.9780&radiusKm=5.0&fuelAmountLiters=30.0&fuelEfficiencyKmPerLiter=10.0&fuelTypes=REGULAR_GASOLINE&fuelTypes=PREMIUM_GASOLINE&fuelTypes=DIESEL&sortOrder=DISTANCE_ASC&referenceLabel=%ED%98%84%EC%9E%AC%20%EC%9C%84%EC%B9%98"
```

### 경로 검색

```bash
curl -X POST "http://localhost:8080/api/stations/route" \
  -H "Content-Type: application/json" \
  -d "{\"originLatitude\":37.5563,\"originLongitude\":126.9723,\"destinationLatitude\":37.4979,\"destinationLongitude\":127.0276,\"routePolyline\":\"37.5563,126.9723;37.4979,127.0276\",\"radiusKm\":5.0,\"fuelAmountLiters\":30.0,\"fuelEfficiencyKmPerLiter\":10.0,\"fuelTypes\":[\"REGULAR_GASOLINE\",\"DIESEL\"],\"sortOrder\":\"DISTANCE_ASC\",\"originLabel\":\"서울역\",\"destinationLabel\":\"강남역\"}"
```

---

## 6. 오류 처리

- HTTP 2xx가 아니면 `BackendApiException`이 발생할 수 있다.
- 네트워크 끊김, DNS 실패, 타임아웃은 `Throwable`로 내려온다.
- 프론트엔드는 실패 시 토스트, 배너, 재시도 버튼 같은 UI를 붙이면 된다.

---

## 7. 프론트엔드 정렬 권장 방식

백엔드는 거리와 계산값을 미리 채워준다.
프론트엔드는 화면 목적에 맞게 추가 정렬을 하면 된다.

- 가장 가까운 순: `distanceMeters`
- 가장 싼 순: `cheapestFuelPriceWon`
- 총비용이 낮은 순: `estimatedTotalCostWon`

---

## 8. 후속 확장 포인트

- 할인 정보가 자동화되면 `fuelPrices` 안에 할인 전/후 금액을 분리할 수 있다.
- LPG가 서비스 범위에 들어오면 `fuelTypes` 기본 목록만 확장하면 된다.
- 추천 점수까지 내려주려면 `recommendationScore` 필드를 추가하면 된다.
