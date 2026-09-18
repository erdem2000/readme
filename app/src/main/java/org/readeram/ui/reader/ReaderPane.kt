package org.readeram.ui.reader

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.readeram.R
import org.readeram.parser.OpenedBook
import org.readeram.tts.TtsPlaybackState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderPane(
    bookId: String,
    onBack: (() -> Unit)?,
    tabletop: Boolean,
    modifier: Modifier = Modifier,
) {
    val vm: ReaderViewModel = viewModel(key = bookId, factory = ReaderViewModel.factory(bookId))
    val book by vm.book.collectAsStateWithLifecycle()
    val opened by vm.opened.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val chapterIndex by vm.chapterIndex.collectAsStateWithLifecycle()
    val pageIndex by vm.pageIndex.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val tts by vm.ttsState.collectAsStateWithLifecycle()
    val engines by vm.engines.collectAsStateWithLifecycle()
    val voices by vm.voices.collectAsStateWithLifecycle()
    val palette = settings.palette
    var chrome by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }
    val activity = LocalContext.current as? Activity

    DisposableEffect(settings.brightness, settings.useSystemBrightness) {
        activity?.let { applyBrightness(it, settings) }
        onDispose {
            activity?.let { restoreBrightness(it) }
        }
    }

    LaunchedEffect(tts.sentenceIndex, tts.bookId) {
        if (tts.bookId == bookId && tts.active) {
            vm.followTtsIndex(tts.sentenceIndex)
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        when {
            opened == null && error == null -> {
                CircularProgressIndicator(
                    Modifier.align(Alignment.Center),
                    color = palette.onBackground,
                )
            }
            error != null && opened == null -> {
                Text(
                    error ?: "",
                    color = palette.onBackground,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }
            opened is OpenedBook.Reflow -> {
                ReflowReader(
                    book = opened as OpenedBook.Reflow,
                    chapterIndex = chapterIndex,
                    settings = settings,
                    highlight = tts.takeIf { it.bookId == bookId }?.currentText,
                    onToggleChrome = { chrome = !chrome },
                    onCycleColor = { vm.cycleColorMode() },
                    onBrightnessDrag = { delta ->
                        if (!settings.useSystemBrightness) {
                            vm.updateSettings {
                                it.copy(brightness = (it.brightness - delta / 600f).coerceIn(0.02f, 1f))
                            }
                        }
                    },
                )
            }
            opened is OpenedBook.Pdf -> {
                PdfReader(
                    uri = book?.uri,
                    pdf = opened as OpenedBook.Pdf,
                    pageIndex = pageIndex,
                    settings = settings,
                    onPage = {
                        vm.setPage(it)
                    },
                    onToggleChrome = { chrome = !chrome },
                    onCycleColor = { vm.cycleColorMode() },
                    onBrightnessDrag = { delta ->
                        if (!settings.useSystemBrightness) {
                            vm.updateSettings {
                                it.copy(brightness = (it.brightness - delta / 600f).coerceIn(0.02f, 1f))
                            }
                        }
                    },
                )
            }
        }

        if (settings.extraDim > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = settings.extraDim * 0.72f)),
            )
        }

        val showChrome = chrome || tabletop
        if (showChrome) {
            Column(
                Modifier
                    .align(if (tabletop) Alignment.BottomCenter else Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(palette.chrome.copy(alpha = 0.96f))
                    .padding(horizontal = 4.dp, vertical = 6.dp),
            ) {
                if (!tabletop) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = stringResource(R.string.back),
                                    tint = palette.onChrome,
                                )
                            }
                        }
                        Text(
                            book?.title ?: "",
                            color = palette.onChrome,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { showToc = true }) {
                            Icon(Icons.AutoMirrored.Outlined.List, stringResource(R.string.contents), tint = palette.onChrome)
                        }
                        IconButton(onClick = { vm.addBookmark() }) {
                            Icon(Icons.Outlined.BookmarkAdd, stringResource(R.string.bookmark), tint = palette.onChrome)
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Outlined.Tune, stringResource(R.string.reading_settings), tint = palette.onChrome)
                        }
                    }
                }
                TtsBar(
                    state = tts.takeIf { it.bookId == bookId } ?: TtsPlaybackState(),
                    palette = palette,
                    onPlay = vm::playTts,
                    onPause = vm::pauseTts,
                    onResume = vm::resumeTts,
                    onStop = vm::stopTts,
                    onPrev = vm::prevSentence,
                    onNext = vm::nextSentence,
                    onSettings = { showSettings = true },
                )
                val progress = when (val o = opened) {
                    is OpenedBook.Reflow -> if (o.chapters.isEmpty()) 0f else (chapterIndex + 1f) / o.chapters.size
                    is OpenedBook.Pdf -> if (o.pageCount == 0) 0f else (pageIndex + 1f) / o.pageCount
                    else -> 0f
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    color = palette.onChrome,
                    trackColor = palette.onChrome.copy(alpha = 0.2f),
                )
            }
        }

        if (showSettings) {
            ModalBottomSheet(
                onDismissRequest = { showSettings = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = palette.chrome,
            ) {
                ReadingSettingsSheet(
                    settings = settings,
                    engines = engines,
                    voices = voices,
                    isPdf = opened is OpenedBook.Pdf,
                    onChange = vm::updateSettings,
                    onEngine = { engine ->
                        vm.updateSettings { it.copy(ttsEngine = engine, ttsVoice = "") }
                        vm.reloadVoices(engine)
                    },
                    onClose = { showSettings = false },
                )
            }
        }

        if (showToc) {
            ModalBottomSheet(onDismissRequest = { showToc = false }, containerColor = palette.chrome) {
                Text(
                    stringResource(R.string.contents),
                    color = palette.onChrome,
                    modifier = Modifier.padding(16.dp),
                    fontWeight = FontWeight.SemiBold,
                )
                when (val o = opened) {
                    is OpenedBook.Reflow -> {
                        LazyColumn {
                            itemsIndexed(o.chapters) { index, chapter ->
                                Text(
                                    chapter.title,
                                    color = palette.onChrome,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            vm.setChapter(index)
                                            showToc = false
                                        }
                                        .padding(16.dp),
                                )
                            }
                        }
                    }
                    is OpenedBook.Pdf -> {
                        Text(
                            stringResource(R.string.page_n, pageIndex + 1, o.pageCount),
                            color = palette.onChrome,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun TtsBar(
    state: TtsPlaybackState,
    palette: ReadingPalette,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconButton(onClick = onSettings) {
            Icon(Icons.Outlined.Settings, stringResource(R.string.tts_title), tint = palette.onChrome)
        }
        IconButton(onClick = onPrev) {
            Icon(Icons.Outlined.SkipPrevious, stringResource(R.string.prev_sentence), tint = palette.onChrome)
        }
        IconButton(
            onClick = {
                when {
                    state.playing -> onPause()
                    state.paused -> onResume()
                    else -> onPlay()
                }
            },
        ) {
            Icon(
                if (state.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                stringResource(R.string.tts_play),
                tint = palette.onChrome,
            )
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Outlined.SkipNext, stringResource(R.string.next_sentence), tint = palette.onChrome)
        }
        IconButton(onClick = onStop) {
            Icon(Icons.Outlined.Stop, stringResource(R.string.tts_stop), tint = palette.onChrome)
        }
    }
}

@Composable
private fun ReflowReader(
    book: OpenedBook.Reflow,
    chapterIndex: Int,
    settings: ReadingSettings,
    highlight: String?,
    onToggleChrome: () -> Unit,
    onCycleColor: () -> Unit,
    onBrightnessDrag: (Float) -> Unit,
) {
    val chapter = book.chapters.getOrNull(chapterIndex) ?: return
    val palette = settings.palette
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .clickable { onToggleChrome() }
                .padding(horizontal = settings.marginDp.dp, vertical = 88.dp),
        ) {
            item {
                Text(
                    chapter.title,
                    color = palette.onBackground,
                    fontSize = (settings.fontSizeSp + 4).sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = settings.font.family,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
            itemsIndexed(chapter.paragraphs) { index, paragraph ->
                val sentenceHere = chapter.sentences.filter { it.paragraphIndex == index }
                val highlighted = highlight?.takeIf { h -> sentenceHere.any { it.text == h } }
                Text(
                    text = annotatedParagraph(paragraph, highlighted, palette),
                    color = palette.onBackground,
                    fontSize = settings.fontSizeSp.sp,
                    fontFamily = settings.font.family,
                    fontWeight = settings.weight.weight,
                    lineHeight = (settings.fontSizeSp * settings.lineHeight).sp,
                    textAlign = settings.align.compose,
                    modifier = Modifier.padding(bottom = settings.paragraphSpacingSp.dp),
                )
            }
        }
        EdgeGestures(onCycleColor, onBrightnessDrag)
    }
}

@Composable
private fun PdfReader(
    uri: String?,
    pdf: OpenedBook.Pdf,
    pageIndex: Int,
    settings: ReadingSettings,
    onPage: (Int) -> Unit,
    onToggleChrome: () -> Unit,
    onCycleColor: () -> Unit,
    onBrightnessDrag: (Float) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = pageIndex, pageCount = { pdf.pageCount })
    LaunchedEffect(pageIndex) {
        if (pagerState.currentPage != pageIndex) pagerState.scrollToPage(pageIndex)
    }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != pageIndex) onPage(pagerState.currentPage)
    }
    val palette = settings.palette
    val filter = pdfColorFilter(settings.colorMode)
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            if (uri != null) {
                PdfPageImage(
                    uri = uri,
                    page = page,
                    colorFilter = filter,
                    contentDescription = stringResource(R.string.page_n, page + 1, pdf.pageCount),
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onToggleChrome() }
                        .background(palette.background),
                )
            }
        }
        if (settings.colorMode != ColorMode.Day) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(palette.background.copy(alpha = 0.22f)),
            )
        }
        EdgeGestures(onCycleColor, onBrightnessDrag)
    }
}

