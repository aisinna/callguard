# 서로지킴 (CallGuard) — 개발 인수인계 문서

> 새 채팅에서 이 파일을 먼저 읽히면 바로 이어서 개발할 수 있습니다.

## 1. 앱 개요
고령의 부모님 등 보호 대상자의 **보이스피싱 피해를 막는 Android 앱**.
통화가 설정 시간을 넘기면 본인에게 경고하고, 보호자에게 자동으로 문자를 보낸다.

| 단계 | 기본값 | 동작 |
|---|---|---|
| 1차 | 8분 | 알림 + 진동 + 경고음 (본인에게만) |
| 2차 | 10분 | 풀스크린 경고 화면 + 강한 경고음 + **보호자 전원에게 SMS** |
| 통화 종료 | - | 예약된 알람 전부 취소 |

- 보호자 번호(최대 3명)와 사용자가 지정한 예외 번호는 감시에서 제외 (화이트리스트)
- 경고 화면에서 "경고 해제"를 누르면 보호자에게 "안심하세요" 문자 발송
- 앱 표시 이름: **서로지킴** / 패키지: `com.parentcare.callguard`

## 2. 기술 스택
- Kotlin 1.9.23, AGP 8.4.0, Gradle 8.7 (CI에서 직접 다운로드), JDK 17
- minSdk 26 / targetSdk·compileSdk 34
- View 기반 UI (XML + findViewById; viewBinding은 켜져 있으나 미사용), AppCompat/Material
- 외부 서버·DB 없음. 설정은 SharedPreferences(`call_guard_prefs`)
- **Gradle Wrapper 없음** → 로컬 빌드는 Android Studio가 자동 처리, CI는 gradle을 직접 설치

## 3. 파일 구조 (`app/src/main/java/com/parentcare/callguard/`)
| 파일 | 역할 |
|---|---|
| `MainActivity` | 설정 UI, 권한 안내/요청, 시작·중지, 상태 표시, 테스트 버튼 |
| `CallStateReceiver` | `PHONE_STATE` 수신. 직전 상태(Prefs 저장)로 수신/발신 구분. RINGING→번호 저장, OFFHOOK→알람 예약, IDLE→알람 취소·번호 초기화 |
| `AlarmScheduler` | 1·2차 알람 예약/취소 (정확 알람 → 권한 없으면 `setAlarmClock` 대체) |
| `AlarmReceiver` | 알람 도착 시 1차: 알림 / 2차: 경고 + SMS |
| `NotificationHelper` | 알림 채널(`*_v3`), 1·2차 알림, 풀스크린 인텐트, 경고 화면 직접 실행 |
| `AlertPlayer` | 진동 + ToneGenerator + 알람 벨소리를 직접 재생 (채널 설정과 무관) |
| `SmsHelper` | 보호자 문자(경고/안심/테스트), 발송 결과 알림 |
| `WarningActivity` | 빨간 풀스크린 경고 화면(잠금 화면 등에서 전체 화면 알림으로 뜰 때). 로직은 `WarningUi` 사용 |
| `WarningOverlayService` | 2차 경고를 **즉시** 화면 위에 덮어 띄우는 오버레이 창 소유 서비스 (`다른 앱 위에 표시` 권한). 통화 종료(IDLE) 시 자동 종료 |
| `WarningUi` | 경고 화면·오버레이 공용 문구/버튼 로직. '전화 끊기'는 `TelecomManager.showInCallScreen` 으로 통화 화면을 앞으로 가져옴 |
| `StatusNotifier` | "동작 중" 상시 알림 |
| `PrefsHelper` | 설정 저장소 + 화이트리스트 판정 + 시간 포맷 |
| `BootReceiver` | 재부팅 시 저장된 통화 상태를 IDLE 로 초기화 |

## 4. 빌드 / 배포
- 푸시(main/master) 또는 수동 실행 시 `.github/workflows/build-apk.yml` 이 디버그 APK를 빌드해 아티팩트(`callguard-debug-apk`)로 업로드
- 시크릿(`KEYSTORE_BASE64` 등)이 있으면 릴리스 빌드도 수행 — 단, 아래 "알려진 이슈 3" 참고
- 로컬: Android Studio에서 폴더를 열고 Run, 또는 `gradle assembleDebug`
- 설치는 사이드로드(APK) 전제. `SEND_SMS`는 Google Play 정책상 제한 권한이라 스토어 배포 시 별도 검토 필요

