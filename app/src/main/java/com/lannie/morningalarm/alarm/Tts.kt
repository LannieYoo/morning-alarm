package com.lannie.morningalarm.alarm

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * TTS 엔진 통일.
 * 기기마다 기본 엔진(삼성/Google)과 말하기 속도 설정이 달라 목소리가 제각각이라,
 * Google TTS가 설치돼 있으면 그것을 쓰고 속도·높낮이·한국어 고품질 목소리를 앱에서 고정한다.
 */
object Tts {
    private const val GOOGLE_ENGINE = "com.google.android.tts"

    /** 준비되면 onReady로 설정 완료된 엔진을 넘긴다. 실패하면 null. */
    fun create(context: Context, onReady: (TextToSpeech?) -> Unit): TextToSpeech {
        val engine = if (isInstalled(context, GOOGLE_ENGINE)) GOOGLE_ENGINE else null
        var tts: TextToSpeech? = null
        val listener = TextToSpeech.OnInitListener { status ->
            val t = tts
            if (status == TextToSpeech.SUCCESS && t != null) {
                configure(t)
                onReady(t)
            } else {
                onReady(null)
            }
        }
        tts = if (engine != null) TextToSpeech(context, listener, engine) else TextToSpeech(context, listener)
        return tts
    }

    private fun configure(t: TextToSpeech) {
        t.language = Locale.KOREAN
        t.setSpeechRate(0.95f) // 시스템 설정과 무관하게 차분한 속도
        t.setPitch(1.0f)
        t.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        // 오프라인 한국어 목소리 중 품질이 가장 높은 것
        runCatching {
            t.voices
                ?.filter { it.locale.language == "ko" && !it.isNetworkConnectionRequired }
                ?.maxByOrNull { it.quality }
                ?.let { t.voice = it }
        }
    }

    private fun isInstalled(context: Context, pkg: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
}
