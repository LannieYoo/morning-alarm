# 모닝콜 프로젝트 컨텍스트

캐나다의 엄마가 한국의 자취 대학생 딸을 아침에 깨우는 가족 알람 앱. 하나의 APK, 첫 실행 때 역할 선택(엄마=발신 / 자녀=수신).

## 핵심 설계 결정 (변경 시 주의)

- **통신**: Firebase Firestore + 익명 인증. FCM/Cloud Functions 없이 자녀 기기의 포그라운드 서비스(`SyncService`)가 실시간 리스너로 수신. 이유: 무료, 서버리스, 캐나다↔한국 국제 SMS 비용 회피
- **알람 실행**: 알람은 자녀 기기에 동기화(Prefs 캐시) 후 로컬 `AlarmManager.setAlarmClock`으로 울림 → 오프라인에도 동작. 시각은 항상 **자녀 기기 현지 시간** 기준
- **무음 뚫기**: `STREAM_ALARM` + TTS(`USAGE_ALARM`), 울릴 때 알람 볼륨 강제 최대(종료 시 복원). 무음·진동·통화 중에도 스피커로 남
- **반복 울림**: `repeatCount`회, `intervalMin`분 간격. `AlarmReceiver`가 다음 회차를 미리 예약하고, 질문 정답 시 `cancelRepeats` + 당일 정지 플래그(`stopped_{alarmId}` = 날짜)
- **그만 울리기**: 질문(최대 10개) 정답을 맞혀야 당일 종료. 오답이면 다음 질문 순환. 정답 비교는 trim+소문자+공백제거
- **긴급 팝업**: `Message.kind = urgent` → `UrgentActivity` 빨강 풀스크린 + 진동 + 1회 낭독
- **수신확인**: Message.deliveredAt(서비스가 기록)/readAt(확인 시). 알람은 events 컬렉션(firedAt/dismissedAt/answered/stoppedForDay)
- **자녀 화면은 조회 전용**: 알람 삭제·수정은 엄마만
- **표시 시간대**: 기본 Asia/Seoul, 상태 탭에서 기기 시간대로 전환(`TzState`)

## 공개 저장소 주의

- **실제 전화번호, google-services.json, 키 파일 절대 커밋 금지** (gitignore 등록됨)
- CI는 GitHub Secret `GOOGLE_SERVICES_JSON`에서 주입, 없으면 `google-services.json.example`(자리표시자)로 빌드만 통과

## 빌드

- 이 PC에는 Java/Android SDK 없음 → APK는 GitHub Actions(`.github/workflows/build-apk.yml`) → Actions 탭 Artifacts에서 다운로드
- 스택: Kotlin 2.0.20 / AGP 8.5.2 / Gradle 8.9 / Compose BOM 2024.09.03 / minSdk 26, target 34

## Firestore 컬렉션

`users/{phone}`(프로필+health), `pairRequests`, `alarms`, `events`, `messages` — 규칙은 `firestore.rules` (인증 필수). 쿼리는 등호 필터만 사용해 복합 인덱스 불필요 (orderBy 추가하지 말 것)

## 남은 일 / 미구현

- 사용자가 Firebase 프로젝트 생성 + Secret 등록 후 재빌드해야 실제 동작 (SETUP.md 참고)
- 실기기 검증 전: 삼성 절전 정책, TTS 한국어 엔진, API 34 전체화면 알림 권한은 SETUP.md 체크리스트로 안내
- 엄마 폰 알림 수신은 SyncService 살아있을 때만 (mom 역할은 BootReceiver 재시작 제외)
