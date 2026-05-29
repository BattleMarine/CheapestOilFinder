# Agent Notes

## 현재 언어 규칙

- 앱 구현 코드는 Kotlin을 기본으로 합니다.
- 기존 Java 소스는 제거했고, 신규/수정 코드는 Kotlin으로 작성합니다.

## 현재 지도 마커 규칙

- 현재 위치 화면은 테스트용으로 삼성역을 기준 좌표로 사용하고, 그 주변 주유소를 백엔드에서 받아 지도에 마커로 표시합니다.
- 카카오맵 마커는 `KakaoMapController`의 `LabelLayer` 렌더링으로 관리합니다.
- 현재 위치 마커와 주유소 마커는 분리해서 유지합니다.

이 문서는 이 프로젝트에서 Codex가 작업할 때 지켜야 할 규칙과 현재 합의된 구현 방향을 기록합니다.

## 담당 범위

- 현재 Codex의 주 담당 범위는 Android 프론트엔드입니다.
- 백엔드 서버, 오피넷 수집기, PostgreSQL 스키마 구현은 현재 범위 밖입니다.
- 프론트엔드는 오피넷 등 외부 유가 API를 직접 호출하지 않습니다.
- 주유소, 경로, 추천 결과 데이터는 백엔드 서버에서 받아오는 구조를 전제로 합니다.
- 백엔드가 준비되기 전에는 더미 데이터 또는 placeholder repository로 UI 흐름을 검증합니다.

## 문서 갱신 규칙

기능을 구현하거나 구조를 바꾸면 관련 문서를 함께 갱신합니다.

- 사용자와 제품 진행 상황에 영향을 주는 변경은 `README.md`에 기록합니다.
- 에이전트 작업 규칙, 보안 규칙, 역할 범위, 구현 전제가 바뀌면 `AGENTS.md`에 기록합니다.
- 문서가 깨진 인코딩으로 저장되지 않도록 UTF-8 상태를 확인합니다.
- 사용자가 `AGESTS.md`처럼 오타로 지칭하더라도, 별도 요청이 없는 한 표준 파일명은 `AGENTS.md`로 유지합니다.

## 비밀 정보 규칙

- API 키, 네이티브 앱 키, 서명키, 토큰은 소스 코드에 직접 작성하지 않습니다.
- 로컬 민감 정보는 `secrets/` 폴더에 둡니다.
- 카카오맵 네이티브 앱 키는 `secrets/kakao_native_app_key.txt`에서 읽습니다.
- `secrets/*.txt`, `secrets/*.properties`, `secrets/*.key`, `secrets/*.jks`는 Git에 올리지 않습니다.
- `keystore.properties`, `signing/*.jks`, `signing/*.keystore`도 Git에 올리지 않습니다.
- release 빌드에는 민감한 API 키를 포함하지 않습니다.
- 키 해시나 서명 관련 디버그 로그는 개발 중에만 허용하고, 운영 빌드에서는 제한해야 합니다.
- 사용자가 실제 키를 입력한 파일은 읽거나 출력하지 않는 것을 기본으로 합니다. 필요하면 존재 여부만 확인합니다.

## 프론트엔드 아키텍처 방향

- 지도 SDK 세부 구현은 `KakaoMapController` 안에 모읍니다.
- Activity는 화면 이벤트와 상태 전달에 집중합니다.
- 백엔드 데이터 접근은 repository 계층으로 분리합니다.
- 백엔드 연동은 비동기 네트워크 호출을 전제로 설계합니다.
- 지도, 주유소, 경로, 추천 계산 모델은 SDK 전용 타입에 직접 묶지 않습니다.
- 주유소 마커, 경로 polyline, 추천 결과 오버레이는 지도 SDK 위에 얹는 렌더링 계층으로 확장합니다.
- 외부 내비게이션 앱 연동은 주유소 선택 후 별도 진입점으로 제공합니다.

## 백엔드 전제

백엔드가 담당할 것으로 보는 영역:

- 오피넷 API 호출
- 주유소 데이터 수집 및 정규화
- PostgreSQL 등 DB 저장
- 주변 주유소 검색
- 경로 주변 주유소 검색
- 가격 최신성 관리
- 필요 시 추천 계산 일부 또는 전체 수행

프론트엔드가 담당할 것으로 보는 영역:

- 현재 위치 또는 목적지 입력 UI
- 백엔드 API 호출
- 백엔드 응답을 지도 마커와 리스트로 렌더링
- 추천 결과, 총비용, 계산 근거 표시
- 외부 내비게이션 앱 연동 진입점 제공

## 현재 구현 메모

- 프로젝트는 Android Native Java 기반입니다.
- 카카오맵 SDK v2 의존성이 추가되어 있습니다.
- 지도 화면은 현재 위치 화면과 목적지 화면에 존재합니다.
- `KakaoMapConfig`는 `BuildConfig.KAKAO_NATIVE_APP_KEY`만 참조합니다.
- debug 빌드는 `secrets/kakao_native_app_key.txt`에서 키를 읽습니다.
- release 빌드는 카카오맵 키를 빈 값으로 주입합니다.
- x86_64 에뮬레이터에서 카카오맵 네이티브 라이브러리 로딩 문제가 발생할 수 있으며, 이 문제는 현재 개발 환경 이슈로 간주합니다.

