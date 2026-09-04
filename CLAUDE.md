# 모닝콜 프로젝트 컨텍스트

캐나다의 엄마와 한국의 자취 대학생 딸이 서로 아침을 깨워주는 가족 알람 앱. 하나의 APK, **역할 구분 없음** — 누구나 여러 명과 연결해 서로 알람·메시지를 보낸다 (2026-09-03 양방향으로 전환).

## 핵심 설계 결정 (변경 시 주의)

- **통신**: Firebase Firestore + 익명 인증. FCM/Cloud Functions 없이 모든 기기의 포그라운드 서비스(`SyncService`)가 실시간 리스너로 수신. 이유: 무료, 서버리스, 캐나다↔한국 국제 SMS 비용 회피
- **연락처**: `pairRequests`(status=accepted)를 from/to 두 방향으로 구독해 합친 것이 연락처(`Repo.listenContacts`). 수락한 쪽이 `toName`을 채운다. Prefs.contacts에 캐시
- **알람 실행**: 알람은 받는 기기에 동기화(Prefs 캐시) 후 로컬 `AlarmManager.setAlarmClock`으로 울림 → 오프라인에도 동작. 시각은 항상 **받는 기기 현지 시간** 기준. `scheduleAll`은 첫 회차만 갱신하고 진행 중 반복 회차는 건드리지 않음
- **알람 거절 시간(QuietRule)**: 각자 무제한 등록(요일+시작~종료, 자정 넘김 가능, 사유 sleep/class/meeting/workout/other). 로컬 Prefs에 저장 + `users/{me}.quietRules`에 업로드. 받는 쪽 `AlarmReceiver`/`SyncService`(테스트 알람)가 `Quiet.find`로 판정해 울리지 않고 events에 `rejected=true, rejectReason` 기록. 보내는 쪽은 알람 편집 화면에서 `Quiet.conflicts`로 즉시 경고, 모든 요일이 겹치면 저장 차단. **긴급 메시지는 예외로 항상 전달**
- **무음 뚫기**: `STREAM_ALARM` + TTS(`USAGE_ALARM`), 울릴 때 알람 볼륨 강제 최대(종료 시 복원). 무음·진동·통화 중에도 스피커로 남
- **울림 방식(`SoundMode`)**: 알람/즉시 알람마다 보내는 쪽이 선택. `force`(무조건 소리, **기본·강조**) / `follow`(폰 설정 따름: 소리 모드→현재 볼륨 낭독+진동, 진동 모드→진동만, 무음→화면만). `RingPlayerService`가 ringerMode로 판정
- **반복 울림**: `repeatCount`회, `intervalMin`분 간격. `AlarmReceiver`가 다음 회차를 미리 예약하고, 질문 정답 시 `cancelRepeats` + 당일 정지 플래그(`stopped_{alarmId}` = 날짜)
- **그만 울리기**: 질문(최대 10개) 정답을 맞혀야 당일 종료. 오답이면 다음 질문 순환. 정답 비교는 trim+소문자+공백제거
- **긴급 팝업**: `Message.kind = urgent` → `UrgentActivity` 빨강 풀스크린 + 진동 + 1회 낭독
- **수신확인**: Message.deliveredAt(서비스가 기록)/readAt(확인 시). 알람은 events 컬렉션(firedAt/dismissedAt/answered/stoppedForDay)
- **5분 전 예고(PreAlarmActivity)**: `AlarmScheduler`가 첫 회차와 함께 ringIndex=-1(PRE_INDEX) 예고를 예약. 깜빡이는 화면에서 "이번 알람 취소"(질문 있으면 정답 필수) → 오늘 종료 + `scheduleNextOccurrence(afterMillis=triggerAt)` + events에 `cancelled=true, firedAt=알람 시각`. 꺼짐/오늘 종료/거절 시간 예정이면 예고 안 함
- **회차 규칙**: 마지막 회차(ringIndex+1 >= repeatCount)는 일단 끄기 없음, 질문 있으면 정답 입력창이 바로 뜸. 이전 회차는 일단 끄기(정답 불필요)/오늘 끝(정답 필요)
- **보낸 사람 알림**: `Repo.listenEventChanges(owner)`(첫 스냅샷 제외, documentChanges) → SyncService가 정답/확인/일단 끔/취소/거절을 알림 (eventId+state로 1회)
- **알람 수정 감지**: SyncService가 `Prefs.alarmVersion`(updatedAt)과 비교해 바뀐 알람은 cancelAll + clearStopped → 당일 이미 울렸어도 새 시각에 다시 울림 (버그 수정 2026-09-04)
- **TTS 통일(`alarm/Tts.kt`)**: Google TTS 설치돼 있으면 그 엔진, 속도 0.95·pitch 1.0 고정, 오프라인 한국어 최고 품질 voice 선택 (기기마다 목소리가 달랐던 문제)
- **문장 선택형**: `util/Korean.kt` `alarmPresets(name)` (아/야 자동) + 기타 직접 입력. 즉시 알람(Kind.INSTANT_ALARM)도 같은 선택기
- **받은 알람은 조회 전용**: 알람 삭제·수정은 보낸 사람만. 알람/기록/울림 화면에 보낸 사람 이름(`ownerName`) 표시
- **표시 시간대**: 기본 Asia/Seoul, 상태 탭에서 기기 시간대로 전환(`TzState`)

