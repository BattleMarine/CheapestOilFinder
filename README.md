# CheapestOilFinder

CheapestOilFinder는 현재 위치 또는 목적지를 기준으로 주유소를 찾고, 유가와 이동 비용을 함께 비교하기 위한 Android 앱입니다.

Android 프론트엔드는 Kakao Maps SDK로 지도와 오버레이를 표시하고, 별도 백엔드 서버에서 주유소 위치·브랜드·유가·상세 정보를 받습니다. 오피넷과 네이버 Directions 5 같은 외부 API는 프론트엔드가 직접 호출하지 않고 백엔드가 담당하는 구조를 지향합니다.

## 핵심 사용 흐름

1. 사용자가 현재 위치 기준 찾기 화면에 진입합니다.
2. 앱이 위치 권한을 확인하고 기기 GPS 좌표를 읽습니다.
3. 현재 위치 마커와 반경 5km 원을 Kakao 지도에 표시합니다.
4. 백엔드의 주변 주유소 API를 호출합니다.
5. 받은 주유소를 브랜드 마커와 유가 말풍선으로 지도에 표시합니다.
6. 주유소 리스트는 화면 하단의 `최소화` 상태로 시작합니다.
7. 사용자가 리스트 또는 지도 마커에서 주유소를 선택하면 상세 패널을 엽니다.

## 현재 구현 상태

### 구현됨

- Kotlin 기반 Android Native 앱
- 메인, 현재 위치 기준 찾기, 목적지 기준 찾기, 설정 화면
- 앱 시작 시 위치 권한 확인 및 요청
- Kakao Maps SDK v2 지도 표시
- 현재 위치 GPS 자동 수신 및 파란색 현재 위치 마커 표시
- 현재 위치 기준 반경 5km 윤곽선 표시
- Retrofit 기반 주변 주유소 조회
- 브랜드 로고 주유소 마커와 유가 말풍선
- 보통휘발유 가격 기준 주유소 리스트 정렬
- 주유소 리스트의 `최소화 / 절반 / 전체` 3단계 패널
- 주유소 상세 API 호출 및 상세 패널 표시
- GPS 재수신과 4단계 줌 버튼

### 준비 중

- 주유소 선택 시 백엔드 경로 API 호출
- 네이버 Directions 5 결과를 Kakao 지도 `RouteLine`으로 표시
- 실제 경로 거리와 사용자 차량 연비, 1회 주유량을 이용한 예상 총비용 계산
- 목적지 검색과 경로 주변 주유소 추천
- 사용자 설정의 주 유종·연비·주유량을 전체 계산에 반영
- 외부 내비게이션 앱 연동

## 현재 위치 화면

현재 위치 화면은 지도가 화면 전체를 채우고, 뒤로가기·줌·GPS·패널 UI가 지도 위에 배치됩니다.

- 화면 진입 시 GPS를 한 번 읽고 주변 주유소를 자동 조회합니다.
- 주변 조회 반경은 `radiusMeters=5000`입니다.
- 기본 조회 유종은 보통휘발유, 고급휘발유, 디젤입니다.
- GPS 버튼은 누르는 즉시 저장된 현재 위치로 지도를 `줌 15`에 맞춘 뒤 GPS를 다시 읽습니다.
- 새 GPS 좌표를 받으면 해당 위치로 다시 중심을 맞춥니다.
- 이전 좌표와 새 좌표의 차이가 10m 미만이면 주변 주유소 재요청은 생략합니다.
- GPS 버튼은 지도보다 위, 주유소 리스트와 상세 패널보다 아래 레이어에 있습니다.
- `+`와 `-` 버튼은 줌 `11 / 12 / 13 / 15` 프리셋을 이동합니다.
- 손가락 핀치는 프리셋과 무관하게 자유롭게 확대·축소할 수 있습니다.
- 설정 화면에서 저장한 유종·연비·1회 주유량은 SharedPreferences에 보관되고, 현재 위치 화면은 이 값을 읽어 비용 계산에 반영합니다.

## 주유소 리스트 명세

주유소 리스트의 사용자 상태는 다음 세 단계뿐입니다.

| 상태 | 의미 |
| --- | --- |
| `최소화` | 리스트가 화면 하단에 도크되어 손잡이와 헤더 일부가 보이는 상태 |
| `절반` | 리스트가 화면 하단 절반을 차지하는 상태 |
| `전체` | 리스트가 화면 전체를 덮는 상태 |

