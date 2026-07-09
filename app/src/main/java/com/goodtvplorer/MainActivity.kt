package com.goodtvplorer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.goodtvplorer.ui.browser.BrowserScreen
import com.goodtvplorer.ui.components.DisplaySettingsDialog
import com.goodtvplorer.ui.home.HomeScreen
import com.goodtvplorer.ui.preview.AudioPreview
import com.goodtvplorer.ui.preview.ImagePreview
import com.goodtvplorer.ui.preview.TextPreview
import com.goodtvplorer.ui.preview.VideoPreview
import com.goodtvplorer.ui.theme.TvTheme
import com.goodtvplorer.viewmodel.MainViewModel
import com.goodtvplorer.viewmodel.Screen

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TvTheme {
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
                LaunchedEffect(Unit) {
                    val permissions = if (Build.VERSION.SDK_INT >= 33) {
                        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
                    } else {
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                    launcher.launch(permissions)
                }
                val state by viewModel.state.collectAsState()
                var showDisplaySettings by remember { mutableStateOf(false) }
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, state.fontScale)) {
                    BackHandler {
                        if (showDisplaySettings) showDisplaySettings = false else viewModel.goBack()
                    }
                    when (val screen = state.screen) {
                        Screen.Home -> HomeScreen(
                            connections = state.smbConnections,
                            onLocal = viewModel::openLocal,
                            onOpenSmb = viewModel::openSmb,
                            onAddSmb = viewModel::addSmb,
                            onDisplaySettings = { showDisplaySettings = true },
                        )
                        is Screen.Browser -> BrowserScreen(
                            path = screen.path,
                            state = state.browser,
                            thumbnails = state.thumbnails,
                            viewMode = state.browserViewMode,
                            onOpen = viewModel::openItem,
                            onBack = viewModel::goBack,
                            onRefresh = viewModel::refresh,
                            onToggleView = viewModel::toggleBrowserViewMode,
                            onDisplaySettings = { showDisplaySettings = true },
                        )
                        is Screen.ImagePreview -> ImagePreview(screen.name, state.preview, viewModel::goBack)
                        is Screen.TextPreview -> TextPreview(screen.name, state.preview, viewModel::goBack)
                        is Screen.AudioPreview -> AudioPreview(screen.name, state.preview, viewModel::goBack)
                        is Screen.VideoPreview -> VideoPreview(screen.name, state.preview, viewModel::goBack)
                    }
                    if (showDisplaySettings) {
                        DisplaySettingsDialog(
                            fontScale = state.fontScale,
                            onFontScale = viewModel::setFontScale,
                            onDismiss = { showDisplaySettings = false },
                        )
                    }
                }
            }
        }
    }
}
