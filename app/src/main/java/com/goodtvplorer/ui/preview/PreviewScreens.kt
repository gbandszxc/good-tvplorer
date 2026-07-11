package com.goodtvplorer.ui.preview

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import com.goodtvplorer.ui.components.TvButton
import com.goodtvplorer.viewmodel.PreviewState
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

@Composable
fun ImagePreview(name: String, state: PreviewState, onBack: () -> Unit) {
    var loaded by remember(state.image) { mutableStateOf(false) }
    var loadError by remember(state.image) { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            state.loading -> {
                state.placeholder?.let {
                    AsyncImage(model = it, contentDescription = name, modifier = Modifier.fillMaxSize().padding(36.dp), contentScale = ContentScale.Fit)
                }
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            state.error != null -> {
                state.placeholder?.let {
                    AsyncImage(model = it, contentDescription = name, modifier = Modifier.fillMaxSize().padding(36.dp), contentScale = ContentScale.Fit)
                }
                Text(state.error, color = Color(0xFFFCA5A5), fontSize = 26.sp, modifier = Modifier.align(Alignment.Center))
            }
            state.image != null -> {
                if (!loaded && state.placeholder != null) {
                    AsyncImage(model = state.placeholder, contentDescription = name, modifier = Modifier.fillMaxSize().padding(36.dp), contentScale = ContentScale.Fit)
                }
                if (!loaded && state.placeholder == null && loadError == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                AsyncImage(
                    model = state.image,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize().padding(36.dp).alpha(if (loaded) 1f else 0f),
                    contentScale = ContentScale.Fit,
                    onSuccess = {
                        loaded = true
                        loadError = null
                    },
                    onError = { loadError = "图片加载失败，请检查 NAS 连接后重试。" },
                )
                loadError?.let {
                    Text(it, color = Color(0xFFFCA5A5), fontSize = 26.sp, modifier = Modifier.align(Alignment.Center))
                }
            }
        }
        Text(name, color = Color.White, fontSize = 24.sp, modifier = Modifier.align(Alignment.TopStart).padding(28.dp))
        TvButton("返回", modifier = Modifier.align(Alignment.TopEnd).padding(24.dp), onClick = onBack)
    }
}

@Composable
fun TextPreview(name: String, state: PreviewState, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFF101418)).padding(36.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = Color.White, fontSize = 28.sp)
            TvButton("返回", onClick = onBack)
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(state.error, color = Color(0xFFFCA5A5), fontSize = 26.sp) }
            else -> Column(Modifier.padding(top = 20.dp).verticalScroll(rememberScrollState()).focusable()) {
                if (state.truncated) Text("仅显示前 1MB", color = Color(0xFFD6F35F), fontSize = 20.sp)
                Text(state.text, color = Color(0xFFE5E7EB), fontSize = 24.sp, lineHeight = 34.sp)
            }
        }
    }
}

@Composable
fun AudioPreview(name: String, state: PreviewState, onBack: () -> Unit) {
    val context = LocalContext.current
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    val player = remember { ExoPlayer.Builder(context).build() }

    DisposableEffect(Unit) { onDispose { player.release() } }
    LaunchedEffect(state.file) {
        state.file?.let {
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(it)))
            player.prepare()
        }
    }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0)
            duration = player.duration.takeIf { it > 0 } ?: 0L
            playing = player.isPlaying
            delay(500)
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF101418)).padding(56.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = Color.White, fontSize = 32.sp)
            TvButton("返回", onClick = {
                player.stop()
                onBack()
            })
        }
        when {
            state.loading -> CircularProgressIndicator()
            state.error != null -> Text(state.error, color = Color(0xFFFCA5A5), fontSize = 26.sp)
            state.file != null -> {
                TvButton(if (playing) "暂停" else "播放") {
                    if (player.isPlaying) player.pause() else player.play()
                }
                LinearProgressIndicator(progress = { if (duration == 0L) 0f else position.toFloat() / duration }, modifier = Modifier.fillMaxWidth())
                Text("${format(position)} / ${format(duration)}", color = Color(0xFFCBD5E1), fontSize = 24.sp)
            }
        }
    }
}

@Composable
fun VideoPreview(name: String, state: PreviewState, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF05080D))) {
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.error != null -> Text(state.error, color = Color(0xFFFFA3A3), fontSize = 26.sp, modifier = Modifier.align(Alignment.Center))
            state.file != null -> AsyncImage(model = state.file, contentDescription = name, modifier = Modifier.fillMaxSize().padding(48.dp), contentScale = ContentScale.Fit)
            else -> Text("没有可用的视频缩略图", color = Color(0xFFC8D5E2), fontSize = 26.sp, modifier = Modifier.align(Alignment.Center))
        }
        Column(Modifier.align(Alignment.TopStart).padding(28.dp)) {
            Text(name, color = Color.White, fontSize = 26.sp, maxLines = 1)
            Text("视频预览 · 播放器后续接入", color = Color(0xFFFFC857), fontSize = 18.sp)
        }
        TvButton("返回", modifier = Modifier.align(Alignment.TopEnd).padding(24.dp), onClick = onBack)
    }
}

private fun format(ms: Long): String {
    val total = TimeUnit.MILLISECONDS.toSeconds(ms)
    return "%02d:%02d".format(total / 60, total % 60)
}
