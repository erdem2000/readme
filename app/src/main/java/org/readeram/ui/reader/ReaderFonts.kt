package org.readeram.ui.reader

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.readeram.R

object ReaderFonts {
    val openDyslexic: FontFamily = FontFamily(
        Font(R.font.opendyslexic_regular, FontWeight.Normal),
        Font(R.font.opendyslexic_bold, FontWeight.Bold),
    )
}
