package org.readeram.tts

import android.content.Context
import android.content.Intent
import android.os.Build
import org.readeram.ReaderamApplication

object TtsController {
    const val ACTION_PLAY = "org.readeram.tts.PLAY"
    const val ACTION_PAUSE = "org.readeram.tts.PAUSE"
    const val ACTION_RESUME = "org.readeram.tts.RESUME"
    const val ACTION_STOP = "org.readeram.tts.STOP"
    const val ACTION_NEXT = "org.readeram.tts.NEXT"
    const val ACTION_PREV = "org.readeram.tts.PREV"
    const val ACTION_SEEK = "org.readeram.tts.SEEK"
    const val EXTRA_INDEX = "org.readeram.tts.INDEX"

    fun play(context: Context, request: TtsPlayRequest) {
        TtsSession.request = request
        start(context, ACTION_PLAY)
    }

    fun pause(context: Context) = start(context, ACTION_PAUSE)
    fun resume(context: Context) = start(context, ACTION_RESUME)
    fun stop(context: Context) = start(context, ACTION_STOP)
    fun next(context: Context) = start(context, ACTION_NEXT)
    fun prev(context: Context) = start(context, ACTION_PREV)

    fun seek(context: Context, index: Int) {
        start(context, ACTION_SEEK, index)
    }

    fun toggle(context: Context) {
        val state = TtsSession.state.value
        when {
            state.playing -> pause(context)
            state.paused -> resume(context)
            else -> Unit
        }
    }

    private fun start(context: Context, action: String, index: Int? = null) {
        val app = context.applicationContext
        val intent = Intent(app, TtsPlaybackService::class.java).setAction(action)
        if (index != null) intent.putExtra(EXTRA_INDEX, index)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.startForegroundService(intent)
        } else {
            app.startService(intent)
        }
    }

    fun appContext(): Context = ReaderamApplication.instance
}