내부 코드의 `HIDDEN`은 데이터가 아직 없거나 패널을 표시하지 않는 내부 상태이며, 사용자가 조작하는 3단계에는 포함하지 않습니다.

### 초기 상태

- 현재 위치 화면에서 첫 주변 주유소 조회가 성공하면 리스트는 반드시 `최소화` 상태로 시작합니다.
- `최소화`는 숨김이 아닙니다. 화면 하단에 패널과 손잡이가 보여야 합니다.
- 초기 표시를 위해 패널 컨테이너를 `VISIBLE`로 전환한 뒤 최소화 위치에 배치합니다.

### 상태 전환

| 현재 상태 | 위로 스와이프 | 아래로 스와이프 | 뒤로가기 |
| --- | --- | --- | --- |
| `최소화` | `절반` | 변화 없음 | 첫 입력은 안내, 2초 안에 다시 입력하면 메인 화면 |
| `절반` | `전체` | `최소화` | `최소화` |
| `전체` | 변화 없음 | `절반` | `절반` |

- 손잡이를 짧게 누르면 `최소화 -> 절반`, `절반 -> 전체`, `전체 -> 절반`으로 전환합니다.
- 패널 상태 전환 제스처는 리스트 상단의 가로로 긴 손잡이 터치 영역에서만 받습니다.
- 리스트 본문은 항목 선택과 세로 스크롤만 담당합니다.
- `최소화`와 `절반` 상태에서는 배경 스크림이 지도 터치를 가로채지 않습니다.
- 리스트 또는 상세 패널이 표시되는 동안 `현 위치로 설정` 버튼은 숨깁니다.

## 지도 마커와 유가 말풍선

- 현재 위치 마커는 파란색 점으로 표시하며 주유소 레이어보다 위에 둡니다.
- 현재 위치의 5km 원은 지도 바로 위, 모든 주유소 마커보다 아래에 둡니다.
- 주유소 마커는 브랜드별 짧은 투명 PNG 로고를 사용합니다.
- 리스트 항목에는 브랜드별 전체 로고를 표시합니다.
- 주유소 마커의 보이는 크기와 클릭 판정 영역은 분리되어 있습니다.
- 유가 말풍선은 주유소 로고보다 위쪽에 배치하고, 중앙 하단 꼬리가 마커를 가리킵니다.
- 가격이 `0원`인 유종은 값이 없는 것으로 보고 표시하지 않습니다.
- 보통·고급휘발유가 모두 있으면 `휘발유(고급) 보통가격(고급가격)` 형식으로 표시합니다.
- 보통휘발유만 있으면 `휘발유 가격`, 고급휘발유만 있으면 `고급휘발유 가격`으로 표시합니다.
- 디젤은 별도 줄에 표시합니다.
- 마커와 말풍선은 겹쳐도 경쟁으로 숨기지 않습니다.
- 화면 아래쪽에 있는 주유소가 위에 쌓이도록 카메라 이동 종료 시 화면 Y 좌표 기준으로 순서를 갱신합니다.

### 줌별 표시 수

| Kakao 줌 | 주유소 표시 범위 |
| --- | --- |
| `11` 이하 | 최저가 상위 5개 |
| `12` | 최저가 상위 20개 |
| `13~14` | 최저가 상위 50개 |
| `15` 이상 | 전체 |

- 줌아웃 구간에서는 휘발유 계열만 간소화해 표시합니다.
- 줌 `15` 이상에서는 디젤을 포함한 상세 유가를 표시합니다.
- 유가 말풍선은 낮은 줌에서 작고 옅게, 높은 줌에서 크고 선명하게 표시합니다.
- 말풍선 배경은 흰색을 유지하고 글자와 내용의 표시 강도를 조절합니다.

## 주유소 상세 패널

