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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.goodtvplorer.ui.browser.BrowserScreen
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
                BackHandler { viewModel.goBack() }
                when (val screen = state.screen) {
                    Screen.Home -> HomeScreen(
                        connections = state.smbConnections,
                        onLocal = viewModel::openLocal,
                        onOpenSmb = viewModel::openSmb,
                        onAddSmb = viewModel::addSmb,
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
                    )
                    is Screen.ImagePreview -> ImagePreview(screen.name, state.preview, viewModel::goBack)
                    is Screen.TextPreview -> TextPreview(screen.name, state.preview, viewModel::goBack)
                    is Screen.AudioPreview -> AudioPreview(screen.name, state.preview, viewModel::goBack)
                    is Screen.VideoPreview -> VideoPreview(screen.name, state.preview, viewModel::goBack)
                }
            }
        }
    }
}
