package org.readeram.ui.reader

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readeram.ReaderamApplication

@Composable
fun PdfPageImage(
    uri: String,
    page: Int,
    colorFilter: ColorFilter?,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as ReaderamApplication
    val bitmap by produceState<Bitmap?>(initialValue = null, uri, page) {
        value = withContext(Dispatchers.IO) {
            app.container.library.renderPdfPage(Uri.parse(uri), page)
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CircularProgressIndicator()
        }
    }
}
