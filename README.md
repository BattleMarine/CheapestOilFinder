# CheapestOilFinder

CheapestOilFinder는 주변 주유소의 유가를 비교하고, 해당 주유소까지 이동하는 데 드는 비용까지 고려해서 더 경제적인 주유소를 찾는 Android 앱입니다.

현재 저장소에는 프론트엔드 Android 앱이 들어 있습니다. 앱은 Kotlin과 XML 레이아웃으로 구성되어 있고, 다음 기능을 포함합니다.

- 시작 화면
- 현재 위치 기준 찾기 화면
- 목적지 기준 찾기 화면
- 설정 화면
- Kakao Map SDK 기반 지도 렌더링
- 백엔드 API를 통한 주유소 검색 연동

## 프로젝트 개요

앱의 기본 흐름은 다음과 같습니다.

1. 사용자가 현재 위치 기준 또는 목적지 기준 검색을 선택합니다.
2. 프론트엔드가 좌표와 검색 조건을 백엔드 서버로 전송합니다.
3. 백엔드가 주유소 데이터, 경로 관련 데이터, 추천 데이터를 반환합니다.
4. 프론트엔드가 받은 결과를 지도와 화면에 표시합니다.

이 저장소에는 백엔드 구현은 포함되어 있지 않습니다. 프론트엔드는 별도의 백엔드 서버가 주유소 검색 API와 JSON 응답을 제공한다는 전제를 두고 동작합니다.

로컬 개발에서는 `localhost:18080`에서 동작하는 백엔드 기준으로 연동을 검증했습니다.

## 저장소 구조

```text
app/
  src/main/java/com/example/cheapestoilfinder/
    entry/                # MainActivity, CurrentLocationActivity, DestinationActivity
    map/                  # KakaoMap 컨트롤러와 지도 모델
    station/              # 백엔드 repository, API service, DTO, 매핑
    settings/             # 설정 화면과 로컬 설정 저장
    recommendation/       # 추천 계산 도우미
  src/main/res/           # XML 레이아웃, drawable, values, theme 리소스
  build.gradle.kts
docs/                    # API 가이드, 백엔드 호출 점검 문서, archive
secrets/                 # 로컬 전용 비밀정보 템플릿과 안내 문서
signing/                 # 로컬 서명 자료, Git 제외 대상
README.md
AGENTS.md
.gitignore
```

`build/`, `app/build/`, IDE 캐시 파일, 화면 캡처, 임시 덤프 파일은 Git에 올리지 않도록 제외되어 있습니다.

## 백엔드 서버

이 프로젝트는 별도의 백엔드 서버가 주유소 데이터를 제공한다는 구조를 전제로 합니다. 프론트엔드는 유가 데이터베이스에 직접 접근하지 않습니다.

백엔드의 역할:

- 주유소 데이터 수집과 정규화
- 백엔드 DB 저장
- 주변 주유소 검색
- 경로 기반 주유소 검색
- 주유소 상세 정보 제공

프론트엔드의 역할:

- 사용자 입력 수집
- 백엔드 API 호출
- 지도 마커와 목록 렌더링
- 추천 결과와 총비용 표시

API 계약과 요청/응답 예시는 [docs/FRONTEND_BACKEND_API_GUIDE.md](docs/FRONTEND_BACKEND_API_GUIDE.md)에 정리되어 있습니다.

## Kakao Map SDK

지도 UI는 Kakao Map SDK를 사용합니다. 현재 위치 화면과 목적지 화면이 주요 지도 화면입니다.

지도 마커 렌더링은 `KakaoMapController`가 담당하며, 현재는 KakaoMap label layer를 사용해서 다음을 표시합니다.

- 현재 위치 마커
- 주유소 마커

## 로컬 비밀정보

민감한 값은 `secrets/` 아래에 로컬로만 저장하며, `.gitignore`로 Git 기록에서 제외합니다.

### 카카오 네이티브 앱 키

- 파일 경로: `secrets/kakao_native_app_key.txt`
- 파일 이름: `kakao_native_app_key.txt`
- 기대 내용: 한 줄짜리 카카오 네이티브 앱 키 문자열

예시 형식:

```text
your_kakao_native_app_key
```

실제 키는 절대 Git에 올리면 안 됩니다. 저장소에는 예제/템플릿 파일만 남겨둡니다.

## Git 제외 규칙

이 저장소는 다음을 Git에서 제외합니다.

- Gradle 및 Android 빌드 산출물
- Android Studio / IntelliJ 프로젝트 캐시
- 로컬 머신 전용 설정 파일
- 서명 키와 릴리즈 keystore
- `secrets/` 아래의 실제 비밀 파일
- 디버깅 중 생성되는 화면 캡처와 UI 덤프 파일

## 빌드

디버그 빌드:

```powershell
.\gradlew.bat assembleDebug
```

연결된 기기나 에뮬레이터에 디버그 빌드 설치:

```powershell
.\gradlew.bat installDebug
```

## 현재 상태 메모

- 앱은 현재 Kotlin으로 구현되어 있습니다.
- UI는 여전히 XML 레이아웃을 사용합니다.
- 현재 위치 테스트 흐름은 개발용으로 삼성역 좌표를 고정값으로 사용합니다.
- 백엔드 연동은 활성화되어 있으며, 현재 위치 화면은 시작 시 더 이상 크래시하지 않습니다.

