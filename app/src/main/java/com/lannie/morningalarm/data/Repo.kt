package com.lannie.morningalarm.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Firestore 데이터 레이어.
 * 오프라인 캐시가 기본 활성화되어 있어 네트워크가 끊겨도 쓰기는 큐에 쌓였다가 동기화된다.
 */
object Repo {
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    suspend fun ensureAuth() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) auth.signInAnonymously().await()
    }

    fun isAuthed(): Boolean = FirebaseAuth.getInstance().currentUser != null

    // ---- 사용자 / 상태(헬스체크) ----

    fun upsertUser(phone: String, fields: Map<String, Any>) {
        db.collection("users").document(phone).set(fields, SetOptions.merge())
    }

    fun updateHealth(phone: String, health: Map<String, Any>) {
        db.collection("users").document(phone).set(mapOf("health" to health), SetOptions.merge())
    }

    fun listenUser(phone: String, onChange: (Map<String, Any>?) -> Unit): ListenerRegistration =
        db.collection("users").document(phone).addSnapshotListener { s, _ ->
            onChange(s?.data)
        }

    // ---- 페어링 ----

    fun sendPairRequest(fromPhone: String, fromName: String, toPhone: String) {
        val doc = db.collection("pairRequests").document()
        doc.set(
            PairRequest(
                id = doc.id, fromPhone = fromPhone, fromName = fromName,
                toPhone = toPhone, status = "pending", createdAt = System.currentTimeMillis(),
            )
        )
    }

    fun listenPairRequestsTo(phone: String, onChange: (List<PairRequest>) -> Unit): ListenerRegistration =
        db.collection("pairRequests")
            .whereEqualTo("toPhone", phone)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { s, _ ->
                if (s != null) onChange(s.documents.mapNotNull { d ->
                    d.toObject(PairRequest::class.java)?.apply { id = d.id }
                })
            }

    fun listenPairAccepted(fromPhone: String, onChange: (List<PairRequest>) -> Unit): ListenerRegistration =
        db.collection("pairRequests")
            .whereEqualTo("fromPhone", fromPhone)
            .whereEqualTo("status", "accepted")
            .addSnapshotListener { s, _ ->
                if (s != null) onChange(s.documents.mapNotNull { d ->
                    d.toObject(PairRequest::class.java)?.apply { id = d.id }
                })
            }

    fun setPairStatus(requestId: String, status: String) {
        db.collection("pairRequests").document(requestId).update("status", status)
    }

    // ---- 알람 ----

    fun saveAlarm(alarm: Alarm): String {
        val doc = if (alarm.id.isBlank()) db.collection("alarms").document()
        else db.collection("alarms").document(alarm.id)
        alarm.id = doc.id
        alarm.updatedAt = System.currentTimeMillis()
        doc.set(alarm)
        return doc.id
    }

    fun deleteAlarm(alarmId: String) {
        db.collection("alarms").document(alarmId).delete()
    }

    fun listenAlarmsFor(targetPhone: String, onChange: (List<Alarm>) -> Unit): ListenerRegistration =
        db.collection("alarms")
            .whereEqualTo("targetPhone", targetPhone)
            .addSnapshotListener { s, _ ->
                if (s != null) onChange(s.documents.mapNotNull { d ->
                    d.toObject(Alarm::class.java)?.apply { id = d.id }
                })
            }

    fun listenAlarmsOwned(ownerPhone: String, onChange: (List<Alarm>) -> Unit): ListenerRegistration =
        db.collection("alarms")
            .whereEqualTo("ownerPhone", ownerPhone)
            .addSnapshotListener { s, _ ->
                if (s != null) onChange(s.documents.mapNotNull { d ->
                    d.toObject(Alarm::class.java)?.apply { id = d.id }
                })
            }

    // ---- 알람 기록 ----

    fun newEventId(): String = db.collection("events").document().id

    fun createEvent(event: AlarmEvent) {
        val doc = if (event.id.isBlank()) db.collection("events").document()
        else db.collection("events").document(event.id)
        event.id = doc.id
        doc.set(event)
    }

    fun updateEvent(eventId: String, fields: Map<String, Any>) {
        db.collection("events").document(eventId).set(fields, SetOptions.merge())
    }

    fun listenEvents(ownerPhone: String, onChange: (List<AlarmEvent>) -> Unit): ListenerRegistration =
        db.collection("events")
            .whereEqualTo("ownerPhone", ownerPhone)
            .addSnapshotListener { s, _ ->
                if (s != null) onChange(
                    s.documents.mapNotNull { d ->
                        d.toObject(AlarmEvent::class.java)?.apply { id = d.id }
                    }.sortedByDescending { it.firedAt }
                )
            }

    // ---- 메시지 (채팅 / 긴급팝업 / 테스트) ----

    fun sendMessage(from: String, to: String, text: String, kind: String): String {
        val doc = db.collection("messages").document()
        doc.set(
            Message(
                id = doc.id, fromPhone = from, toPhone = to, text = text,
                kind = kind, sentAt = System.currentTimeMillis(),
            )
        )
        return doc.id
    }

    /** 두 사람 사이의 대화 전체 (클라이언트에서 정렬) */
    fun listenChat(me: String, peer: String, onChange: (List<Message>) -> Unit): ListenerRegistration =
        db.collection("messages")
            .whereIn("fromPhone", listOf(me, peer))
            .addSnapshotListener { s, _ ->
                if (s != null) onChange(
                    s.documents.mapNotNull { d -> d.toObject(Message::class.java)?.apply { id = d.id } }
                        .filter { (it.fromPhone == me && it.toPhone == peer) || (it.fromPhone == peer && it.toPhone == me) }
                        .sortedBy { it.sentAt }
                )
            }

    /** 나에게 온, 아직 전달 확인 안 된 메시지 (수신 기기 서비스가 구독) */
    fun listenUndelivered(me: String, onChange: (List<Message>) -> Unit): ListenerRegistration =
        db.collection("messages")
            .whereEqualTo("toPhone", me)
            .whereEqualTo("deliveredAt", 0L)
            .addSnapshotListener { s, _ ->
                if (s != null) onChange(s.documents.mapNotNull { d ->
                    d.toObject(Message::class.java)?.apply { id = d.id }
                })
            }

    fun markDelivered(messageId: String) {
        db.collection("messages").document(messageId)
            .set(mapOf("deliveredAt" to System.currentTimeMillis()), SetOptions.merge())
    }

    fun markRead(messageId: String) {
        db.collection("messages").document(messageId)
            .set(mapOf("readAt" to System.currentTimeMillis()), SetOptions.merge())
    }
}
