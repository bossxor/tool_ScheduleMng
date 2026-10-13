# 하루결 추가 검증: 알림·저장 용량

검증일: 2026-10-13 (Asia/Seoul). 제품 코드 기준: `793991bc765606fe8b07e2b10d7f91a34bc2f37c`.
검증 목적은 앞서 제기한 세 문제의 재현이며 제품 코드는 수정하지 않았다.

## 결과

| 항목 | 결과 | 확인한 증거 |
|---|---|---|
| 기존 단위 테스트 | 87개, 실패·오류 0 | `gradlew.bat testDebugUnitTest`, BUILD SUCCESSFUL |
| 지연된 작업 예약 취소 | 결함 재현 | 변경 없는 작업의 OS 예약 개수 1 → 0 |
| 요약 차단 시 습관 알림 누락 | 결함 재현 | 습관 채널 허용·요약 채널 차단 상태에서 요약 예약만 존재, 게시 알림 0 |
| 20MiB 초과 저장 후 재실행 오류 | 결함 재현 | 21,777,420바이트 저장 성공, 다시 열면 ‘파일이 너무 커요’ |
| 작은 파일 재열기 대조군 | 정상 | 생성·저장·다시 열기 값 일치 |
| 요약 비활성화 대조군 | 정상 | 습관 예약 복구, 실기기 게시 알림 1개 |

추가 진단용 JUnit 3개는 실패·오류 0이다. 이 테스트는 현재 결함이 나타나는지 확인하는 테스트다.
진단 테스트 통과를 제품 정상 동작이나 수정 완료로 해석하면 안 된다.

## 1. 지연된 작업 알림

Galaxy S26 Ultra SM-S948N / Android 17의 별도 검증 앱에서 실제 AlarmManager를 사용했다.
한 작업을 가까운 미래 시각에 예약하고, 데이터는 그대로 둔 채 `rescheduleAll`의 `now` 인수에 예정 시각 1분 뒤를 주입했다.
시스템 시계를 변경하지 않았다. `dumpsys alarm`에서 해당 검증 패키지의 실제 OS 예약이 1개에서 0개로 바뀌었다.

원인: `ReminderScheduler`는 기존 예약을 모두 취소한 뒤 `taskAlarmPlans`를 호출하며, 이 함수는 `at <= now`를 제외한다.
따라서 OS가 아직 발송하지 않은 지연 예약도 데이터 변경에 따른 재예약에서 사라질 수 있다.
별도 JUnit에서도 같은 데이터의 계획이 예정 전 1개, 예정 후 0개임을 확인했다.

수정 방향: 변경 없는 기존 예약을 보존하고 변경·완료·삭제된 예약만 취소한다. 발송/미발송 상태와 재부팅 복구 규칙도 구분한다.
실제 Doze 상태를 만들고 정해진 시각의 지연 발송을 기다리는 시험은 하지 않았다. 이번 검증은 시각 주입과 실제 OS 예약 취소를 결합한 결정적 재현이다.

## 2. 요약 알림 채널 차단

검증 패키지에만 알림 권한을 부여하고 요약 채널을 IMPORTANCE_NONE, 습관 채널을 IMPORTANCE_HIGH로 설정했다.
요약과 습관 시각은 둘 다 20:30이었다. 앱 설정의 요약은 활성화했다.
`planAlarms`는 습관 예약을 제외하고 요약 예약만 반환했다. 실제 NotificationManager에 요약을 게시해도 활성 알림은 0개였다.
대조군으로 앱 설정의 요약을 끄면 습관 예약이 생기고 실제 습관 알림 1개가 게시됐다. 이후 검증 알림을 모두 제거했다.

원인: 같은 시각 병합은 앱의 `summary.enabled`만 확인하고 Android 채널 허용 상태를 확인하지 않는다.
수정 방향: 요약 채널이 실제 허용된 경우에만 개별 알림을 병합하고, 채널 차단 시 개별 예약을 유지한다.
시스템 설정 UI에서 차단하는 조작 자체는 하지 않았다. 동일한 차단 중요도를 검증 앱의 채널에 적용해 NotificationManager 동작을 확인했다.

