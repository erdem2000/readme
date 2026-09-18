package org.readeram.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import org.readeram.MainActivity
import org.readeram.R
import java.util.Locale

class TtsPlaybackService : Service(), AudioManager.OnAudioFocusChangeListener {
    private val notificationId = 42
    private val channelId = "readeram_tts"
    private val utteranceId = "readeram_sentence"

    private var tts: TextToSpeech? = null
    private var mediaSession: MediaSessionCompat? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null
    private var noisyRegistered = false
    private var enginePackage: String = ""
    private var pendingAction: String? = null
    private var ttsReady = false

    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private val notifications by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pauseInternal()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        mediaSession = MediaSessionCompat(this, "ReaderamTts").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = resumeInternal()
                override fun onPause() = pauseInternal()
                override fun onStop() = stopInternal()
                override fun onSkipToNext() = skip(1)
                override fun onSkipToPrevious() = skip(-1)
            })
            isActive = true
        }
        val power = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "readeram:tts").apply {
            setReferenceCounted(false)
        }
        startForeground(notificationId, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(notificationId, buildNotification())
        when (intent?.action) {
            TtsController.ACTION_PLAY -> playNew()
            TtsController.ACTION_PAUSE -> pauseInternal()
            TtsController.ACTION_RESUME -> resumeInternal()
            TtsController.ACTION_STOP -> stopInternal()
            TtsController.ACTION_NEXT -> skip(1)
            TtsController.ACTION_PREV -> skip(-1)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        abandonFocus()
        unregisterNoisy()
        tts?.stop()
        tts?.shutdown()
        tts = null
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        if (wakeLock?.isHeld == true) wakeLock?.release()
        TtsSession.reset()
    }

    private fun playNew() {
        val request = TtsSession.request ?: return
        TtsSession.update {
            TtsPlaybackState(
                bookId = request.bookId,
                title = request.title,
                playing = false,
                paused = false,
                sentenceIndex = request.startIndex.coerceIn(0, (request.sentences.size - 1).coerceAtLeast(0)),
                sentenceCount = request.sentences.size,
                currentText = request.sentences.getOrNull(request.startIndex).orEmpty(),
            )
        }
        ensureEngine(request) {
            if (requestFocus()) {
                speakCurrent(flush = true)
            }
        }
    }

    private fun ensureEngine(request: TtsPlayRequest, onReady: () -> Unit) {
        val engine = request.engine
        if (tts != null && ttsReady && enginePackage == engine) {
            applyVoice(request)
            onReady()
            return
        }
        ttsReady = false
        tts?.stop()
        tts?.shutdown()
        tts = null
        pendingAction = "ready"
        enginePackage = engine
        val listener = TextToSpeech.OnInitListener { status ->
            if (status != TextToSpeech.SUCCESS) {
                TtsSession.update { it.copy(error = getString(R.string.tts_no_engines), playing = false) }
                stopSelf()
                return@OnInitListener
            }
            ttsReady = true
            applyVoice(request)
            tts?.setOnUtteranceProgressListener(utteranceListener)
            onReady()
        }
        tts = if (engine.isNotBlank()) {
            TextToSpeech(this, listener, engine)
        } else {
            TextToSpeech(this, listener)
        }
    }

    private fun applyVoice(request: TtsPlayRequest) {
        val engine = tts ?: return
        engine.language = Locale.getDefault()
        if (request.voice.isNotBlank()) {
            engine.voices?.firstOrNull { it.name == request.voice }?.let { engine.voice = it }
        }
        engine.setSpeechRate(request.speed.coerceIn(0.5f, 2.5f))
        engine.setPitch(request.pitch.coerceIn(0.5f, 2.0f))
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
    }

    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            TtsSession.update { it.copy(playing = true, paused = false, error = null) }
            publish()
        }

        override fun onDone(utteranceId: String?) {
            skip(1, fromCallback = true)
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            TtsSession.update { it.copy(error = "TTS error", playing = false) }
            publish()
        }
    }

    private fun speakCurrent(flush: Boolean) {
        val request = TtsSession.request ?: return
        val state = TtsSession.state.value
        if (request.sentences.isEmpty()) {
            TtsSession.update { it.copy(error = getString(R.string.tts_pdf_no_text), playing = false) }
            publish()
            return
        }
        val index = state.sentenceIndex.coerceIn(0, request.sentences.lastIndex)
        val text = request.sentences[index]
        TtsSession.update {
            it.copy(
                playing = true,
                paused = false,
                sentenceIndex = index,
                currentText = text,
                sentenceCount = request.sentences.size,
            )
        }
        wakeLock?.acquire(3 * 60 * 60 * 1000L)
        registerNoisy()
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        val queue = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(text, queue, params, utteranceId)
        publish()
    }

    private fun pauseInternal() {
        tts?.stop()
        TtsSession.update { it.copy(playing = false, paused = it.bookId.isNotEmpty()) }
        if (wakeLock?.isHeld == true) wakeLock?.release()
        publish()
    }

    private fun resumeInternal() {
        val state = TtsSession.state.value
        if (state.bookId.isEmpty()) {
            playNew()
            return
        }
        if (requestFocus()) speakCurrent(flush = true)
    }

    private fun skip(delta: Int, fromCallback: Boolean = false) {
        val request = TtsSession.request ?: return
        if (request.sentences.isEmpty()) return
        val current = TtsSession.state.value.sentenceIndex
        val next = current + delta
        if (next !in request.sentences.indices) {
            if (fromCallback || delta > 0) stopInternal()
            return
        }
        TtsSession.update { it.copy(sentenceIndex = next) }
        if (TtsSession.state.value.playing || fromCallback) {
            speakCurrent(flush = true)
        } else {
            TtsSession.update {
                it.copy(currentText = request.sentences[next])
            }
            publish()
        }
    }

    private fun stopInternal() {
        tts?.stop()
        abandonFocus()
        unregisterNoisy()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        TtsSession.reset()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun requestFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setOnAudioFocusChangeListener(this)
                .build()
            focusRequest = req
            audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(this)
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pauseInternal()
            AudioManager.AUDIOFOCUS_GAIN -> if (TtsSession.state.value.paused) resumeInternal()
        }
    }

    private fun registerNoisy() {
        if (noisyRegistered) return
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        ContextCompat.registerReceiver(this, noisyReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        noisyRegistered = true
    }

    private fun unregisterNoisy() {
        if (!noisyRegistered) return
        try {
            unregisterReceiver(noisyReceiver)
        } catch (_: Exception) {
        }
        noisyRegistered = false
    }

    private fun publish() {
        val state = TtsSession.state.value
        val playback = if (state.playing) PlaybackStateCompat.STATE_PLAYING
        else if (state.paused) PlaybackStateCompat.STATE_PAUSED
        else PlaybackStateCompat.STATE_STOPPED
        mediaSession?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, state.title.ifBlank { getString(R.string.app_name) })
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, getString(R.string.tts_title))
                .build(),
        )
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS,
                )
                .setState(playback, state.sentenceIndex.toLong(), if (state.playing) 1f else 0f)
                .build(),
        )
        notifications.notify(notificationId, buildNotification())
    }

    private fun buildNotification(): Notification {
        val state = TtsSession.state.value
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val playPauseAction = if (state.playing) {
            action(R.drawable.ic_stat_tts, getString(R.string.tts_pause), TtsController.ACTION_PAUSE, 1)
        } else {
            action(R.drawable.ic_stat_tts, getString(R.string.tts_resume), TtsController.ACTION_RESUME, 2)
        }
        val title = state.title.ifBlank { getString(R.string.app_name) }
        val text = when {
            state.playing -> getString(R.string.tts_notification_playing)
            state.paused -> getString(R.string.tts_notification_paused)
            else -> getString(R.string.tts_title)
        }
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_tts)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(state.playing)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(MediaStyle().setMediaSession(mediaSession?.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .addAction(action(R.drawable.ic_stat_tts, getString(R.string.prev_sentence), TtsController.ACTION_PREV, 3))
            .addAction(playPauseAction)
            .addAction(action(R.drawable.ic_stat_tts, getString(R.string.next_sentence), TtsController.ACTION_NEXT, 4))
            .addAction(action(R.drawable.ic_stat_tts, getString(R.string.tts_stop), TtsController.ACTION_STOP, 5))
            .build()
    }

    private fun action(icon: Int, title: String, action: String, request: Int): NotificationCompat.Action {
        val pi = PendingIntent.getService(
            this,
            request,
            Intent(this, TtsPlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action(icon, title, pi)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.tts_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
                setSound(null, null)
            }
            notifications.createNotificationChannel(channel)
        }
    }
}