- 지도 마커 또는 리스트 항목을 누르면 선택한 주유소로 지도를 이동하고 하단 상세 패널을 엽니다.
- 상세 패널은 처음 화면 절반 높이로 열리며 위로 스와이프하면 전체 화면으로 확장됩니다.
- 패널 밖을 누르거나 패널을 아래로 충분히 스와이프하면 닫힙니다.
- 패널이 열려 있는 동안에는 패널이 입력 우선권을 갖지만 지도 배경을 어둡게 만들지 않습니다.
- 표시 항목은 브랜드 로고, 상호명, 주소, 전화번호, 유가, 거리, 선택 유종 가격, 이동비용, 예상 주유비, 예상 총비용입니다.
- 선택 즉시 목록 데이터를 먼저 표시하고 `GET /api/stations/{stationId}` 응답으로 갱신합니다.
- 현재 백엔드 응답에서 전화번호가 비어 있으면 임시 문구를 표시합니다.
- 현재 위치 화면의 비용 계산은 저장된 유종·연비·1회 주유량과 응답 거리로 계산합니다.

## 백엔드 연동

프론트엔드는 Retrofit 저장소 계층을 통해 다음 API를 사용합니다.

- `GET /api/stations/nearby`: 현재 위치 주변 주유소 조회
- `GET /api/stations/{stationId}`: 선택한 주유소 상세 조회
- `POST /api/stations/route`: 요청 인터페이스와 repository 메서드가 있으며, 경로 형상 응답 DTO와 화면 연결은 준비 중

좌표는 프론트엔드와 백엔드 사이에서 `WGS84`의 `latitude / longitude`로 교환합니다. 네이버 Directions 5 호출에 필요한 `경도,위도` 변환과 인증 정보 관리는 백엔드 책임입니다.

세부 계약과 호출 예시는 [프론트엔드-백엔드 API 가이드](docs/FRONTEND_BACKEND_API_GUIDE.md)를 참고합니다.

## 설정과 데이터 흐름

### Kakao Maps SDK 키 주입

```text
secrets/kakao_native_app_key.txt
  -> app/build.gradle.kts
  -> BuildConfig.KAKAO_NATIVE_APP_KEY
  -> KakaoMapConfig
  -> CheapOilApplication
  -> KakaoMapSdk.init(...)
```

- `app/build.gradle.kts`가 로컬 키 파일을 빌드 시점에 읽어 debug `BuildConfig` 필드로 주입합니다.
- `KakaoMapConfig`는 소스 코드에 키를 보관하지 않고 `BuildConfig` 값만 제공합니다.
- `CheapOilApplication`이 앱 프로세스 시작 시 키 존재 여부를 확인하고 Kakao Maps SDK를 초기화합니다.
- release 빌드에는 Kakao 네이티브 앱 키를 빈 값으로 주입합니다.

### 백엔드 주소 주입

```text
secrets/backend_base_url.txt
  -> app/build.gradle.kts
  -> BuildConfig.BACKEND_BASE_URL
  -> BackendApiConfig
  -> BackendApiClient
  -> Retrofit StationApiService
```

- 로컬 파일이 없으면 `http://10.0.2.2:8080/`을 개발 기본값으로 사용합니다.
- `BackendApiConfig`가 주소를 제공하고, `BackendApiClient`가 URL 끝의 `/`를 정규화한 뒤 Retrofit을 생성합니다.
- 화면 코드는 URL을 직접 조합하지 않고 `BackendStationRepository`를 통해 API를 호출합니다.

### 사용자 설정 저장

```text
SettingsActivity
  -> UserPreferenceManager
  -> SharedPreferences
```

- `UserPreferenceManager`가 `fuel_type`, `fuel_efficiency_km_per_l`, `refuel_amount_liter`를 저장하고 읽습니다.
- `CurrentLocationActivity`는 저장값을 읽어 주변 주유소 조회 요청과 비용 계산에 반영합니다.
- `CostCalculator`가 이동비용, 예상 주유비, 예상 총비용 계산을 담당합니다.

### 주변 주유소 조회

```text
CurrentLocationActivity
  -> DeviceLocationResolver
  -> NearbyStationSearchRequest
  -> BackendStationRepository.searchNearbyStations(...)
  -> StationApiService GET /api/stations/nearby
  -> StationSearchResponse
  -> StationDisplayMapper
  -> List<GasStation>
  -> KakaoMapController + StationListAdapter
```

1. `CurrentLocationActivity`가 GPS 좌표를 얻고 반경 5km 요청 객체를 만듭니다.
2. `BackendStationRepository`가 Retrofit 호출과 성공·실패 응답 처리를 담당합니다.
3. Gson이 JSON 응답을 `StationSearchResponse`와 `StationSearchItem`으로 변환합니다.
4. `StationDisplayMapper`가 백엔드 DTO를 지도와 UI에서 사용하는 `GasStation` 모델로 바꿉니다.
5. `KakaoMapController`가 브랜드 마커·유가 말풍선·카메라를 갱신합니다.
6. `StationListAdapter`가 같은 데이터를 리스트 항목으로 표시합니다.
7. 첫 성공 조회 후 `CurrentLocationActivity`가 리스트 패널을 하단 `최소화` 상태로 엽니다.