## 5. 필요한 권한 (사용자가 직접 켜야 하는 것 포함)
런타임: `READ_PHONE_STATE`, `READ_CONTACTS`(연락처 번호도 심사 대상에 포함), `POST_NOTIFICATIONS`(13+)
  — v1.5 에서 `READ_CALL_LOG`·`SEND_SMS` 제거(아래 'v1.5 구현' 참고)
역할: 발신자 정보 및 스팸 방지 앱(`ROLE_CALL_SCREENING`, API 29+)
특수: 다른 앱 위에 표시(오버레이), 알람 및 리마인더(정확 알람), 배터리 최적화 제외, 전체 화면 알림(14+)

## 6. 코드 리뷰에서 발견한 알려진 이슈 (우선순위순)

1. ~~**[높음] 수신 번호가 Android 9+ 에서 null**~~ ✅ **수정됨** — `EXTRA_INCOMING_NUMBER`는 `READ_CALL_LOG` 권한이 없으면 오지 않는다.
   → 번호가 항상 "알수없음"이 되어 화이트리스트(보호자 통화 제외)가 사실상 동작하지 않을 수 있음.
   해결: `READ_CALL_LOG` 추가 + 런타임 요청 (또는 `CallScreeningService` 사용 검토).
2. ~~**[높음] 발신 통화 처리 부정확**~~ ✅ **수정됨** — OFFHOOK 시 `getLastNumber()`를 쓰므로, 내가 건 전화는 *이전에 받은 전화 번호*로 판정됨.
   → 발신 상태 구분(RINGING 없이 OFFHOOK) 시 lastNumber 초기화 필요. 발신 번호 확인은 `NEW_OUTGOING_CALL`(deprecated) 대신 CallLog/`CallScreeningService` 고려.
3. ~~**[중간] 릴리스 서명 미설정**~~ ✅ **수정됨(아래 '릴리스 서명' 참고)** — `app/build.gradle`에 `signingConfigs`가 없어, CI가 keystore를 복호화해도 `assembleRelease` 결과는 서명되지 않은 APK.
4. ~~**[중간] 런처 아이콘 없음**~~ ✅ **수정됨(런처 아이콘 + 알림 작은 아이콘)** — manifest에 `android:icon` 미지정(기본 아이콘). 상태바 아이콘도 시스템 기본(`ic_dialog_alert`) 사용.
5. **[중간] "보호자에게 이미 알림을 보냈습니다" 문구가 항상 표시** — SMS 실패/권한 없음이어도 표시됨. 실제 발송 결과를 반영해야 함.
6. **[중간] `allowBackup="true"`** — 보호자 번호 등 개인정보가 백업에 포함됨. `false` 권장.
7. ~~**[낮음] `CallStateReceiver.lastState`가 static 변수**~~ ✅ **수정됨(1·2번과 함께)** — 프로세스가 죽으면 초기화되어 중복/누락 이벤트 가능. 상태를 Prefs에 저장하는 방안 검토.
8. **[낮음] 화이트리스트 `endsWith` 매칭** — 짧은 번호를 넣으면 의도치 않게 넓게 매칭됨(최소 자릿수 검증 필요).
9. **[낮음] 통화 중 경고음 재생 보장 불명확** — ToneGenerator/Ringtone이 통화 중 실제로 들리는지는 기기별 실측 필요.
10. **[낮음] 전화 끊기 버튼은 안내만 함** — 실제 종료는 `TelecomManager.endCall()`(API 28+, `ANSWER_PHONE_CALLS` 권한)로 구현 가능.
11. 정리: 미사용 viewBinding 설정/필드, `BootReceiver` 빈 훅, `kotlinOptions`/JDK 1.8 설정(JDK 17 CI와 무관하게 동작하나 통일 가능).

### 수정 내역 (이슈 1·2·7)
- **1번:** `READ_CALL_LOG` 선언 + 런타임 요청 + 권한 상태 표시 + 메인 화면 "④ 통화 기록 권한" 버튼
  ('다시 묻지 않음' 상태면 앱 설정 화면으로 안내). 권한이 없으면 번호를 모르는 채로 **감시는 계속**(안전 쪽으로 동작).
- **2번:** 직전 상태로 방향 판정(`RINGING→OFFHOOK`=수신, `IDLE→OFFHOOK`=발신). 발신 번호는 `NEW_OUTGOING_CALL`
  (`PROCESS_OUTGOING_CALLS`)로 받아 60초간 보관 후 사용. 통화 종료(IDLE) 시 번호를 지워 이전 통화 번호가 재사용되지 않음.
