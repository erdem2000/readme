package org.readeram.tts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TtsPlaybackState(
    val bookId: String = "",
    val title: String = "",
    val playing: Boolean = false,
    val paused: Boolean = false,
    val sentenceIndex: Int = 0,
    val sentenceCount: Int = 0,
    val currentText: String = "",
    val error: String? = null,
) {
    val active: Boolean get() = bookId.isNotEmpty() && (playing || paused)
}

data class TtsPlayRequest(
    val bookId: String,
    val title: String,
    val sentences: List<String>,
    val startIndex: Int,
    val engine: String,
    val voice: String,
    val speed: Float,
    val pitch: Float,
)

object TtsSession {
    @Volatile
    var request: TtsPlayRequest? = null

    private val _state = MutableStateFlow(TtsPlaybackState())
    val state: StateFlow<TtsPlaybackState> = _state.asStateFlow()

    fun update(transform: (TtsPlaybackState) -> TtsPlaybackState) {
        _state.value = transform(_state.value)
    }

    fun reset() {
        request = null
        _state.value = TtsPlaybackState()
    }
}