@Composable
private fun EdgeGestures(onCycleColor: () -> Unit, onBrightnessDrag: (Float) -> Unit) {
    Box(
        Modifier
            .fillMaxHeight()
            .width(28.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount -> onBrightnessDrag(dragAmount) },
                )
            }
            .clickable { onCycleColor() },
    )
}

private fun annotatedParagraph(text: String, highlight: String?, palette: ReadingPalette) =
    buildAnnotatedString {
        if (highlight.isNullOrBlank() || !text.contains(highlight)) {
            append(text)
            return@buildAnnotatedString
        }
        val start = text.indexOf(highlight)
        append(text.substring(0, start))
        withStyle(SpanStyle(background = palette.highlight, fontWeight = FontWeight.Medium)) {
            append(highlight)
        }
        append(text.substring(start + highlight.length))
    }

private fun pdfColorFilter(mode: ColorMode): ColorFilter? = when (mode) {
    ColorMode.Day -> null
    ColorMode.Night, ColorMode.Console, ColorMode.Twilight -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
    ColorMode.Sepia -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.9f, 0.1f, 0.05f, 0f, 20f,
                0.2f, 0.75f, 0.05f, 0f, 10f,
                0.1f, 0.15f, 0.55f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}

private fun applyBrightness(activity: Activity, settings: ReadingSettings) {
    val lp = activity.window.attributes
    lp.screenBrightness = if (settings.useSystemBrightness) {
        WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    } else {
        settings.brightness.coerceIn(0.02f, 1f)
    }
    activity.window.attributes = lp
}

private fun restoreBrightness(activity: Activity) {
    val lp = activity.window.attributes
    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    activity.window.attributes = lp
}
