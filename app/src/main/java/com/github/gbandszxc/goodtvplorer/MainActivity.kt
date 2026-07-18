package com.github.gbandszxc.goodtvplorer

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.github.gbandszxc.goodtvplorer.domain.FileSourceImageFetcher
import com.github.gbandszxc.goodtvplorer.domain.ImageModelKeyer
import com.github.gbandszxc.goodtvplorer.data.effectiveFontScale
import com.github.gbandszxc.goodtvplorer.ui.browser.BrowserScreen
import com.github.gbandszxc.goodtvplorer.ui.main.MainDockLayout
import com.github.gbandszxc.goodtvplorer.ui.preview.AudioPreview
import com.github.gbandszxc.goodtvplorer.ui.preview.ImageViewer
import com.github.gbandszxc.goodtvplorer.ui.preview.TextPreview
import com.github.gbandszxc.goodtvplorer.ui.preview.VideoPreview
import com.github.gbandszxc.goodtvplorer.ui.theme.TvTheme
import com.github.gbandszxc.goodtvplorer.viewmodel.MainViewModel
import com.github.gbandszxc.goodtvplorer.viewmodel.Screen

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()
    private var lastGrantedMediaPermissions: Set<String>? = null
    private val mediaPermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        lastGrantedMediaPermissions = grantedMediaPermissions()
        viewModel.onMediaPermissionsChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context).diskCache { null }.components {
                add(ImageModelKeyer())
                add(FileSourceImageFetcher.Factory())
            }.build()
        }
        setContent {
            TvTheme {
                val state by viewModel.state.collectAsState()
                val immersivePreviewVisible = state.screen is Screen.ImageViewer || state.screen is Screen.VideoPreview
                LaunchedEffect(immersivePreviewVisible) {
                    WindowCompat.setDecorFitsSystemWindows(window, !immersivePreviewVisible)
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        if (immersivePreviewVisible) {
                            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            hide(WindowInsetsCompat.Type.systemBars())
                        } else {
                            show(WindowInsetsCompat.Type.systemBars())
                        }
                    }
                }
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
                            displayScale = state.fontScale,
                        ) { focusNavigation ->
                            if (screen is Screen.Browser) {
                                BrowserScreen(
                                    path = screen.path,
                                    canNavigateUp = screen.sourceKey != "local" || screen.path.isNotBlank(),
                                    navigation = focusNavigation,
                                    state = state.browser,
                                    thumbnails = state.thumbnails,
                                    viewMode = state.browserViewMode,
                                    sort = state.browserSort,
                                    searchQuery = state.browserSearchQuery,
                                    searchItems = state.browserSearchItems,
                                    searchLoading = state.browserSearchLoading,
                                    previewMetadata = state.browserPreviewMetadata,
                                    focusAnchorPath = state.focusAnchorPath,
                                    onOpen = viewModel::openItem,
                                    onNavigateUp = viewModel::goBack,
                                    onOpenPath = viewModel::openEnteredPath,
                                    onSortChange = viewModel::setBrowserSort,
                                    onSearchQueryChange = viewModel::setBrowserSearchQuery,
                                    onPreviewMetadataRequest = viewModel::requestBrowserPreviewMetadata,
                                    onThumbnailVisible = viewModel::requestThumbnail,
                                    onThumbnailHidden = viewModel::releaseThumbnail,
                                    displayScale = state.fontScale,
                                )
                            }
                        }
                        is Screen.ImageViewer -> ImageViewer(
                            name = screen.name,
                            selectedPath = screen.path,
                            state = state.preview,
                            images = state.previewItems,
                            thumbnails = state.thumbnails,
                            onPrevious = { viewModel.movePreviewItem(-1) },
                            onNext = { viewModel.movePreviewItem(1) },
                            onSelect = viewModel::selectPreviewItem,
                            onThumbnailVisible = viewModel::requestThumbnail,
                            onThumbnailHidden = viewModel::releaseThumbnail,
                            onBack = viewModel::goBack,
                        )
                        is Screen.TextPreview -> TextPreview(screen.name, state.preview, viewModel::goBack)
                        is Screen.AudioPreview -> AudioPreview(
                            name = screen.name,
                            state = state.preview,
                            onPrevious = { viewModel.movePreviewItem(-1) },
                            onNext = { viewModel.movePreviewItem(1) },
                            onBack = viewModel::goBack,
                        )
                        is Screen.VideoPreview -> VideoPreview(
                            name = screen.name,
                            state = state.preview,
                            onPrevious = { viewModel.movePreviewItem(-1) },
                            onNext = { viewModel.movePreviewItem(1) },
                            onBack = viewModel::goBack,
                        )
                    }
                }
            }
        }
        if (savedInstanceState == null) requestMissingMediaPermissions()
    }

    override fun onResume() {
        super.onResume()
        val granted = grantedMediaPermissions()
        if (lastGrantedMediaPermissions?.let { it != granted } == true) viewModel.onMediaPermissionsChanged()
        lastGrantedMediaPermissions = granted
    }

    private fun requestMissingMediaPermissions() {
        val granted = grantedMediaPermissions()
        lastGrantedMediaPermissions = granted
        missingMediaPermissions(Build.VERSION.SDK_INT, granted).takeIf(Array<String>::isNotEmpty)?.let(mediaPermissionsLauncher::launch)
    }

    private fun grantedMediaPermissions(): Set<String> = mediaPermissions(Build.VERSION.SDK_INT)
        .filterTo(mutableSetOf()) { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
}

@SuppressLint("InlinedApi")
internal fun mediaPermissions(sdkInt: Int): Array<String> = when {
    sdkInt >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        Manifest.permission.READ_MEDIA_AUDIO,
    )
    sdkInt >= 33 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_AUDIO,
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

@SuppressLint("InlinedApi")
internal fun missingMediaPermissions(sdkInt: Int, granted: Set<String>): Array<String> = buildList {
    if (sdkInt >= 34) {
        if (Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED !in granted) {
            if (Manifest.permission.READ_MEDIA_IMAGES !in granted) add(Manifest.permission.READ_MEDIA_IMAGES)
            if (Manifest.permission.READ_MEDIA_VIDEO !in granted) add(Manifest.permission.READ_MEDIA_VIDEO)
            if (isNotEmpty()) add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        }
        if (Manifest.permission.READ_MEDIA_AUDIO !in granted) add(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        addAll(mediaPermissions(sdkInt).filterNot(granted::contains))
    }
}.toTypedArray()
