package com.lannie.morningalarm.data

/** 그만 울리기용 질문/정답 쌍 (최대 10개) */
data class Question(var q: String = "", var a: String = "")

/** 연결된 상대 (연락처). 전화번호가 ID. */
data class Contact(var phone: String = "", var name: String = "")

/**
 * 알람. 보낸 사람(owner)이 만들고 받는 사람(target) 기기에 동기화되어 로컬 AlarmManager로 예약된다.
 * 누구나 서로에게 보낼 수 있다 (양방향).
 */
data class Alarm(
    var id: String = "",
    var ownerPhone: String = "",
    var ownerName: String = "",
    var targetPhone: String = "",
    var targetName: String = "",
    /** 울릴 때 TTS가 반복해서 읽어주는 문장 */
    var text: String = "",
    var hour: Int = 7,
    var minute: Int = 0,
    /** ISO 요일 1=월 .. 7=일. 비어 있으면 매일 반복 */
    var days: List<Int> = emptyList(),
    var enabled: Boolean = true,
    /** 총 울림 횟수 (1이면 반복 없음) */
    var repeatCount: Int = 3,
    /** 반복 간격(분) */
    var intervalMin: Int = 5,
    var questions: List<Question> = emptyList(),
    /** force(무조건 소리, 기본) | follow(폰 설정 따름) */
    var soundMode: String = SoundMode.FORCE,
    var updatedAt: Long = 0L
)

/** 울림 방식 */
object SoundMode {
    /** 무음·진동 모드여도 알람 볼륨 최대로 낭독 (기본, 추천) */
    const val FORCE = "force"

    /** 폰 설정 따름: 소리 모드면 현재 볼륨으로 낭독, 진동 모드면 진동만, 무음이면 화면만 */
    const val FOLLOW = "follow"

    fun label(mode: String): String = if (mode == FOLLOW) "📱 폰 설정 따름" else "🔊 무조건 소리"
}

/** 알람이 울릴 때(또는 거절될 때)마다 1건 기록. 받는 사람이 반응하면 dismissedAt이 채워진다. */
data class AlarmEvent(
    var id: String = "",
    var alarmId: String = "",
    var alarmText: String = "",
    var ownerPhone: String = "",
    var ownerName: String = "",
    var targetPhone: String = "",
    var targetName: String = "",
    /** alarm | test */
    var type: String = "alarm",
    /** 0부터 시작하는 회차 */
    var ringIndex: Int = 0,
    var firedAt: Long = 0L,
    var dismissedAt: Long = 0L,
    /** 질문 정답을 맞혔는지 */
    var answered: Boolean = false,
    /** 오늘 하루 종료 처리됐는지 */
    var stoppedForDay: Boolean = false,
    /** 받는 사람의 알람 거절 시간이라 울리지 않음 */
    var rejected: Boolean = false,
    /** 거절 사유 표시용 (예: "수업시간 · 월~금 09:00~15:00") */
    var rejectReason: String = "",
    /** 받는 사람이 5분 전 예고에서 취소함 (firedAt = 원래 알람 시각) */
    var cancelled: Boolean = false
)

/** 채팅/긴급팝업/테스트 메시지 */
data class Message(
    var id: String = "",
    var fromPhone: String = "",
    var fromName: String = "",
    var toPhone: String = "",
    var text: String = "",
    /** chat | urgent | test_alarm | instant_alarm */
    var kind: String = "chat",
    /** 즉시/테스트 알람의 울림 방식 (SoundMode) */
    var soundMode: String = SoundMode.FORCE,
    var sentAt: Long = 0L,
    var deliveredAt: Long = 0L,
    var readAt: Long = 0L
)

/** 연결 요청. 수락되면 양쪽 모두의 연락처가 된다. */
data class PairRequest(
    var id: String = "",
    var fromPhone: String = "",
    var fromName: String = "",
    var toPhone: String = "",
    /** 수락한 쪽이 자기 이름을 채운다 */
    var toName: String = "",
    /** pending | accepted | rejected */
    var status: String = "pending",
    var createdAt: Long = 0L
)

/**
 * 알람 거절(방해금지) 시간 규칙. 각자 자기 폰에서 무제한 등록.
 * 시각은 규칙을 만든 사람 폰의 현지 시간 기준이며, 알람도 받는 사람 폰 현지 시간에 울리므로 그대로 비교한다.
 */
data class QuietRule(
    var id: String = "",
    /** ISO 요일 1=월 .. 7=일. 비어 있으면 매일 */
    var days: List<Int> = emptyList(),
    /** 시작 시각 (0시 기준 분, 0..1439) */
    var startMin: Int = 0,
    /** 종료 시각 (분). 시작보다 작으면 자정을 넘기는 구간 (예: 23:00~07:00) */
    var endMin: Int = 0,
    /** sleep | class | meeting | workout | other */
    var reason: String = QuietReason.SLEEP,
    /** 기타일 때 설명 */
    var note: String = ""
)

object QuietReason {
    const val SLEEP = "sleep"
    const val CLASS = "class"
    const val MEETING = "meeting"
    const val WORKOUT = "workout"
    const val OTHER = "other"

    val ALL = listOf(SLEEP, CLASS, MEETING, WORKOUT, OTHER)

    fun label(reason: String, note: String = ""): String = when (reason) {
        SLEEP -> "취침시간"
        CLASS -> "수업시간"
        MEETING -> "미팅시간"
        WORKOUT -> "운동시간"
        OTHER -> if (note.isBlank()) "기타" else "기타($note)"
        else -> reason
    }

    fun emoji(reason: String): String = when (reason) {
        SLEEP -> "😴"
        CLASS -> "📚"
        MEETING -> "💼"
        WORKOUT -> "🏃"
        else -> "⛔"
    }
}

object Kind {
    const val CHAT = "chat"
    const val URGENT = "urgent"
    const val TEST_ALARM = "test_alarm"

    /** 예약 없이 지금 바로 울리는 알람 (거절 시간은 적용됨) */
    const val INSTANT_ALARM = "instant_alarm"

    /** 채팅 목록/안 읽음 배지에서 제외할 종류 */
    fun isAlarmLike(kind: String): Boolean = kind == TEST_ALARM || kind == INSTANT_ALARM
}
