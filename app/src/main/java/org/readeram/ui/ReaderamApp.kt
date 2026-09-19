package org.readeram.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.adaptive.collectFoldingFeaturesAsState
import androidx.window.core.layout.WindowWidthSizeClass
import androidx.window.layout.FoldingFeature
import kotlinx.coroutines.launch
import org.readeram.R
import org.readeram.ui.library.LibraryPane
import org.readeram.ui.reader.ReaderPane
import org.readeram.ui.theme.ReaderamTheme

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ReaderamApp() {
    ReaderamTheme {
        val navigator = rememberListDetailPaneScaffoldNavigator<String>()
        val scope = rememberCoroutineScope()
        val adaptive = currentWindowAdaptiveInfo()
        val foldingFeatures by collectFoldingFeaturesAsState()
        val tabletop = isTabletop(foldingFeatures)
        var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
        val notificationPermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { }
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        BackHandler(navigator.canNavigateBack()) {
            scope.launch { navigator.navigateBack() }
        }
        ListDetailPaneScaffold(
            directive = navigator.scaffoldDirective,
            value = navigator.scaffoldValue,
            listPane = {
                AnimatedPane {
                    LibraryPane(
                        selectedId = selectedId,
                        onOpen = { id ->
                            selectedId = id
                            scope.launch {
                                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id)
                            }
                        },
                        onRemoved = { id ->
                            if (selectedId == id) selectedId = null
                        },
                    )
                }
            },
            detailPane = {
                AnimatedPane {
                    val id = selectedId
                    if (id == null) {
                        val expanded = adaptive.windowSizeClass.windowWidthSizeClass != WindowWidthSizeClass.COMPACT
                        if (expanded) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.select_book))
                            }
                        }
                    } else {
                        val showBack = adaptive.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT
                        ReaderPane(
                            bookId = id,
                            onBack = if (showBack) {
                                {
                                    scope.launch { navigator.navigateBack() }
                                }
                            } else {
                                null
                            },
                            tabletop = tabletop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            },
        )
    }
}

private fun isTabletop(features: List<FoldingFeature>): Boolean {
    return features.any { feature ->
        feature.state == FoldingFeature.State.HALF_OPENED &&
            feature.orientation == FoldingFeature.Orientation.HORIZONTAL
    }
}