- **같은 원인의 추가 수정:**
  - RINGING 이 두 번 오는 기기에서 번호가 든 두 번째 브로드캐스트가 중복 제거로 버려지던 문제
  - `+82 10-…` 형식 수신 번호가 `010…` 보호자 번호와 매칭되지 않던 문제 (`PrefsHelper.normalizeNumber`)
  - 통화 상태를 Prefs 에 저장(7번) → 프로세스 종료·감시 OFF 중 통화·재부팅 후에도 판정 유지, 통화 대기(통화 중 2번째 벨)는 무시
- **한계/주의:**
  - 발신 통화는 Android 가 *다이얼 시작 시점*에 OFFHOOK 을 알리므로 타이머가 상대가 받기 전부터 시작됨
  - `NEW_OUTGOING_CALL` 은 API 29 부터 deprecated. 기기/OS 에 따라 번호가 안 올 수 있으며, 그 경우 번호 미확인(감시 유지)으로 처리
  - `READ_CALL_LOG`·`PROCESS_OUTGOING_CALLS` 는 Google Play 제한 권한 (사이드로드는 무관)
  - 실제 기기 검증 전. 컴파일 검증과 번호 정규화 단위 테스트만 수행함

### ⚠ 기기 보안 차단 이슈 (2026-09-28)
- `PROCESS_OUTGOING_CALLS` + `NEW_OUTGOING_CALL` 수신기를 추가한 빌드가 삼성 폰에서
  **"악성 앱으로 의심 — 설치할 수 없습니다(끌 수 없음)"** 으로 차단됨. 직전 빌드(해당 항목 없음)는 설치됐음.
  발신 통화 가로채기 계열 권한이 보이스피싱 악성앱 패턴과 겹치는 것으로 추정(원인 확정은 못 함).
- 조치: `PROCESS_OUTGOING_CALLS`·`NEW_OUTGOING_CALL` 제거 → ~~발신 통화는 번호 '모름'으로 감시~~ → **이후 '발신 통화는 감시하지 않음'으로 변경**(아래 '발신 통화 감시 제외' 참고).
  `PrefsHelper` 의 `*PendingOutgoing` 함수는 현재 미사용(삭제 가능).
- **그래도 차단되면** 다음 후보는 `READ_CALL_LOG`. 제거하면 수신 번호를 못 읽어 보호자 통화 제외가 동작하지 않으므로,
  대안으로 `CallScreeningService`(역할 기반, 사용자가 '발신자 정보 및 스팸 방지 앱'으로 지정) 검토.

### 경고 방식 변경 (2026-09-28)
- **진동:** 1차 = 강한 진동 2번("징징", 600ms×2), 2차 = 4번×3라운드("징징징징", 약 8.7초). 최대 세기(255) + 알람 용도 지정.
  알림 채널의 진동은 껐다(채널 v4) — 앱 직접 진동과 겹쳐 서로 덮어쓰는 것을 방지. 진동 세기는 기기의 '진동 세기' 설정에도 영향받음.
- **2차 즉시 표시:** 화면이 켜지고 잠금이 풀린 상태에선 전체 화면 알림(fullScreenIntent)이 '상단 알림'으로만 표시되는 안드로이드 동작이 원인.
  → `WarningOverlayService` 가 오버레이 창을 직접 띄움. 잠금 화면일 때는 기존 전체 화면 알림(`WarningActivity`)이 처리.
  오버레이·Activity 가 동시에 뜨지 않도록 `WarningActivity` 시작 시 오버레이를 닫음.
- **전화 끊기 버튼:** 홈 화면 대신 `showInCallScreen`(통화 중) → 실패/통화 아님이면 다이얼러 → 홈 순으로 대체. 새 권한 없음(READ_PHONE_STATE 사용).
- **미검증(실기기 확인 필요):** 오버레이 표시 타이밍, 삼성 통화 화면 전환, 화면 꺼진 상태(근접센서)에서의 표시.
  Android 14+ 에서 잠금 화면 전체 화면 알림은 '전체 화면 알림' 특별 권한이 필요할 수 있음(설정 > 앱 > 특별한 접근).

### 릴리스 서명 (2026-09-28)
- `app/build.gradle` 이 환경변수 `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` 와 `app/release.keystore` 파일이 **모두** 있을 때만 서명한다.
  하나라도 없으면 서명 없이 빌드(디버그·포크 PR 영향 없음).
