package org.readeram.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class TtsPreview(context: Context) {
    private val app = context.applicationContext
    private val lock = Any()
    private var generation = 0
    private var enginePackage = ""
    private var tts: TextToSpeech? = null
    private var ready = false

    fun speak(engine: String, voice: String, text: String, speed: Float, pitch: Float) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val myGen: Int
        synchronized(lock) {
            generation += 1
            myGen = generation
        }
        val existing = synchronized(lock) { tts }
        if (existing != null && ready && enginePackage == engine) {
            existing.stop()
            applyVoice(existing, voice, speed, pitch)
            speakNow(existing, trimmed)
            return
        }
        rebuild(engine, voice, speed, pitch, trimmed, myGen)
    }

    fun release() {
        synchronized(lock) {
            generation += 1
            ready = false
            enginePackage = ""
            tts?.stop()
            tts?.shutdown()
            tts = null
        }
    }

    private fun rebuild(
        engine: String,
        voice: String,
        speed: Float,
        pitch: Float,
        text: String,
        myGen: Int,
    ) {
        synchronized(lock) {
            ready = false
            tts?.stop()
            tts?.shutdown()
            tts = null
            enginePackage = engine
        }
        val holder = arrayOfNulls<TextToSpeech>(1)
        val listener = TextToSpeech.OnInitListener { status ->
            val created = holder[0] ?: return@OnInitListener
            synchronized(lock) {
                if (myGen != generation) {
                    created.shutdown()
                    return@OnInitListener
                }
                if (status != TextToSpeech.SUCCESS) {
                    created.shutdown()
                    if (tts === created) tts = null
                    ready = false
                    return@OnInitListener
                }
                tts = created
                ready = true
                applyVoice(created, voice, speed, pitch)
                created.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) = Unit

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = Unit
                })
                speakNow(created, text)
            }
        }
        holder[0] = if (engine.isNotBlank()) {
            TextToSpeech(app, listener, engine)
        } else {
            TextToSpeech(app, listener)
        }
    }

    private fun applyVoice(engine: TextToSpeech, voice: String, speed: Float, pitch: Float) {
        engine.language = Locale.getDefault()
        if (voice.isNotBlank()) {
            engine.voices?.firstOrNull { it.name == voice }?.let { engine.voice = it }
        }
        engine.setSpeechRate(speed.coerceIn(0.5f, 2.5f))
        engine.setPitch(pitch.coerceIn(0.5f, 2.0f))
    }

    private fun speakNow(engine: TextToSpeech, text: String) {
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID)
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)
    }

    companion object {
        private const val UTTERANCE_ID = "readeram_preview"
    }
}