## 우선 고려할 문제

- 한국어 문자열 리소스가 깨진 상태라 UI 검증 전에 복구가 필요합니다.
- `StationRepository`가 동기 `List` 반환 구조라 실제 서버 연동 전 비동기 구조로 바꿔야 합니다.
- 지도 시작 좌표가 컨트롤러 내부에 하드코딩되어 있어 외부 상태 주입 구조로 정리해야 합니다.
- `DebugKeyHashLogger`가 항상 호출되면 안전하지 않으므로 debug 조건으로 제한해야 합니다.

## 작업 방식

- 기존 사용자의 변경을 되돌리지 않습니다.
- 수동 파일 편집은 `apply_patch`를 사용합니다.
- 검색은 가능하면 `rg` 또는 `rg --files`를 사용합니다.
- README 또는 AGENTS에 영향을 주는 변경이면 코드 변경과 함께 문서도 갱신합니다.
- 빌드나 테스트가 가능한 변경이면 가능한 한 `gradlew.bat`로 검증합니다.
- 문서만 바꾼 경우에는 빌드 실행을 생략해도 되지만, 생략 사실을 최종 응답에 밝힙니다.
## 프론트엔드 백엔드 연동 메모

- 프론트엔드의 주유소 조회는 Retrofit 기반 `BackendStationRepository`를 통해 수행합니다.
- API 계약, 요청/응답 필드, 호출 예시는 [docs/FRONTEND_BACKEND_API_GUIDE.md](docs/FRONTEND_BACKEND_API_GUIDE.md)에 유지합니다.
- 주변/경로 조회는 `WGS84` 좌표를 사용하며, 기본 유종은 `REGULAR_GASOLINE`, `PREMIUM_GASOLINE`, `DIESEL`입니다.
- Android Emulator의 로컬 백엔드 주소는 `http://10.0.2.2:8080/`를 기본값으로 사용합니다.
- 현재 로컬 백엔드에서는 `GET /api/stations/nearby`가 살아 있지만, 요청 형식이 아직 맞지 않아 `400`이 발생합니다.
- `POST /api/stations/route`와 `GET /api/stations/{stationId}`는 현재 `404`를 반환합니다.
- 예전의 `/api/stations/search/nearby` 표기는 사용하지 않습니다.
- `docs/BACKEND_API_ACCESS_CLAIM.md`는 활성 최신본만 유지하고, 이전 실패 기록은 `docs/archive/` 아래로 아카이브합니다.
Retrofit query lists in Kotlin should suppress wildcards when passed through Retrofit interfaces.

## GitHub prep

- Keep `secrets/` entries, signing files, and build outputs out of Git history.
- Treat `secrets/*.example` and `secrets/README.md` as the only safe committed secrets-related files.

## 문서 언어 규칙

- 저장소의 `README.md`와 `AGENTS.md`는 한국어로 작성합니다.
- 새 문서나 기존 문서 수정 시에도 기본적으로 한국어를 사용합니다.

## README 작성 규칙

- `README.md`에는 반드시 프로젝트 소개를 포함합니다.
- `README.md`에는 앱이 어떤 문제를 해결하는지와 핵심 사용 흐름을 포함합니다.
- `README.md`에는 저장소 구조를 포함합니다.
- `README.md`에는 프론트엔드와 백엔드의 역할 분담을 포함합니다.
- `README.md`에는 백엔드 서버가 별도 서비스라는 점과 프론트엔드가 백엔드 API를 호출한다는 점을 포함합니다.
- `README.md`에는 Kakao Map SDK 사용 사실과 지도 마커 역할을 포함합니다.
- `README.md`에는 빌드 방법과 현재 상태 메모를 포함합니다.

## 비밀정보 문서 규칙

- `README.md`에는 비밀정보의 실제 값은 적지 않습니다.
- `README.md`에는 비밀정보의 경로, 파일명, 기대 형식만 적습니다.
- `README.md`에는 예시 값만 표기하고 실제 키 문자열은 절대 적지 않습니다.
- 카카오 네이티브 앱 키는 `secrets/kakao_native_app_key.txt`에서 읽는다고 명시합니다.
- `secrets/` 아래의 실제 비밀 파일들은 `.gitignore`로 무조건 제외되어야 합니다.
- 비밀정보 관련 예제 파일과 안내 문서만 Git에 남깁니다.

## 저장소 소개 문서 규칙

- README의 저장소 구조는 실제 디렉터리와 파일 배치를 반영합니다.
- README에는 백엔드 API 가이드 문서 위치를 링크로 남깁니다.
- README에는 로컬 개발에서 사용한 백엔드 주소나 검증 상태를 필요 시 기록합니다.
- README에는 사용자가 GitHub에서 처음 봤을 때 필요한 정보를 우선 배치합니다.