- 워크플로: `KEYSTORE_BASE64` 시크릿이 있으면 → 시크릿 3종 검사 → 키스토어 복원 → `assembleRelease` → **서명 검증(apksigner) + SHA-256 요약** → 업로드.
  서명이 안 된 APK(`*-unsigned.apk`)가 만들어지면 워크플로가 실패한다.
- GitHub Secrets 4개: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
- 키스토어 생성: `keytool -genkeypair -v -keystore release.keystore -alias callguard -keyalg RSA -keysize 2048 -validity 10000`
  (JDK 9+ 기본 형식이 PKCS12 라 **키 비밀번호는 스토어 비밀번호와 항상 같다** → `KEY_PASSWORD` = `KEYSTORE_PASSWORD`)
- ⚠ 키스토어·비밀번호를 잃어버리면 같은 앱의 업데이트를 배포할 수 없다. 저장소에 절대 커밋 금지(`.gitignore` 등록됨), 별도 백업 필수.
- ⚠ 디버그 서명으로 설치한 기기에는 릴리스 APK 를 덮어 설치할 수 없다(서명 불일치) → 기존 앱 삭제 후 설치(설정값 초기화됨).

### 앱 아이콘 (2026-09-28)
- 방패 + 맞잡은 두 손 + 전화 로고. **적응형 아이콘**(minSdk 26 이라 PNG 폴백 불필요) + Android 13 **테마 아이콘(monochrome)** 지원.
- 파일: `res/drawable-nodpi/ic_launcher_{background,foreground,monochrome}.png`(각 432px = 108dp@xxxhdpi),
  `res/mipmap-anydpi-v26/ic_launcher.xml`·`ic_launcher_round.xml`, 매니페스트 `android:icon`/`android:roundIcon`.
- 배경 = 원본의 파란 그라데이션을 복원한 PNG, 전경 = 로고만 투명 배경으로 분리(외접원 지름 = 캔버스의 52%, 안전 영역 66dp 이내).
- `docs/icon-512.png` = 모서리 둥글림 없는 512px 정사각(Play 스토어/README/GitHub 소셜 미리보기용).
- **알림 작은 아이콘(상태바)** = `res/drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_stat_callguard.png` (24/36/48/72/96px, **흰색 + 알파 실루엣**).
  기존엔 시스템 아이콘을 썼다: 상시 알림 `ic_menu_call`(전화기 → 전화가 걸려온 것처럼 오해), 경고 `ic_dialog_alert`, 문자 결과 `ic_dialog_email`.
  로고 중앙의 작은 전화기 글리프는 **제거**했고(전화기로 오인 방지), 작은 크기에서 뭉개지지 않게 안쪽 틈을 2px 넓혔다(원본 512px 기준).
  사용처: `NotificationHelper`(2곳), `StatusNotifier`, `SmsHelper` 모두 `R.drawable.ic_stat_callguard`.
  알림 색상(`setColor`)은 미지정 → 알림창에서 아이콘 배경은 기기 기본 색.

### 알림 정리 · 문서 (2026-09-28)
- **문자 발송 결과 알림**: 전부 성공하면 알림을 띄우지 않는다("알림이 너무 많다"). **실패·미발송은 계속 알림**(보호자에게 경고가 안 닿았다는 뜻이므로).
  테스트 버튼만 성공 시 토스트로 알림. 주의: '성공'은 시스템에 발송을 요청했다는 뜻이지 도착 확인이 아님(sentIntent/deliveryIntent 미사용).
- `SmsHelper` 로그에서 보호자 전화번호 제거(버그 신고 때 로그 첨부해도 번호가 노출되지 않게).
- `android:allowBackup="false"` — 보호자 번호가 클라우드 백업으로 복사되지 않게(PRIVACY.md 의 "기기 밖으로 나가지 않음"과 일치시키기 위함).
- 문서 추가: `LICENSE`(MIT), `README.md`, `PRIVACY.md`, `THIRD_PARTY_NOTICES.md`(Apache-2.0 전문 포함, 의존성 바꾸면 갱신).
  빈칸이던 `LICENSE` 저작권자 이름(aisinna)과 `PRIVACY.md` 문의 이메일은 채움(2026-09-29). README 는 `docs/icon-512.png` 를 참조.
- 문서가 사실이려면 유지해야 하는 것: 앱에 INTERNET·RECORD_AUDIO 권한/분석 SDK 를 넣지 않는다. 넣게 되면 PRIVACY.md 를 먼저 고친다.