### 주유소 상세 조회

```text
지도 마커 또는 리스트 항목 선택
  -> CurrentLocationActivity.openStationInfo(...)
  -> GET /api/stations/{stationId}
  -> StationDetailResponse
  -> 기존 목록 데이터와 병합
  -> 상세 패널 갱신
```

- 선택 직후에는 이미 받은 목록 데이터로 상세 패널을 빠르게 먼저 엽니다.
- 상세 API가 성공하면 상호명·브랜드·주소·전화번호·유가를 응답값으로 갱신합니다.
- 상세 응답에 값이 없으면 목록 데이터나 임시 안내 문구를 유지합니다.

### 경로 기능 확장 지점

```text
선택 주유소 + 현재 위치
  -> BackendStationRepository.searchRouteStations(...)
  -> POST /api/stations/route
  -> 경로 응답 DTO
  -> RouteInfo
  -> KakaoMapController.showRoute(...)
```

현재는 요청 인터페이스와 repository 메서드까지만 존재합니다. 다음 구현에서는 경로 좌표·거리·시간을 받을 DTO를 추가하고, `KakaoMapController.showRoute()`가 Kakao `RouteLine`을 실제로 생성하도록 연결합니다.

## 저장소 구조

```text
app/
  src/main/java/com/example/cheapestoilfinder/
    CheapOilApplication.kt       # 앱 시작과 Kakao Maps SDK 초기화
    entry/
      MainActivity.kt            # 첫 화면, 권한 확인, 화면 이동
      CurrentLocationActivity.kt # GPS·주변 조회·리스트·상세 패널 상태
      DestinationActivity.kt     # 목적지 화면 지도와 GPS
      StationListAdapter.kt      # 주유소 리스트 항목 렌더링
    location/
      DeviceLocationResolver.kt  # 기기 위치 조회
    map/
      KakaoMapConfig.kt          # BuildConfig의 Kakao 키 접근
      KakaoMapController.kt      # 지도, 카메라, 마커, 말풍선, 5km 원
      model/                     # 지도·화면 독립 모델
    recommendation/              # 추천 계산 로직
    settings/                    # 차량 설정 화면과 저장소 확장 지점
    station/
      api/                       # Retrofit 서비스와 API 설정
      dto/                       # 백엔드 요청·응답 모델
      BackendStationRepository.kt
      StationDisplayMapper.kt
      BrandLogoResolver.kt
  src/main/res/
    layout/                      # 화면과 리스트 XML
    drawable-nodpi/              # 앱에서 사용하는 브랜드 PNG
    values/                      # 문자열, 색상, 테마
docs/
  FRONTEND_BACKEND_API_GUIDE.md # 백엔드 API 계약
  BACKEND_API_ACCESS_CLAIM.md   # 최신 백엔드 연동 이슈, 로컬 전용
logo/                           # 브랜드 로고 원본
secrets/                        # 로컬 설정 안내와 추적 가능한 예제만 보관
app/build.gradle.kts            # 비밀 파일을 BuildConfig로 주입
README.md
AGENTS.md
```

## 주요 파일 빠른 찾기

| 작업 | 먼저 확인할 파일 |
| --- | --- |
| 현재 위치 화면과 리스트 상태 | `CurrentLocationActivity.kt`, `activity_current_location.xml` |
| GPS 수신 | `DeviceLocationResolver.kt` |
| 지도 마커·말풍선·카메라 | `KakaoMapController.kt` |
| 주변·상세·경로 API 정의 | `StationApiService.kt` |
| Retrofit 호출과 오류 처리 | `BackendStationRepository.kt`, `BackendApiClient.kt` |
| 백엔드 DTO 변환 | `StationDisplayMapper.kt`, `station/dto/` |
| 브랜드 로고 선택 | `BrandLogoResolver.kt`, `drawable-nodpi/` |
| API 키와 백엔드 주소 빌드 주입 | `app/build.gradle.kts`, `KakaoMapConfig.kt`, `BackendApiConfig.kt` |
| API 계약과 호출 예시 | `docs/FRONTEND_BACKEND_API_GUIDE.md` |
| 개발 규칙과 UI 상태 계약 | `AGENTS.md` |

