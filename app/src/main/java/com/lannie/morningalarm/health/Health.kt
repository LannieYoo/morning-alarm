package com.lannie.morningalarm.health

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * 알람이 실제로 울릴 수 있는 상태인지 점검 (헬스체크).
 * 결과는 Firestore users/{phone}.health로 올라가 엄마 화면에도 보인다.
 */
data class HealthStatus(
    val notifOk: Boolean,
    val exactOk: Boolean,
    val batteryOk: Boolean,
    val fullscreenOk: Boolean,
    /** "다른 앱 위에 표시" — 폰을 쓰는 중에도 알람/긴급 화면을 바로 띄우려면 필요 */
    val overlayOk: Boolean,
    val dndOk: Boolean,
    val alarmVolumePct: Int
) {
    val allOk: Boolean
        get() = notifOk && exactOk && batteryOk && fullscreenOk && overlayOk && dndOk

    /** 부족한 항목 이름 (배너·상대 카드에 표시) */
    val missing: List<String>
        get() = missingFrom(toMap())

    companion object {
        private val LABELS = listOf(
            "notifOk" to "알림",
            "exactOk" to "정확한 알람",
            "batteryOk" to "배터리 최적화 제외",
            "overlayOk" to "다른 앱 위에 표시",
            "fullscreenOk" to "전체 화면 알림",
            "dndOk" to "방해금지 예외"
        )

        /** users/{phone}.health 맵에서 부족한 항목 이름 */
        fun missingFrom(h: Map<String, Any>?): List<String> {
            if (h == null) return emptyList()
            return LABELS.filter { (key, _) -> (h[key] as? Boolean) == false }.map { it.second }
        }
    }

    fun toMap(): Map<String, Any> = mapOf(
        "notifOk" to notifOk,
        "exactOk" to exactOk,
        "batteryOk" to batteryOk,
        "fullscreenOk" to fullscreenOk,
        "overlayOk" to overlayOk,
        "dndOk" to dndOk,
        "alarmVolumePct" to alarmVolumePct,
        "updatedAt" to System.currentTimeMillis()
    )
}

object Health {
    /** users/{phone}.health 맵에서 부족한 항목 이름 (상대 카드 표시용) */
    fun missingFrom(h: Map<String, Any>?): List<String> = HealthStatus.missingFrom(h)

    fun check(context: Context): HealthStatus {
        val nm = context.getSystemService(NotificationManager::class.java)
        val am = context.getSystemService(AlarmManager::class.java)
        val pm = context.getSystemService(PowerManager::class.java)
        val audio = context.getSystemService(AudioManager::class.java)

        val notifOk = nm.areNotificationsEnabled()
        val exactOk = if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
        val batteryOk = pm.isIgnoringBatteryOptimizations(context.packageName)
        val fullscreenOk = if (Build.VERSION.SDK_INT >= 34) nm.canUseFullScreenIntent() else true
        val overlayOk = Settings.canDrawOverlays(context)

        // 방해금지: 알람까지 차단하는 모드인지 (PRIORITY까지는 알람 허용이 기본)
        val filter = nm.currentInterruptionFilter
        val dndOk = filter == NotificationManager.INTERRUPTION_FILTER_ALL ||
            filter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN ||
            filter == NotificationManager.INTERRUPTION_FILTER_PRIORITY

        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM).coerceAtLeast(1)
        val cur = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        val pct = cur * 100 / max

        return HealthStatus(notifOk, exactOk, batteryOk, fullscreenOk, overlayOk, dndOk, pct)
    }

    // ---- 설정 화면으로 이동하는 인텐트들 ----

    fun notifSettingsIntent(context: Context): Intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun exactAlarmIntent(context: Context): Intent? = if (Build.VERSION.SDK_INT >= 31) {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName))
    } else {
        null
    }

    fun batteryIntent(context: Context): Intent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:" + context.packageName)
    )

    fun fullscreenIntent(context: Context): Intent? = if (Build.VERSION.SDK_INT >= 34) {
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + context.packageName))
    } else {
        null
    }

    fun overlayIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:" + context.packageName)
    )

    fun soundSettingsIntent(): Intent = Intent(Settings.ACTION_SOUND_SETTINGS)
}