### ⚠ Android 개발자 인증 (2026-09-28 기준 공식 문서 확인, 문서 갱신일 2026-08-18)
- **2026-09-30**부터 브라질·인도네시아·싱가포르·태국에서 "등록된 개발자의 앱만 인증 기기에 설치" 적용. **2027년 이후 전 세계 확대**(한국 시점은 미발표) — 이 프로젝트는 그 전에 대비 필요.
- 방식 3가지: ① Full distribution(신원 인증 필요, 어디서든 배포) ② **Limited distribution(신원 인증 불필요, 최대 20대, 초대 수락 후 설치)** ③ 미등록 sideload(고급 절차 필요).
- 패키지명 등록은 **릴리스 서명 키로 서명한 APK** 로 소유를 증명 → **릴리스 키스토어 분실 금지**(위 '릴리스 서명' 참고). 패키지명 `com.parentcare.callguard` 도 함께 등록 대상.
- 가족·지인 소수에게만 배포하면 Limited distribution 이 맞고, 널리 배포할 계획이면 신원 인증(Android Developer Console)이 필요.
- 참고: https://developer.android.com/developer-verification (Android Developer Console)

### 릴리스 절차 (GitHub Actions 자동화, 2026-09-28)
1. (첫 릴리스 전 1회) `LICENSE` 저작권자 이름, `PRIVACY.md` 문의 이메일 채우기.
2. 새 버전이면 `app/build.gradle` 의 `versionCode`(정수, 반드시 증가)와 `versionName` 을 올리고 커밋. (첫 릴리스는 `versionName "1.0"` 그대로 → 태그 `v1.0`)
3. GitHub → **Actions → Build APK → Run workflow** → `release_tag` 에 `v1.0` 입력 → 실행.
   (터미널을 쓴다면 `git tag v1.0 && git push origin v1.0` 도 동일하게 동작)
4. 워크플로가 **서명된 APK + SHA-256 파일**이 붙은 릴리스 **초안(draft)** 을 만든다. 공개되지 않는다.
5. **Releases** 에서 초안을 열어 "변경 내용"을 채우고(필요하면 *Generate release notes* 버튼), 실기기 설치 확인 후 **Publish release**.
- 자동 검증: 태그 형식(`v숫자.숫자…`), 서명 키 존재, 태그 = `versionName`, 같은 태그 릴리스(초안 포함) 중복 → 하나라도 어긋나면 **빌드 전에** 실패.
- 같은 태그를 다시 만들려면 Releases 에서 기존 초안/릴리스를 먼저 삭제. (초안은 공개 전까지 태그가 만들어지지 않음 — Publish 시점에 대상 커밋에 태그가 생김)
- 릴리스 초안 생성에는 `GITHUB_TOKEN` 의 `contents: write` 만 사용. 서드파티 릴리스 액션 없이 GitHub CLI(`gh`)만 쓴다(공급망 위험 최소화).
- 검증 한계: 이 문서 작성 환경에서는 실제 GitHub 를 호출할 수 없어, 스크립트 로직만 가짜 `gh` 로 검증했다. 첫 실행 로그는 확인할 것.

### 발신 통화 감시 제외 (2026-09-28)
- 요구: **내가 건 전화는 감시에서 제외.** `CallStateReceiver` 의 `OFFHOOK` 처리에서 `RINGING` 을 거치지 않은 통화(=발신)는 알람을 예약하지 않고 return.
  발신 시작 시 `AlarmScheduler.cancelAll` 로 남은 알람을 정리하고 번호도 지운다.
- 방향 판정 원리: `RINGING → OFFHOOK` = 수신, `IDLE → OFFHOOK` = 발신. 직전 상태는 Prefs 에 저장(프로세스가 죽어도 유지), 재부팅 시 `BootReceiver` 가 초기화.
- ⚠ **트레이드오프**: 상대가 알려준 번호로 사용자가 다시 걸도록 유도하는 수법(콜백 유도)은 감시되지 않는다. README '알려진 한계'에 명시함.
  필요하면 설정 스위치("내가 건 전화도 감시", 기본 꺼짐)로 확장 가능.
- ⚠ **오분류 위험**: 어떤 기기·통신 방식(VoLTE/Wi-Fi 통화 등)에서 수신인데 `RINGING` 브로드캐스트가 오지 않으면 발신으로 오판해 **조용히 감시가 빠진다.** 이전에는 발신도 감시해서 오판이 무해했음.
  → 실기기에서 "모르는 번호로 걸려 온 전화 → 10분 뒤 경고"가 실제로 되는지 반드시 확인.