## 공개 저장소 주의

- **실제 전화번호, google-services.json, 키 파일 절대 커밋 금지** (gitignore 등록됨)
- CI는 GitHub Secret `GOOGLE_SERVICES_JSON`에서 주입, 없으면 `google-services.json.example`(자리표시자)로 빌드만 통과

## 빌드

- 이 PC에는 Android SDK 없음 → APK는 GitHub Actions(`.github/workflows/build-apk.yml`) → Actions 탭 Artifacts에서 다운로드
- JDK 17: `C:\Program Files\Eclipse Adoptium\jdk-17.*` (PATH 미등록 — `lint.ps1`이 자동 탐색, 없으면 winget으로 설치 안내), ktlint jar: `~/.ktlint/ktlint.jar` (없으면 스크립트가 다운로드)
- **코드 수정 후 반드시 `.\lint.ps1` 실행** (자동 수정: `.\lint.ps1 -Fix`). CI의 `lint` job도 **같은 ktlint CLI 1.3.1**을 내려받아 실행 (gradle 플러그인 안 씀 — 버전 불일치로 CI만 실패한 적 있음)
- **단위 테스트**: `app/src/test` (JUnit4, 순수 로직만 — 전화번호 정규화·정답 비교·다음 울림 시각·요일 표시·거절 시간 판정). CI `build` job이 `testDebugUnitTest`를 APK 패키징 전에 실행. 로컬 SDK 설치 후에는 로컬에서도 돈다
- 스택: Kotlin 2.0.20 / AGP 8.5.2 / Gradle 8.9 / Compose BOM 2024.09.03 / minSdk 26, target 34

## Firestore 컬렉션

`users/{phone}`(프로필+health+quietRules), `pairRequests`(from/to/toName/status), `alarms`(owner/target 양방향), `events`(rejected/rejectReason 포함), `messages`(fromName 포함) — 규칙은 `firestore.rules` (인증 필수). 쿼리는 등호 필터만 사용해 복합 인덱스 불필요 (orderBy 추가하지 말 것)

## 남은 일 / 미구현

- Firebase 프로젝트 `morning-alarm-c2c62` 생성 완료(2026-08-23): Firestore(서울, default), 익명 인증 ON, rules 게시, app/google-services.json 배치, GitHub Secret 등록
- 실기기 검증 전: 삼성 절전 정책, TTS 한국어 엔진, API 34 전체화면 알림 권한은 SETUP.md 체크리스트로 안내
- 로컬 빌드 가능해짐(2026-09-03): Android SDK `C:\Android\sdk`(cmdline-tools, platform 34, build-tools 34.0.0), `local.properties`는 gitignore. `.\gradlew.bat compileDebugKotlin testDebugUnitTest`로 컴파일·테스트 확인 후 push
