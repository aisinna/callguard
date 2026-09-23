# GitHub Actions로 APK 자동 빌드하기 (Android Studio 없이)

PC에 Android Studio를 설치하지 않고, GitHub에 코드만 올려서
클라우드에서 APK를 만들어 내려받는 방법입니다.

---

## 1. 최종 폴더 구조

GitHub 저장소를 아래와 같이 구성해야 합니다.
(파일 이름과 위치가 정확해야 빌드가 됩니다)

```
CallGuard/                          <- 저장소 최상단(루트)
 ├─ .github/
 │   └─ workflows/
 │        └─ build-apk.yml          <- 받은 build-apk.yml
 ├─ settings.gradle                 <- 받은 settings.gradle
 ├─ build.gradle                    <- 받은 root-build.gradle 을 "build.gradle" 로 이름 변경
 ├─ gradle.properties               <- 받은 gradle.properties
 └─ app/
     ├─ build.gradle                <- 앞서 받은 app용 build.gradle
     └─ src/main/
         ├─ AndroidManifest.xml
         ├─ java/com/parentcare/callguard/
         │    ├─ MainActivity.kt
         │    ├─ WarningActivity.kt
         │    ├─ CallStateReceiver.kt
         │    ├─ CallMonitorService.kt
         │    ├─ BootReceiver.kt
         │    ├─ SmsHelper.kt
         │    ├─ NotificationHelper.kt
         │    └─ PrefsHelper.kt
         └─ res/
             ├─ layout/
             │    ├─ activity_main.xml
             │    └─ activity_warning.xml
             └─ values/
                  └─ strings.xml
```

### 헷갈리기 쉬운 부분 (중요)

- `build.gradle` 이라는 이름의 파일이 **두 개** 입니다.
  - 루트의 `build.gradle`  <- 받은 파일 이름은 `root-build.gradle`. 이름을 `build.gradle` 로 바꿔서 최상단에 두세요.
  - `app/build.gradle`     <- 앞서 드린 앱용 설정.
- 워크플로 파일은 반드시 `.github/workflows/` 폴더 안에 있어야 합니다.
  폴더 이름 앞의 점(`.`)을 빠뜨리지 마세요.

---

## 2. 추가로 필요한 파일: strings.xml

`app/src/main/res/values/strings.xml` 을 아래 내용으로 만들어 주세요.

```xml
<resources>
    <string name="app_name">보이스피싱 안심콜</string>
</resources>
```

## 3. 앱 아이콘 관련 (빌드 실패 방지)

AndroidManifest.xml 에 `android:icon="@mipmap/ic_launcher"` 가 있는데,
아이콘 파일이 없으면 빌드가 실패합니다. 두 가지 해결책 중 하나를 쓰세요.

**방법 A (가장 간단)** - 매니페스트에서 아이콘 줄을 지웁니다.
`android:icon="@mipmap/ic_launcher"` 이 한 줄을 삭제하면 기본 아이콘으로 빌드됩니다.

**방법 B** - Android Studio에서 프로젝트를 한 번 만들어 생성된
`res/mipmap-*` 폴더들을 그대로 복사해 넣습니다.

---

## 4. GitHub에 올리는 순서

1. GitHub 접속 -> 우측 상단 `+` -> **New repository**
   - 이름: `CallGuard` (아무거나 가능)
   - Public 또는 Private 선택 (Private도 Actions 무료 사용량 있음)
   - **Create repository** 클릭

2. 파일 업로드
   - 저장소 화면에서 **Add file -> Upload files**
   - 위 폴더 구조대로 파일을 끌어다 놓습니다.
   - 웹에서 폴더를 만들려면 "Create new file" 후 파일명에
     `.github/workflows/build-apk.yml` 처럼 슬래시를 포함해 입력하면
     폴더가 자동 생성됩니다.

3. **Commit changes** 클릭

## 5. 빌드 실행 및 APK 받기