- 정리 후보(미사용 코드): `PrefsHelper` 의 `setPendingOutgoing/takePendingOutgoing/clearPendingOutgoing`, `KEY_PENDING_OUT_*`.
- **검증 방법(재사용 가능)**: 안드로이드 부품(Context/Intent/SharedPreferences/Log/TelephonyManager 상수)을 메모리 가짜로 대체하고, 실제 `CallStateReceiver`·`PrefsHelper` 를
  통화 시나리오 15개(수신/발신/보호자/예외/통화대기/부재중/감시 ON·OFF/프로세스 재시작/재부팅)로 실행 → 수정 후 전부 통과, **수정 전 코드에선 발신 관련 9개 실패**로 테스트가 버그를 잡는 것도 확인.
  참고: 스텁 android.jar 에서는 `TelephonyManager.EXTRA_STATE_RINGING` 등이 null 이라 가짜로 대체해야 함(실기기에서는 "RINGING" 등 정상 문자열).

### v1.5 구현 (2026-09-29) — 구글 플레이 대응 착수

설계안(Claude Doc "서로지킴 v1.5 — 구글 플레이 등록 설계안")대로 코드에 반영했다. 컴파일은 실제
android.jar(API 33/34, GitHub 미러에서 받음) 기준으로 kotlinc 로 전체 소스를 검증했고(오류 0),
`CallStateReceiver` 의 통화 상태 판정 로직은 이전 15개 시나리오 중 핵심 6개를 메모리 페이크(Context·
SharedPreferences·TelephonyManager 상수 흉내)로 재실행해 회귀가 없음을 확인했다. **실기기·실제
Gradle/AGP 빌드는 이 환경에서 확인 불가** — 아래 "미검증" 항목 참고.

**1) 통화 상대 번호: `READ_CALL_LOG` 제거 → `CallGuardScreeningService`**
- 새 파일 `CallGuardScreeningService.kt`: `CallScreeningService.onScreenCall()` 에서 `Call.Details.handle`
  로 번호를 받아 `PrefsHelper.setLastNumber()` 에 저장. 전화를 막지도 거절하지도 않음(`respondToCall` 을
  항상 "허용"으로 응답).
- 매니페스트에 서비스 등록 + `BIND_SCREENING_SERVICE` + `android.telecom.CallScreeningService` 인텐트
  필터 추가.
- `MainActivity`: `RoleManager.ROLE_CALL_SCREENING` 요청 플로우로 교체(④ 버튼). API 29(Q) 미만은 역할
  자체가 없어 기능을 숨기고 "지원 안 함" 문구만 표시 — 이 기기들은 상대 번호를 영영 알 수 없고, 모든
  수신 통화가 동일하게 감시된다(보호자 통화 제외 불가).
- `CallStateReceiver`: RINGING 에서 `EXTRA_INCOMING_NUMBER` 를 더 이상 읽지 않는다(권한 없이는 항상
  비어 있음). 번호는 오직 `CallGuardScreeningService` 가 채운다.

**2) 보호자 문자: `SEND_SMS` 제거 → 문자 작성 화면(탭-발송)**
- `SmsHelper` 전면 재작성: `SmsManager` 를 걷어내고 `Intent.ACTION_SENDTO`(`smsto:`) 로 수신자·내용을
  채운 문자 작성 화면만 연다. 여러 보호자는 `;` 로 나열(제조사 문자 앱에 따라 첫 번째만 채워질 수 있음 —
  그래도 문자 자체는 작성됨).
- `AlarmReceiver` 의 2차 경고 시 자동 발송 호출을 제거. 발송은 오직 경고 화면의 버튼 탭으로만 일어난다.
- `WarningUi` 에 "보호자에게 문자 보내기" 버튼 추가(`activity_warning.xml`). "경고 해제" 버튼도 자동
  발송 대신 작성 화면을 연다.
- ⚠ **발견한 제약**: 2차 경고를 백그라운드(AlarmReceiver)에서 처리하는 시점에 SMS 작성 화면을
  "자동으로" 띄우는 것은 안드로이드 10+ 의 백그라운드 액티비티 시작 제한에 걸려 대부분 막힌다.
  그래서 설계안의 "탭 한 번만 남기는" 표현을 "경고 화면의 버튼을 한 번 탭하면 작성 화면이 열리고,
  거기서 보내기를 한 번 더 누른다"로 수정해 구현했다(총 두 번 탭). 자동으로 열리는 것은 경고
  화면/오버레이 자체뿐이다(fullScreenIntent 예외로 허용됨).
