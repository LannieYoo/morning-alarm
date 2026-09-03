package com.lannie.morningalarm.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Firestore 데이터 레이어.
 * 오프라인 캐시가 기본 활성화되어 있어 네트워크가 끊겨도 쓰기는 큐에 쌓였다가 동기화된다.
 * 모든 쿼리는 등호 필터만 사용한다 (복합 인덱스 불필요).
 */
object Repo {
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    suspend fun ensureAuth() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) auth.signInAnonymously().await()
    }

    fun isAuthed(): Boolean = FirebaseAuth.getInstance().currentUser != null

    // ---- 사용자 / 상태(헬스체크) / 거절 시간 ----

    fun upsertUser(phone: String, fields: Map<String, Any>) {
        db.collection("users").document(phone).set(fields, SetOptions.merge())
    }

    fun updateHealth(phone: String, health: Map<String, Any>) {
        db.collection("users").document(phone).set(mapOf("health" to health), SetOptions.merge())
    }

    /** 내 알람 거절 시간을 올려 두면 상대가 알람을 만들 때 미리 경고를 볼 수 있다 */
    fun updateQuietRules(phone: String, rules: List<QuietRule>) {
        val list = rules.map {
            mapOf(
                "id" to it.id,
                "days" to it.days,
                "startMin" to it.startMin,
                "endMin" to it.endMin,
                "reason" to it.reason,
                "note" to it.note
            )
        }
        db.collection("users").document(phone).set(mapOf("quietRules" to list), SetOptions.merge())
    }

    fun listenUser(phone: String, onChange: (Map<String, Any>?) -> Unit): ListenerRegistration =
        db.collection("users").document(phone).addSnapshotListener { s, _ ->
            onChange(s?.data)
        }

    /** users/{phone}.quietRules 파싱 */
    @Suppress("UNCHECKED_CAST")
    fun parseQuietRules(data: Map<String, Any>?): List<QuietRule> {
        val raw = data?.get("quietRules") as? List<Map<String, Any>> ?: return emptyList()
        return raw.mapNotNull { m ->
            runCatching {
                QuietRule(
                    id = m["id"] as? String ?: "",
                    days = (m["days"] as? List<Number>)?.map { it.toInt() } ?: emptyList(),
                    startMin = (m["startMin"] as? Number)?.toInt() ?: 0,
                    endMin = (m["endMin"] as? Number)?.toInt() ?: 0,
                    reason = m["reason"] as? String ?: QuietReason.OTHER,
                    note = m["note"] as? String ?: ""
                )
            }.getOrNull()
        }
    }

    // ---- 연결 요청 / 연락처 ----

    fun sendPairRequest(fromPhone: String, fromName: String, toPhone: String) {
        val doc = db.collection("pairRequests").document()
        doc.set(
            PairRequest(
                id = doc.id,
                fromPhone = fromPhone,
                fromName = fromName,
                toPhone = toPhone,
                status = "pending",
                createdAt = System.currentTimeMillis()
            )
        )
    }

    /** 나에게 온 대기 중 요청 */
    fun listenPairRequestsTo(phone: String, onChange: (List<PairRequest>) -> Unit): ListenerRegistration =
        db.collection("pairRequests")
            .whereEqualTo("toPhone", phone)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { s, _ ->
                if (s !=
                    null
                ) {
                    onChange(
                        s.documents.mapNotNull { d ->
                            d.toObject(PairRequest::class.java)?.apply { id = d.id }
                        }
                    )
                }
            }

    /** 내가 보낸 대기 중 요청 */
    fun listenPairRequestsFrom(phone: String, onChange: (List<PairRequest>) -> Unit): ListenerRegistration =
        db.collection("pairRequests")
            .whereEqualTo("fromPhone", phone)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { s, _ ->
                if (s !=
                    null
                ) {
                    onChange(
                        s.documents.mapNotNull { d ->
                            d.toObject(PairRequest::class.java)?.apply { id = d.id }
                        }
                    )
                }
            }

    /**
     * 수락된 연결 전부 → 연락처 목록. 내가 보낸 것과 받은 것을 각각 구독해 합친다.
     * 같은 번호가 여러 번 수락됐으면 하나로 합친다.
     */
    fun listenContacts(me: String, onChange: (List<Contact>) -> Unit): ListenerRegistration {
        var sent: List<PairRequest> = emptyList()
        var received: List<PairRequest> = emptyList()
        fun emit() {
            val map = linkedMapOf<String, Contact>()
            for (r in sent) map[r.toPhone] = Contact(r.toPhone, r.toName.ifBlank { map[r.toPhone]?.name ?: r.toPhone })
            for (r in received) {
                map[r.fromPhone] =
                    Contact(r.fromPhone, r.fromName.ifBlank { map[r.fromPhone]?.name ?: r.fromPhone })
            }
            onChange(map.values.toList())
        }
        val a = db.collection("pairRequests")
            .whereEqualTo("fromPhone", me)
            .whereEqualTo("status", "accepted")
            .addSnapshotListener { s, _ ->
                if (s != null) {
                    sent = s.documents.mapNotNull { d -> d.toObject(PairRequest::class.java) }
                    emit()
                }
            }
        val b = db.collection("pairRequests")
            .whereEqualTo("toPhone", me)
            .whereEqualTo("status", "accepted")
            .addSnapshotListener { s, _ ->
                if (s != null) {
                    received = s.documents.mapNotNull { d -> d.toObject(PairRequest::class.java) }
                    emit()
                }
            }
        return ListenerRegistration {
            a.remove()
            b.remove()
        }
    }

    fun acceptPairRequest(requestId: String, myName: String) {
        db.collection("pairRequests").document(requestId)
            .set(mapOf("status" to "accepted", "toName" to myName), SetOptions.merge())
    }

    fun rejectPairRequest(requestId: String) {
        db.collection("pairRequests").document(requestId).update("status", "rejected")
    }

    // ---- 알람 ----

    fun saveAlarm(alarm: Alarm): String {
        val doc = if (alarm.id.isBlank()) {
            db.collection("alarms").document()
        } else {
            db.collection("alarms").document(alarm.id)
        }
        alarm.id = doc.id
        alarm.updatedAt = System.currentTimeMillis()
        doc.set(alarm)
        return doc.id
    }

    fun deleteAlarm(alarmId: String) {
        db.collection("alarms").document(alarmId).delete()
    }

    /** 나에게 오는 알람 (받는 기기가 구독) */
    fun listenAlarmsFor(targetPhone: String, onChange: (List<Alarm>) -> Unit): ListenerRegistration =
        db.collection("alarms")
            .whereEqualTo("targetPhone", targetPhone)
            .addSnapshotListener { s, _ ->
                if (s !=
                    null
                ) {
                    onChange(s.documents.mapNotNull { d -> d.toObject(Alarm::class.java)?.apply { id = d.id } })
                }
            }

    /** 내가 만든 알람 */
    fun listenAlarmsOwned(ownerPhone: String, onChange: (List<Alarm>) -> Unit): ListenerRegistration =
        db.collection("alarms")
            .whereEqualTo("ownerPhone", ownerPhone)
            .addSnapshotListener { s, _ ->
                if (s !=
                    null
                ) {
                    onChange(s.documents.mapNotNull { d -> d.toObject(Alarm::class.java)?.apply { id = d.id } })
                }
            }

    // ---- 알람 기록 ----

    fun newEventId(): String = db.collection("events").document().id

    fun createEvent(event: AlarmEvent) {
        val doc = if (event.id.isBlank()) {
            db.collection("events").document()
        } else {
            db.collection("events").document(event.id)
        }
        event.id = doc.id
        doc.set(event)
    }

    fun updateEvent(eventId: String, fields: Map<String, Any>) {
        db.collection("events").document(eventId).set(fields, SetOptions.merge())
    }

    /** 내가 보낸 알람의 기록 */
    fun listenEvents(ownerPhone: String, onChange: (List<AlarmEvent>) -> Unit): ListenerRegistration =
        db.collection("events")
            .whereEqualTo("ownerPhone", ownerPhone)
            .addSnapshotListener { s, _ ->
                if (s != null) {
                    onChange(
                        s.documents.mapNotNull { d -> d.toObject(AlarmEvent::class.java)?.apply { id = d.id } }
                            .sortedByDescending { it.firedAt }
                    )
                }
            }

    /**
     * 내가 보낸 알람 기록의 변화만 구독 (보낸 사람 알림용).
     * 첫 스냅샷(기존 기록 전체)은 건너뛰고, 이후 추가/변경된 문서만 넘긴다.
     */
    fun listenEventChanges(ownerPhone: String, onChange: (List<AlarmEvent>) -> Unit): ListenerRegistration {
        var first = true
        return db.collection("events")
            .whereEqualTo("ownerPhone", ownerPhone)
            .addSnapshotListener { s, _ ->
                if (s == null) return@addSnapshotListener
                if (first) {
                    first = false
                    return@addSnapshotListener
                }
                val changed = s.documentChanges.mapNotNull { dc ->
                    dc.document.toObject(AlarmEvent::class.java).apply { id = dc.document.id }
                }
                if (changed.isNotEmpty()) onChange(changed)
            }
    }

    // ---- 메시지 (채팅 / 긴급팝업 / 테스트) ----

    fun sendMessage(from: String, fromName: String, to: String, text: String, kind: String): String {
        val doc = db.collection("messages").document()
        doc.set(
            Message(
                id = doc.id,
                fromPhone = from,
                fromName = fromName,
                toPhone = to,
                text = text,
                kind = kind,
                sentAt = System.currentTimeMillis()
            )
        )
        return doc.id
    }

    /** 두 사람 사이의 대화 전체 (클라이언트에서 정렬) */
    fun listenChat(me: String, peer: String, onChange: (List<Message>) -> Unit): ListenerRegistration =
        db.collection("messages")
            .whereIn("fromPhone", listOf(me, peer))
            .addSnapshotListener { s, _ ->
                if (s != null) {
                    onChange(
                        s.documents.mapNotNull { d -> d.toObject(Message::class.java)?.apply { id = d.id } }
                            .filter {
                                (it.fromPhone == me && it.toPhone == peer) ||
                                    (it.fromPhone == peer && it.toPhone == me)
                            }
                            .sortedBy { it.sentAt }
                    )
                }
            }

    /** 나에게 온, 아직 전달 확인 안 된 메시지 (수신 기기 서비스가 구독) */
    fun listenUndelivered(me: String, onChange: (List<Message>) -> Unit): ListenerRegistration =
        db.collection("messages")
            .whereEqualTo("toPhone", me)
            .whereEqualTo("deliveredAt", 0L)
            .addSnapshotListener { s, _ ->
                if (s !=
                    null
                ) {
                    onChange(s.documents.mapNotNull { d -> d.toObject(Message::class.java)?.apply { id = d.id } })
                }
            }

    /** 나에게 온, 아직 안 읽은 메시지 (하단 메뉴 배지용). 테스트 알람은 제외 */
    fun listenUnread(me: String, onChange: (List<Message>) -> Unit): ListenerRegistration = db.collection("messages")
        .whereEqualTo("toPhone", me)
        .whereEqualTo("readAt", 0L)
        .addSnapshotListener { s, _ ->
            if (s != null) {
                onChange(
                    s.documents.mapNotNull { d -> d.toObject(Message::class.java)?.apply { id = d.id } }
                        .filter { !Kind.isAlarmLike(it.kind) }
                )
            }
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
