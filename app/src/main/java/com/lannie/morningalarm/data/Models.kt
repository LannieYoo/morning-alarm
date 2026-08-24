package com.lannie.morningalarm.data

/** 그만 울리기용 질문/정답 쌍 (최대 10개) */
data class Question(var q: String = "", var a: String = "")

/** 엄마가 만드는 알람. 딸 기기에 동기화되어 로컬 AlarmManager로 예약된다. */
data class Alarm(
    var id: String = "",
    var ownerPhone: String = "",
    var targetPhone: String = "",
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
    var updatedAt: Long = 0L
)

/** 알람이 울릴 때마다 1건 기록. 딸이 반응하면 dismissedAt이 채워진다. */
data class AlarmEvent(
    var id: String = "",
    var alarmId: String = "",
    var alarmText: String = "",
    var ownerPhone: String = "",
    var targetPhone: String = "",
    /** alarm | test */
    var type: String = "alarm",
    /** 0부터 시작하는 회차 */
    var ringIndex: Int = 0,
    var firedAt: Long = 0L,
    var dismissedAt: Long = 0L,
    /** 질문 정답을 맞혔는지 */
    var answered: Boolean = false,
    /** 오늘 하루 종료 처리됐는지 */
    var stoppedForDay: Boolean = false
)

/** 채팅/긴급팝업/테스트 메시지 */
data class Message(
    var id: String = "",
    var fromPhone: String = "",
    var toPhone: String = "",
    var text: String = "",
    /** chat | urgent | test_alarm */
    var kind: String = "chat",
    var sentAt: Long = 0L,
    var deliveredAt: Long = 0L,
    var readAt: Long = 0L
)

/** 발신자(엄마) → 수신자(딸) 연결 요청 */
data class PairRequest(
    var id: String = "",
    var fromPhone: String = "",
    var fromName: String = "",
    var toPhone: String = "",
    /** pending | accepted | rejected */
    var status: String = "pending",
    var createdAt: Long = 0L
)

object Kind {
    const val CHAT = "chat"
    const val URGENT = "urgent"
    const val TEST_ALARM = "test_alarm"
}
