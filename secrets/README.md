# Local Secrets

이 폴더는 로컬 개발용 민감 정보를 보관하는 위치입니다.

카카오맵 SDK 네이티브 앱 키는 다음 파일에 저장합니다.

```text
kakao_native_app_key.txt
```

파일 내용은 키 문자열 한 줄만 둡니다.

```text
your_kakao_native_app_key
```

호환용으로 아래 properties 방식도 남겨둘 수 있지만, 현재 우선순위는 `kakao_native_app_key.txt`입니다.

```properties
KAKAO_NATIVE_APP_KEY=your_kakao_native_app_key
```

`*.txt`, `*.properties`, `*.key`, `*.jks` 파일은 Git에 올라가지 않도록 `.gitignore`에 등록되어 있습니다. 실제 키 값은 문서나 코드에 복사하지 않습니다.