1. 업로드(커밋)하는 순간 **자동으로 빌드가 시작**됩니다.
2. 저장소 상단 **Actions** 탭 클릭
3. 실행 중인 워크플로(`Build APK`) 클릭
4. 빌드는 보통 **3~6분** 걸립니다.
   - 초록색 체크 = 성공
   - 빨간색 X = 실패 (클릭해서 로그 확인)
5. 성공하면 화면 아래쪽 **Artifacts** 항목에
   `callguard-debug-apk` 가 생깁니다. 클릭하면 zip 파일로 다운로드됩니다.
6. zip 압축을 풀면 `app-debug.apk` 가 들어 있습니다.

## 6. 폰에 설치하기

1. APK 파일을 안드로이드 폰으로 전송 (카톡 내게쓰기, USB, 구글 드라이브 등)
2. 파일 탭 -> "출처를 알 수 없는 앱 설치" 허용 요청이 뜨면 허용
3. 설치 완료

디버그 APK는 자동으로 서명되어 있어 별도 작업 없이 바로 설치됩니다.

---

## 7. (선택) 릴리즈용 서명 APK 만들기

Play 스토어에 올리거나 정식 배포하려면 직접 만든 키로 서명해야 합니다.

### 7-1. 키스토어 생성 (PC에 JDK가 있어야 함)

```
keytool -genkey -v -keystore release.keystore -alias callguard \
  -keyalg RSA -keysize 2048 -validity 10000
```

### 7-2. 키스토어를 base64로 변환

윈도우(PowerShell):
```
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) > keystore.txt
```

맥/리눅스:
```
base64 -i release.keystore -o keystore.txt
```

### 7-3. GitHub Secrets에 등록

저장소 -> **Settings** -> **Secrets and variables** -> **Actions** -> **New repository secret**

| Secret 이름 | 값 |
|---|---|
| `KEYSTORE_BASE64` | keystore.txt 의 내용 전체 |
| `KEYSTORE_PASSWORD` | 키스토어 만들 때 입력한 비밀번호 |
| `KEY_ALIAS` | callguard |
| `KEY_PASSWORD` | 키 비밀번호 |

### 7-4. app/build.gradle 에 서명 설정 추가

`android { }` 블록 안에 아래를 추가합니다.

```gradle
    signingConfigs {
        release {
            storeFile file("release.keystore")
            storePassword System.getenv("KEYSTORE_PASSWORD")
            keyAlias System.getenv("KEY_ALIAS")
            keyPassword System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            minifyEnabled false
            signingConfig signingConfigs.release
        }
    }
```

Secrets를 등록하지 않았다면 워크플로의 릴리즈 단계는 자동으로 건너뜁니다.
디버그 APK만 받아 쓰실 거면 7번 전체를 무시해도 됩니다.

---

## 8. 빌드 실패 시 자주 나오는 원인

| 오류 메시지 | 원인 / 해결 |
|---|---|
| `resource mipmap/ic_launcher not found` | 아이콘 파일 없음 -> 위 3번 방법 A 적용 |
| `resource string/app_name not found` | strings.xml 없음 -> 위 2번 참고 |
| `Could not find method ... signingConfigs` | 서명 설정 문법 오류 -> 7-4 내용 다시 확인 |
| `Unsupported class file major version` | JDK 버전 문제 -> 워크플로의 java-version 확인(17이어야 함) |
| `SDK location not found` | setup-android 액션이 빠짐 -> 워크플로 파일 확인 |
| 한글 주석 깨짐 | 파일을 UTF-8로 저장했는지 확인 |

실패했을 때는 Actions 탭 -> 실패한 실행 클릭 -> 빨간 X 표시된 단계를 펼치면
정확한 오류 메시지가 나옵니다. 그 메시지를 그대로 복사해서 질문하시면
원인을 찾아드릴 수 있습니다.

---

## 9. 참고: 무료 사용량

GitHub Actions는 Public 저장소는 무제한 무료,
Private 저장소도 월 2,000분 무료입니다.
이 앱 빌드는 1회당 3~6분 정도라 사실상 비용 걱정은 없습니다.