- 오버레이(`WarningOverlayService`)에서 버튼을 눌러 문자 앱 등 다른 액티비티를 띄울 때는 **오버레이를
  먼저 닫고(`onDismiss()` 먼저 호출) 나서** 액티비티를 시작하도록 순서를 바꿨다 — `TYPE_APPLICATION_OVERLAY`
  창이 새 화면 위에 계속 떠 있으면 터치가 막힐 수 있기 때문(기존 "전화 끊기" 버튼도 같은 문제가 있어 함께 고쳤다).

**3) 포그라운드 서비스 정비**
- `WarningOverlayService` 를 정식 포그라운드 서비스로 전환(`startForegroundService` +
  `startForeground(..., ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)`, API 34 미만은 타입 없이 호출).
  낮은 우선순위 알림 채널(`call_guard_overlay_fg`)을 하나 새로 만든다(2차 경고 알림과는 별개).
- 매니페스트에 `FOREGROUND_SERVICE`·`FOREGROUND_SERVICE_SPECIAL_USE` 권한과
  `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` 선언 추가.

**4) 온보딩 동의 화면**
- `MainActivity.ensureConsentThenRequestPermissions()`: 최초 실행 시 권한을 요청하기 전에 이 앱이
  무엇을 하는지 설명하는 대화상자를 먼저 보여주고, "동의하고 시작"을 눌러야 다음으로 진행한다.
  `PrefsHelper.isConsentGiven`/`setConsentGiven` 으로 1회만 표시.

**5) 정리**
- `PrefsHelper` 의 미사용 발신 번호 보관 함수(`*PendingOutgoing`) 제거, 같은 자리에 동의 여부 저장 함수 추가.
- `app/build.gradle`: `versionCode 2`, `versionName "1.5"`.

**미검증 (다음에 반드시 확인)**
- ⚠ **`compileSdk`/`targetSdk` 는 34 그대로 두었다.** 구글 플레이 신규 앱 제출은 2026-08-31부터
  targetSdk 36(Android 16) 을 요구하지만, compileSdk 36 은 AGP 9.0 이상이 필요하고 AGP 9.0 은 지금 쓰는
  별도 Kotlin 플러그인(`org.jetbrains.kotlin.android`)과 호환되지 않는 새 DSL 을 쓴다(내장 Kotlin 지원으로
  옮겨야 함). 이 환경은 실제 Gradle/AGP/Android SDK 를 내려받을 수 없어(Maven 저장소 접근 불가) 이
  마이그레이션을 검증하지 못했다. **Android Studio 의 "AGP Upgrade Assistant" 로 진행할 것.** 이거 없이는
  스토어 제출 자체가 안 된다 — 다음 작업 최우선 순위.
- 실기기에서 확인 필요: ① `CallGuardScreeningService` 가 갤럭시에서 역할 요청 팝업을 실제로 띄우는지
  (설계안에서부터 우려했던 지점), ② 역할을 줬을 때 정말 `onScreenCall` 이 불리고 번호가 들어오는지,
  ③ 포그라운드 서비스 전환 후에도 오버레이가 여전히 즉시 뜨는지, ④ 오버레이에서 문자 앱 버튼을 눌렀을 때
  오버레이가 먼저 사라지고 문자 앱이 정상적으로 조작되는지, ⑤ 온보딩 동의 대화상자 문구·흐름.
- 이 릴리스부터 `READ_CALL_LOG`/`SEND_SMS` 를 요청하지 않으므로, 기존 사이드로드 사용자가 업데이트하면
  권한 승인 팝업이 다시 뜨거나 최초 1회 동의 화면을 다시 보게 된다 — 정상 동작이다.

### 실기기 테스트 결과 · 연락처 번호 수정 (2026-09-29)
- **확인됨(갤럭시)**: 발신자 정보 앱 역할 `[O]`, **연락처에 없는 번호**로 걸면 번호가 정상으로 잡힘.
- **버그**: **연락처에 저장된 번호**로 걸면 번호가 "알수없음". 원인은 안드로이드 사양 —
  `ROLE_CALL_SCREENING` 앱에는 연락처에 없는 번호의 전화만 `onScreenCall` 로 넘어오고, 앱이 `READ_CONTACTS`
  를 받았을 때만 연락처 번호도 넘어온다(developer.android.com "Screen calls"). 보호자는 대부분 연락처에
  있으므로 **보호자 통화 제외(화이트리스트)가 사실상 동작하지 않던 문제**와 같은 원인이다.
