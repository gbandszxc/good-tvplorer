package com.goodtvplorer

import android.Manifest
import android.content.Intent
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.goodtvplorer.domain.FileSourceImageFetcher
import com.goodtvplorer.domain.ImageModelKeyer
import com.goodtvplorer.data.effectiveFontScale
import com.goodtvplorer.ui.browser.BrowserScreen
import com.goodtvplorer.ui.main.MainDockLayout
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
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context).components {
                add(ImageModelKeyer())
                add(FileSourceImageFetcher.Factory())
            }.build()
        }
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
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, effectiveFontScale(state.fontScale))) {
                    BackHandler(onBack = viewModel::goBack)
                    when (val screen = state.screen) {
                        is Screen.Browser, Screen.Network -> MainDockLayout(
                            networkSelected = screen is Screen.Network || (screen is Screen.Browser && screen.sourceKey.startsWith("smb:")),
                            showNetworkHub = screen is Screen.Network,
                            connections = state.smbConnections,
                            onLocal = viewModel::openLocal,
                            onNetwork = viewModel::openNetwork,
                            onOpenSmb = viewModel::openSmb,
                            onConnections = { startActivity(Intent(this@MainActivity, ConnectionManagementActivity::class.java)) },
                            onSettings = { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) },
                            browserViewMode = if (screen is Screen.Browser) state.browserViewMode else null,
                            onToggleView = viewModel::toggleBrowserViewMode,
                            onRefresh = viewModel::refresh,
                            onBack = viewModel::goBack,
                        ) {
                            if (screen is Screen.Browser) {
                                BrowserScreen(
                                    path = screen.path,
                                    state = state.browser,
                                    thumbnails = state.thumbnails,
                                    viewMode = state.browserViewMode,
                                    sort = state.browserSort,
                                    searchQuery = state.browserSearchQuery,
                                    searchItems = state.browserSearchItems,
                                    searchLoading = state.browserSearchLoading,
                                    focusAnchorPath = state.focusAnchorPath,
                                    onOpen = viewModel::openItem,
                                    onOpenPath = viewModel::openEnteredPath,
                                    onSortChange = viewModel::setBrowserSort,
                                    onSearchQueryChange = viewModel::setBrowserSearchQuery,
                                    onThumbnailVisible = viewModel::requestThumbnail,
                                    onThumbnailHidden = viewModel::releaseThumbnail,
                                )
                            }
                        }
                        is Screen.ImagePreview -> ImagePreview(screen.name, state.preview, viewModel::goBack)
                        is Screen.TextPreview -> TextPreview(screen.name, state.preview, viewModel::goBack)
                        is Screen.AudioPreview -> AudioPreview(screen.name, state.preview, viewModel::goBack)
                        is Screen.VideoPreview -> VideoPreview(screen.name, state.preview, viewModel::goBack)
                    }
                }
            }
        }
    }
}