## 개발 작업 규칙

- 작업을 시작하기 전에 `README.md`와 `AGENTS.md`를 읽고 현재 구현 방향과 제약을 확인합니다.
- 기능 구현이나 사용자 동작을 변경하면 `README.md`를 함께 갱신합니다.
- 에이전트 규칙, 보안, 아키텍처 전제, UI 상태 계약이 바뀌면 `AGENTS.md`도 갱신합니다.
- 문서 끝에 “최근 규칙”을 계속 추가하지 않고, 같은 주제의 기존 설명을 찾아 교체하거나 통합합니다.
- 앱 코드는 Kotlin을 기본으로 작성합니다.
- 비밀 파일과 디버깅 이미지, UI dump, 로컬 백엔드 클레임 문서는 Git에 포함하지 않습니다.
- Kotlin 또는 Android 리소스를 수정하면 가능한 한 `.\gradlew.bat compileDebugKotlin`으로 검증합니다.
- 주유소 리스트를 수정하면 최소화·절반·전체 전환, 본문 스크롤, 지도 터치, 뒤로가기 동작을 함께 확인합니다.
- 전체 작업 규칙의 정본은 `AGENTS.md`입니다.

### 커밋 메시지

변경 성격에 따라 다음 접두사를 사용합니다.

- 문서 위주: `docs:`
- 기능 구현 위주: `feat:`
- 버그 수정 위주: `fix:`

커밋 메시지는 제목 한 줄과 세부 bullet 두 개 이상으로 작성합니다.

```text
타입: 주된 핵심 변화

- 세부 변화 1
- 세부 변화 2
```

예시:

```text
fix: 주유소 리스트 초기 최소화 상태 복구

- 첫 조회 성공 시 하단 도크 패널을 명시적으로 표시
- 리스트 3단계 상태 정의와 회귀 검증 문서 정리
```

- 커밋 전에는 `git status`와 diff를 확인해 변경 범위를 점검합니다.
- 사용자가 요청하지 않은 파일은 커밋하지 않습니다.
- 기존 커밋 amend와 원격 push는 사용자가 명시적으로 요청한 경우에만 수행합니다.

## 로컬 설정과 비밀정보

실제 값은 다음 로컬 파일에 한 줄로 저장합니다.

```text
secrets/kakao_native_app_key.txt
secrets/backend_base_url.txt
```

- `kakao_native_app_key.txt`: Kakao 네이티브 앱 키
- `backend_base_url.txt`: 끝에 `/`가 포함된 백엔드 기본 URL
- 파일이 없으면 개발 기본 주소 `http://10.0.2.2:8080/`을 사용합니다.
- 실제 `secrets/*.txt`, `*.properties`, 서명키와 keystore는 `.gitignore`로 제외합니다.
- 실제 키나 URL 값을 README, AGENTS, 소스 코드, 로그에 복사하지 않습니다.
- release 빌드의 Kakao 앱 키는 빈 값으로 주입합니다.

실기기에서 로컬 백엔드를 사용할 때는 다음 중 하나를 사용합니다.

- 같은 Wi-Fi: `http://<개발 PC LAN IP>:8080/`
- USB: `adb reverse tcp:8080 tcp:8080` 후 `http://127.0.0.1:8080/`
- 외부 테스트: Cloudflare Tunnel HTTPS 주소

## 빌드

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
.\gradlew.bat test
```

개발 환경은 Java 17, `compileSdk 35`, `minSdk 24`, `targetSdk 35`를 사용합니다.

## 알려진 제한

- Kakao Maps SDK 네이티브 라이브러리 때문에 일부 x86_64 에뮬레이터에서 실행이 실패할 수 있습니다.
- 주유소 상세 응답의 전화번호가 비어 있어 현재 임시 문구를 표시합니다.
- 경로 API 요청 인터페이스는 준비되어 있지만 경로 형상 응답 DTO, 주유소 선택 흐름, Kakao `RouteLine`에는 아직 연결되지 않았습니다.
- 목적지 화면은 지도와 GPS 중심 이동까지만 구현되어 있으며 목적지 검색·경로 조회는 준비 중입니다.