- **수정**: `READ_CONTACTS` 선언 + 최초 실행 런타임 요청(`READ_PHONE_STATE` 와 함께) + 권한 상태 줄
  "연락처" 추가(없으면 설정 경로 안내) + 온보딩 문구에 용도 명시. 연락처 내용은 코드에서 읽지 않는다
  (시스템이 심사 범위를 넓히는 조건으로만 쓰임). README·PRIVACY 권한 설명 갱신.
  - 기존에 동의한 사용자는 온보딩 대화상자를 다시 보지 않고, 다음 실행 때 연락처 권한 팝업만 뜬다.
  - Play 데이터 보안 설문: 연락처 권한 보유를 적되 "수집·전송 안 함"으로 답할 수 있어야 한다(코드가 읽지 않으므로).
- **잠금 화면 → 문자 화면**: 잠금 상태에서 2차 경고의 "문자 보내기"를 누르면 경고 화면이 사라지고 잠금해제를
  요구함. 다른 앱(문자 앱)은 보안 잠금 위에 뜰 수 없는 안드로이드 제약이라 잠금해제 자체는 없앨 수 없다.
  → `WarningUi.openAfterUnlock()`: 잠겨 있으면 경고 화면을 띄운 채 `KeyguardManager.requestDismissKeyguard()`
  로 잠금해제 창을 먼저 부르고, **성공하면** 경고를 닫고 문자 작성 화면(또는 통화 화면)을 연다. 취소하면 경고
  화면이 남고, 오류면 예전 방식(바로 열기)으로 대체. 세 버튼 모두 적용. 잠금 방식이 없음/밀어서 열기면 즉시 통과.
- **연락처 번호 감시 제외 (요청 반영)**: 연락처에 저장된 번호에서 온 전화는 감시하지 않는다(설정 체크박스
  `cb_skip_contacts`, `PrefsHelper.isSkipContacts`, **기본 켜짐**). 판정은 `ContactLookup.isSavedContact()` —
  `ContactsContract.PhoneLookup` 으로 `_ID` 만 조회(번호 형식 차이는 시스템이 처리), 권한 없음·오류면 '연락처 아님'(감시 유지).
  `CallStateReceiver` 의 OFFHOOK 에서 화이트리스트 다음에 검사.
  - ⚠ 트레이드오프: 사기범이 알려준 번호를 피해자가 연락처에 저장한 경우(수사기관·금융기관 사칭에서 흔한 유도)
    감시가 빠진다. 설정 화면 안내 문구와 README '알려진 한계'에 명시.
  - 앞서 "연락처 내용을 읽지 않는다"고 쓴 문서 표현을 "저장 여부만 확인, 이름 등은 읽지 않음"으로 바꿈.
    Play 데이터 보안 설문: 연락처 데이터에 **접근**하지만 기기 밖으로 **수집·전송하지 않음**.
- **미확인(실기기)**: ⓪ 연락처 번호로 걸었을 때 감시가 빠지는지, 체크를 끄면 감시되는지. ① 연락처 권한 허용 후 저장된 번호로 걸었을 때 번호가 잡히는지, 보호자 번호면 경고가
  안 뜨는지. ② 잠금 상태에서 문자 버튼 → 잠금해제 창 → 해제 후 문자 작성 화면이 자동으로 열리는지(지문·얼굴·PIN
  각각), 취소 시 경고 화면이 남는지.

## 7. 개발 아이디어 (선택)
- 보호자 측 "역방향" 알림(서로 지킴 컨셉 강화) — 현재는 문자 기반 단방향
- 의심 키워드/번호 DB 기반 즉시 경고, 통화 기록 화면
- 설정 UI를 Material 컴포넌트/설정 검증 강화, 다국어 문자열 리소스화(현재 하드코딩 한글)
- 단위 테스트 (`PrefsHelper.isWhitelisted`, `formatSeconds`, 시간 검증 로직)

## 8. 새 채팅에서 이어가는 방법
1. 이 프로젝트 zip을 업로드하고 "DEVELOPMENT.md 읽고 이어서 개발하자"라고 요청
2. 하고 싶은 작업(예: "이슈 1, 2 먼저 수정")을 지정
3. 이 환경에서는 SDK/Gradle 서버 접근이 제한되어 **빌드 검증이 불가**하므로, 수정 후 GitHub에 푸시해 Actions 결과로 확인