## 3. 저장과 읽기의 용량 불일치

임시 디렉터리의 AppStore에 유효한 할 일 360개를 저장했다. 각 메모는 한글 ‘가’ 20,000자다.
모든 필드는 기존 검증을 통과했고 저장과 메모리 상태 갱신도 성공했다.
실제 파일 크기는 21,777,420바이트로 읽기 한도 20,971,520바이트를 초과했다.
`AppStore.open` 재호출은 StoreException으로 실패했으며 원인 메시지는 ‘파일이 너무 커요’였다.
오류 전후 파일 바이트는 동일했다. 원본이 삭제되는 문제는 아니지만 앱이 자체 저장 파일을 읽을 수 없게 되는 문제다.

원인: 읽기는 20MiB를 제한하지만 AppStore 쓰기는 직렬화 크기를 검사하지 않는다.
수정 방향: 원본 교체 전에 쓰기 크기도 같은 한도로 검사하고, 실패 시 기존 파일과 메모리 상태를 유지한다.

## 환경과 보호 범위

- Windows 11, JDK 17, Gradle 9.8.1. Android SDK Platform 37.0은 검증 빌드 과정에서 설치했다.
- 진단 앱 ID는 `com.bossxor.harugyeol.verification`이다. 제품의 저장소·알림 채널·서명 키를 사용하지 않는다.
- 진단 앱은 일반 Application과 자동 백업 제외 설정을 사용해 제품의 자동 예약 관찰자를 실행하지 않았다.
- 초기 APK는 패키지명 override가 적용되지 않아 기존 앱과 서명이 다르다는 설치 오류로 차단됐다. 기존 앱은 업데이트되지 않았다.
- finalizeDsl에서 별도 앱 ID를 적용하고 APK metadata를 확인한 뒤 설치했다.
- 검증 후 진단 앱을 제거했다. 원래 제품 앱은 versionName 0.1.1 / versionCode 3을 유지한다.
- 개인 기록을 열거나 수정하지 않았다. 진단 데이터는 합성 값과 JVM 임시 파일뿐이다.

## 재현 자료

진단 소스·별도 앱 구성·실기기 결과는 [verification/20261013](../verification/20261013)에 보관한다.
진단 소스는 기본 빌드·CI에 포함하지 않고 init script를 명시한 경우에만 사용한다.

```powershell
$env:ANDROID_HOME = 'C:\Android\Sdk'
.\gradlew.bat testDebugUnitTest
.\gradlew.bat -I verification/20261013/probe.init.gradle :app:testDebugUnitTest --tests com.bossxor.harugyeol.VerificationProbeTest :app:assembleDebug
```

SDK 경로는 실행 환경에 맞춰 설정한다. 실기기 재현 시 기존 제품 APK를 덮어 설치하지 않는다.

```powershell
$meta = Get-Content -Raw 'build\verification\app-artifacts\outputs\apk\debug\output-metadata.json' | ConvertFrom-Json
if ($meta.applicationId -ne 'com.bossxor.harugyeol.verification') { throw 'Incorrect verification package' }
adb install --user 0 build/verification/app-artifacts/outputs/apk/debug/app-debug.apk
if ($LASTEXITCODE -ne 0) { throw 'Install failed' }
try {
    adb shell pm grant --user 0 com.bossxor.harugyeol.verification android.permission.POST_NOTIFICATIONS
    if ($LASTEXITCODE -ne 0) { throw 'Notification permission failed' }
    adb shell am instrument -w --user 0 com.bossxor.harugyeol.verification/com.bossxor.harugyeol.verification.VerificationInstrumentation
} finally {
    adb uninstall com.bossxor.harugyeol.verification
}
```

INSTRUMENTATION 결과는 Activity.RESULT_OK(-1)와 ALL_CURRENT_DEFECT_ASSERTIONS_PASSED를 함께 확인한다.
시스템 시계·개인 앱 설정 변경이나 실제 절전 시나리오의 추가 시험은 이 진단 명령에 포함하지 않는다.
