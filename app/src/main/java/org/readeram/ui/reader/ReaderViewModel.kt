package org.readeram.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.readeram.ReaderamApplication
import org.readeram.data.local.BookEntity
import org.readeram.parser.OpenedBook
import org.readeram.parser.TextSplitter
import org.readeram.tts.TtsController
import org.readeram.tts.TtsEngineOption
import org.readeram.tts.TtsPlayRequest
import org.readeram.tts.TtsPreview
import org.readeram.tts.TtsSession
import org.readeram.tts.TtsVoiceOption

class ReaderViewModel(
    application: Application,
    val bookId: String,
) : AndroidViewModel(application) {
    private val app = application as ReaderamApplication
    private val library = app.container.library
    private val prefs = app.container.preferences
    private val ttsCatalog = app.container.ttsCatalog
    private val preview = TtsPreview(application)
    private var previewJob: Job? = null
    private var previewSentence: String = ""
    private var reflowPages: List<ReflowPage> = emptyList()
    private var pinToLastReflowPage = false

    val book: StateFlow<BookEntity?> = library.observeBook(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<ReadingSettings> = prefs.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadingSettings())

    val ttsState = TtsSession.state

    private val _opened = MutableStateFlow<OpenedBook?>(null)
    val opened: StateFlow<OpenedBook?> = _opened

    private val _chapterIndex = MutableStateFlow(0)
    val chapterIndex: StateFlow<Int> = _chapterIndex

    private val _pageIndex = MutableStateFlow(0)
    val pageIndex: StateFlow<Int> = _pageIndex

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _engines = MutableStateFlow<List<TtsEngineOption>>(emptyList())
    val engines: StateFlow<List<TtsEngineOption>> = _engines

    private val _voices = MutableStateFlow<List<TtsVoiceOption>>(emptyList())
    val voices: StateFlow<List<TtsVoiceOption>> = _voices

    private val _reflowPageCount = MutableStateFlow(1)
    val reflowPageCount: StateFlow<Int> = _reflowPageCount

    init {
        viewModelScope.launch {
            runCatching { library.open(bookId) }
                .onSuccess { openedBook ->
                    _opened.value = openedBook
                    restorePosition(openedBook)
                    library.saveProgress(
                        bookId,
                        currentPosition(),
                        currentProgress(),
                    )
                }
                .onFailure { _error.value = it.message }
        }
        viewModelScope.launch {
            val list = ttsCatalog.engines()
            _engines.value = list
            val saved = settings.value.ttsEngine
            val engine = ttsCatalog.preferredEngine(list, saved)
            if (engine.isNotEmpty() && engine != saved) {
                updateSettings { it.copy(ttsEngine = engine) }
            }
            if (engine.isNotEmpty()) {
                _voices.value = ttsCatalog.voices(engine)
            }
        }
    }

    fun cycleColorMode() = updateSettings { it.copy(colorMode = it.colorMode.next()) }

    fun updateSettings(transform: (ReadingSettings) -> ReadingSettings) {
        viewModelScope.launch { prefs.update(transform) }
    }

    fun setChapter(index: Int) {
        val book = _opened.value as? OpenedBook.Reflow ?: return
        _chapterIndex.value = index.coerceIn(0, book.chapters.lastIndex)
        _pageIndex.value = 0
        pinToLastReflowPage = false
        persist()
    }

    fun setPage(index: Int) {
        when (val book = _opened.value) {
            is OpenedBook.Pdf -> _pageIndex.value = index.coerceIn(0, (book.pageCount - 1).coerceAtLeast(0))
            is OpenedBook.Reflow -> {
                val last = (_reflowPageCount.value - 1).coerceAtLeast(0)
                _pageIndex.value = index.coerceIn(0, last)
            }
            null -> return
        }
        persist()
    }

    fun updateReflowPages(pages: List<ReflowPage>) {
        reflowPages = pages
        _reflowPageCount.value = pages.size.coerceAtLeast(1)
        val last = pages.lastIndex.coerceAtLeast(0)
        val tts = ttsState.value
        val sentence = _opened.value?.sentences?.getOrNull(tts.sentenceIndex)
        val followPage = if (tts.bookId == bookId && tts.active && sentence != null) {
            pages.indexOfFirst { sentence.id in it.sentenceIds }.takeIf { it >= 0 }
        } else {
            null
        }
        val pinLast = pinToLastReflowPage
        pinToLastReflowPage = false
        _pageIndex.value = when {
            pinLast -> last
            followPage != null -> followPage
            else -> _pageIndex.value.coerceIn(0, last)
        }
        if (pinLast && tts.bookId == bookId && tts.active) {
            seekPlayingToId(pages.getOrNull(last)?.sentenceIds?.firstOrNull())
        }
    }

    fun addBookmark() {
        viewModelScope.launch {
            val label = when (val opened = _opened.value) {
                is OpenedBook.Reflow -> opened.chapters.getOrNull(_chapterIndex.value)?.title.orEmpty()
                is OpenedBook.Pdf -> "p${_pageIndex.value + 1}"
                null -> ""
            }
            library.addBookmark(bookId, currentPosition(), label)
        }
    }

    fun playTts(startIndex: Int? = null) {
        previewJob?.cancel()
        preview.release()
        val opened = _opened.value ?: return
        val sentences = opened.sentences.map { it.text }
        if (sentences.isEmpty()) {
            _error.value = getApplication<Application>().getString(org.readeram.R.string.tts_pdf_no_text)
            return
        }
        val start = startIndex ?: startSentenceIndex(opened)
        val s = settings.value
        val engine = ttsCatalog.preferredEngine(_engines.value, s.ttsEngine)
        TtsController.play(
            getApplication(),
            TtsPlayRequest(
                bookId = bookId,
                title = book.value?.title ?: opened.meta.title,
                sentences = sentences,
                startIndex = start,
                engine = engine,
                voice = s.ttsVoice,
                speed = s.ttsSpeed,
                pitch = s.ttsPitch,
            ),
        )
    }

    fun pauseTts() = TtsController.pause(getApplication())
    fun resumeTts() = TtsController.resume(getApplication())
    fun stopTts() = TtsController.stop(getApplication())
    fun nextSentence() = TtsController.next(getApplication())
    fun prevSentence() = TtsController.prev(getApplication())

    fun nextPage() = turnPage(1)
    fun prevPage() = turnPage(-1)

    fun seekToSentence(index: Int) {
        val opened = _opened.value ?: return
        if (opened.sentences.isEmpty()) return
        val clamped = index.coerceIn(0, opened.sentences.lastIndex)
        followTtsIndex(clamped)
        persist()
        val state = ttsState.value
        if (state.bookId == bookId && state.active) {
            TtsSession.request = TtsSession.request?.copy(startIndex = clamped)
            TtsController.seek(getApplication(), clamped)
        } else {
            playTts(clamped)
        }
    }

    fun onSettingsOpened() {
        val wasActive = ttsState.value.bookId == bookId && ttsState.value.active
        previewSentence = currentSentenceText()
        if (wasActive) stopTts()
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            if (wasActive) delay(350)
            speakPreview(settings.value.ttsVoice, previewSentence)
        }
    }

    fun previewVoice(voice: String) {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            speakPreview(voice, previewSentence.ifBlank { currentSentenceText() })
        }
    }

    fun onSettingsClosed() {
        previewJob?.cancel()
        preview.release()
    }

    fun reloadVoices(engine: String) {
        viewModelScope.launch {
            val list = ttsCatalog.voices(engine)
            _voices.value = list
            val current = settings.value.ttsVoice
            val selected = if (list.any { it.name == current }) {
                current
            } else {
                val pick = list.firstOrNull()?.name.orEmpty()
                if (pick.isNotEmpty()) updateSettings { it.copy(ttsVoice = pick) }
                pick
            }
            if (previewSentence.isNotBlank()) speakPreview(selected, previewSentence)
        }
    }

    fun persist() {
        viewModelScope.launch {
            library.saveProgress(bookId, currentPosition(), currentProgress())
        }
    }

    fun followTtsIndex(index: Int) {
        val opened = _opened.value ?: return
        val sentence = opened.sentences.getOrNull(index) ?: return
        when (opened) {
            is OpenedBook.Reflow -> {
                _chapterIndex.value = sentence.chapterIndex
                val page = reflowPages.indexOfFirst { sentence.id in it.sentenceIds }
                if (page >= 0) _pageIndex.value = page
            }
            is OpenedBook.Pdf -> _pageIndex.value = sentence.chapterIndex
        }
    }

    override fun onCleared() {
        previewJob?.cancel()
        preview.release()
        super.onCleared()
    }

    private fun speakPreview(voice: String, text: String) {
        if (text.isBlank()) return
        val s = settings.value
        val engine = ttsCatalog.preferredEngine(_engines.value, s.ttsEngine)
        preview.speak(engine, voice, text, s.ttsSpeed, s.ttsPitch)
    }

    private fun currentSentenceText(): String {
        val opened = _opened.value ?: return ""
        val playing = ttsState.value
        if (playing.bookId == bookId && playing.currentText.isNotBlank()) return playing.currentText
        return opened.sentences.getOrNull(startSentenceIndex(opened))?.text.orEmpty()
    }

    private fun turnPage(delta: Int) {
        when (val opened = _opened.value) {
            is OpenedBook.Pdf -> {
                val next = (_pageIndex.value + delta).coerceIn(0, (opened.pageCount - 1).coerceAtLeast(0))
                if (next == _pageIndex.value) return
                _pageIndex.value = next
                persist()
                seekPlayingToIndex(opened.sentences.indexOfFirst { it.chapterIndex == next })
            }
            is OpenedBook.Reflow -> {
                val lastPage = reflowPages.lastIndex.coerceAtLeast(0)
                val page = _pageIndex.value
                val chapter = _chapterIndex.value
                when {
                    delta > 0 && page < lastPage -> {
                        val next = page + 1
                        _pageIndex.value = next
                        persist()
                        seekPlayingToId(reflowPages.getOrNull(next)?.sentenceIds?.firstOrNull())
                    }
                    delta > 0 && chapter < opened.chapters.lastIndex -> {
                        _chapterIndex.value = chapter + 1
                        _pageIndex.value = 0
                        pinToLastReflowPage = false
                        persist()
                        seekPlayingToIndex(opened.sentences.indexOfFirst { it.chapterIndex == chapter + 1 })
                    }
                    delta < 0 && page > 0 -> {
                        val next = page - 1
                        _pageIndex.value = next
                        persist()
                        seekPlayingToId(reflowPages.getOrNull(next)?.sentenceIds?.firstOrNull())
                    }
                    delta < 0 && chapter > 0 -> {
                        _chapterIndex.value = chapter - 1
                        pinToLastReflowPage = true
                        persist()
                    }
                    else -> return
                }
            }
            null -> return
        }
    }

    private fun seekPlayingToId(sentenceId: Int?) {
        if (sentenceId == null) return
        val index = _opened.value?.sentences?.indexOfFirst { it.id == sentenceId } ?: return
        seekPlayingToIndex(index)
    }

    private fun seekPlayingToIndex(index: Int) {
        if (index < 0) return
        val state = ttsState.value
        if (state.bookId != bookId || !state.active) return
        TtsSession.request = TtsSession.request?.copy(startIndex = index)
        TtsController.seek(getApplication(), index)
    }

    private fun restorePosition(opened: OpenedBook) {
        val raw = book.value?.lastPosition ?: return
        val parts = raw.split(':')
        when (opened) {
            is OpenedBook.Reflow -> {
                _chapterIndex.value = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, opened.chapters.lastIndex) ?: 0
                _pageIndex.value = parts.getOrNull(2)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            }
            is OpenedBook.Pdf -> {
                _pageIndex.value = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, opened.pageCount - 1) ?: 0
            }
        }
    }

    private fun currentPosition(): String {
        return when (_opened.value) {
            is OpenedBook.Reflow -> "ch:${_chapterIndex.value}:${_pageIndex.value}"
            is OpenedBook.Pdf -> "pg:${_pageIndex.value}"
            null -> "0"
        }
    }

    private fun currentProgress(): Float {
        return when (val opened = _opened.value) {
            is OpenedBook.Reflow -> TextSplitter.progressFor(_chapterIndex.value, opened.chapters.size)
            is OpenedBook.Pdf -> TextSplitter.progressFor(_pageIndex.value, opened.pageCount)
            null -> 0f
        }
    }

    private fun startSentenceIndex(opened: OpenedBook): Int {
        val playing = ttsState.value
        if (playing.bookId == bookId) return playing.sentenceIndex
        return when (opened) {
            is OpenedBook.Reflow -> {
                val pageId = reflowPages.getOrNull(_pageIndex.value)?.sentenceIds?.firstOrNull()
                if (pageId != null) {
                    opened.sentences.indexOfFirst { it.id == pageId }.coerceAtLeast(0)
                } else {
                    opened.sentences.indexOfFirst { it.chapterIndex == _chapterIndex.value }.coerceAtLeast(0)
                }
            }
            is OpenedBook.Pdf -> opened.sentences.indexOfFirst { it.chapterIndex == _pageIndex.value }.coerceAtLeast(0)
        }
    }

    companion object {
        fun factory(bookId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ReaderViewModel(ReaderamApplication.instance, bookId) as T
            }
        }
    }
}
