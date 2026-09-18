package org.readeram.tts

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

data class TtsEngineOption(
    val packageName: String,
    val label: String,
    val kind: Kind,
) {
    enum class Kind { Google, Samsung, Other }

    val preferred: Boolean get() = kind != Kind.Other
}

data class TtsVoiceOption(
    val name: String,
    val locale: String,
    val label: String,
)

class TtsCatalog(private val context: Context) {
    fun engines(): List<TtsEngineOption> {
        val resolved = context.packageManager.queryIntentServices(
            Intent("android.intent.action.TTS_SERVICE"),
            0,
        )
        val engines = resolved.map { info ->
            val packageName = info.serviceInfo.packageName
            TtsEngineOption(
                packageName = packageName,
                label = info.serviceInfo.loadLabel(context.packageManager).toString(),
                kind = when {
                    packageName.contains("google", ignoreCase = true) -> TtsEngineOption.Kind.Google
                    packageName.contains("samsung", ignoreCase = true) ||
                        packageName == SAMSUNG_ENGINE -> TtsEngineOption.Kind.Samsung
                    else -> TtsEngineOption.Kind.Other
                },
            )
        }
        return engines.sortedWith(
            compareBy<TtsEngineOption> {
                when (it.kind) {
                    TtsEngineOption.Kind.Google -> 0
                    TtsEngineOption.Kind.Samsung -> 1
                    TtsEngineOption.Kind.Other -> 2
                }
            }.thenBy { it.label },
        )
    }

    suspend fun voices(enginePackage: String): List<TtsVoiceOption> {
        if (enginePackage.isBlank()) return emptyList()
        return suspendCancellableCoroutine { cont ->
            var tts: TextToSpeech? = null
            tts = TextToSpeech(context, { status ->
                if (status != TextToSpeech.SUCCESS) {
                    tts?.shutdown()
                    if (cont.isActive) cont.resume(emptyList())
                    return@TextToSpeech
                }
                val locale = Locale.getDefault()
                val list = tts?.voices.orEmpty()
                    .filter { !it.isNetworkConnectionRequired }
                    .sortedWith(
                        compareByDescending<Voice> { it.locale.language == locale.language }
                            .thenByDescending { it.locale.country == locale.country }
                            .thenBy { it.locale.toLanguageTag() }
                            .thenBy { it.name },
                    )
                    .map { voice ->
                        TtsVoiceOption(
                            name = voice.name,
                            locale = voice.locale.toLanguageTag(),
                            label = "${voice.locale.displayName} · ${voice.name.substringAfterLast(' ')}",
                        )
                    }
                tts?.shutdown()
                if (cont.isActive) cont.resume(list)
            }, enginePackage)
            cont.invokeOnCancellation { tts?.shutdown() }
        }
    }

    fun preferredEngine(available: List<TtsEngineOption>, saved: String): String {
        available.firstOrNull { it.packageName == saved }?.let { return it.packageName }
        available.firstOrNull { it.kind == TtsEngineOption.Kind.Google }?.let { return it.packageName }
        available.firstOrNull { it.kind == TtsEngineOption.Kind.Samsung }?.let { return it.packageName }
        return available.firstOrNull()?.packageName.orEmpty()
    }

    companion object {
        const val GOOGLE_ENGINE = "com.google.android.tts"
        const val SAMSUNG_ENGINE = "com.samsung.SMT"
    }
}
