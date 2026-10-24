package com.github.gbandszxc.goodtvplorer.ui.preview

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.github.gbandszxc.goodtvplorer.domain.FileSourceDataSource
import com.github.gbandszxc.goodtvplorer.domain.StreamingMedia
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AudioPlayMode(val label: String) {
    Sequential("顺序播放"),
    RepeatOne("单曲循环"),
    RepeatAll("列表循环"),
    Shuffle("随机播放");

    fun next(): AudioPlayMode = entries[(ordinal + 1) % entries.size]
}

data class AudioPlaybackState(
    val media: StreamingMedia? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val playMode: AudioPlayMode = AudioPlayMode.Sequential,
    val sleepTimerEndMs: Long? = null,
    val sleepTimerRemainingMs: Long = 0L,
    val error: String? = null,
)

data class AudioPlayerActions(
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onTogglePlayPause: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onCyclePlayMode: () -> Unit,
    val onStartSleepTimer: (Int) -> Unit,
    val onCancelSleepTimer: () -> Unit,
    val onBack: () -> Unit,
)

/**
 * 持有唯一 ExoPlayer 实例的音频播放控制器。
 *
 * 队列推进（上一首/下一首/自动续播）由 ViewModel 负责，控制器只负责单曲播放、
 * 播放状态发布与睡眠定时。
 */
@OptIn(UnstableApi::class)
class AudioPlaybackController(
    context: Context,
    private val scope: CoroutineScope,
    private val onEnded: () -> Unit,
    private val onSleepTimerExpired: () -> Unit,
) {
    private val appContext: Context = context.applicationContext
    private val player: ExoPlayer = ExoPlayer.Builder(appContext)
        .setAudioAttributes(AUDIO_ATTRIBUTES, true)
        .setHandleAudioBecomingNoisy(true)
        .build()

    private val _state = MutableStateFlow(AudioPlaybackState())
    val state: StateFlow<AudioPlaybackState> = _state.asStateFlow()

    private var loadedMedia: StreamingMedia? = null
    private var endedNotified = false
    private var ticker: Job? = null
    private var sleepTimerDeadlineMs: Long? = null
    private var released = false

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (released) return
            _state.update { it.copy(playing = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (released) return
            _state.update { it.copy(buffering = playbackState == Player.STATE_BUFFERING) }
            // 播放器重新进入可播放状态说明已离开 ENDED（重播/拖动），允许下一次自然结束再次回调。
            if (playbackState == Player.STATE_READY) endedNotified = false
            if (playbackState == Player.STATE_ENDED && !endedNotified) {
                endedNotified = true
                onEnded()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (released) return
            _state.update {
                it.copy(
                    playing = false,
                    buffering = false,
                    error = audioPlaybackErrorMessage(error),
                )
            }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            if (released) return
            _state.update {
                it.copy(
                    title = mediaMetadata.title?.toString().orEmpty(),
                    artist = mediaMetadata.artist?.toString().orEmpty(),
                    album = mediaMetadata.albumTitle?.toString().orEmpty(),
                )
            }
        }
    }

    init {
        player.addListener(listener)
    }

    fun load(media: StreamingMedia, playWhenReady: Boolean = true) {
        if (released) return
        val sameMedia = loadedMedia == media
        val ended = player.playbackState == Player.STATE_ENDED
        if (sameMedia && !ended) {
            _state.update { it.copy(media = media, error = null) }
            startTicker()
            if (playWhenReady) player.play()
            return
        }
        endedNotified = false
        loadedMedia = media
        _state.update {
            it.copy(
                media = media,
                playing = false,
                buffering = true,
                positionMs = 0L,
                durationMs = 0L,
                title = media.item.name,
                artist = "",
                album = "",
                error = null,
            )
        }
        val mediaSource = DefaultMediaSourceFactory(appContext)
            .setDataSourceFactory(FileSourceDataSource.Factory(media))
            .createMediaSource(MediaItem.fromUri(media.uri))
        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = playWhenReady
        startTicker()
    }

    fun togglePlayPause() {
        if (released) return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) {
        if (released) return
        val target = positionMs.coerceAtLeast(0L)
        player.seekTo(target)
        _state.update { it.copy(positionMs = target) }
    }

    fun seekBy(deltaMs: Long) {
        if (released) return
        val duration = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
        seekTo((player.currentPosition + deltaMs).coerceIn(0L, duration))
    }

    fun cyclePlayMode() {
        setPlayMode(_state.value.playMode.next())
    }

    fun setPlayMode(mode: AudioPlayMode) {
        if (released) return
        _state.update { it.copy(playMode = mode) }
    }

    fun startSleepTimer(minutes: Int) {
        if (released || minutes <= 0) return
        val remaining = minutes * 60_000L
        sleepTimerDeadlineMs = System.currentTimeMillis() + remaining
        _state.update { it.copy(sleepTimerEndMs = sleepTimerDeadlineMs, sleepTimerRemainingMs = remaining) }
    }

    fun cancelSleepTimer() {
        if (released) return
        sleepTimerDeadlineMs = null
        _state.update { it.copy(sleepTimerEndMs = null, sleepTimerRemainingMs = 0L) }
    }

    fun release() {
        if (released) return
        released = true
        ticker?.cancel()
        ticker = null
        sleepTimerDeadlineMs = null
        player.removeListener(listener)
        player.release()
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                refreshPlaybackState()
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    private fun refreshPlaybackState() {
        if (released) return
        val deadline = sleepTimerDeadlineMs
        val remaining = deadline?.let { (it - System.currentTimeMillis()).coerceAtLeast(0L) }
        _state.update {
            it.copy(
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = player.duration.takeIf { duration -> duration > 0L } ?: 0L,
                playing = player.isPlaying,
                buffering = player.playbackState == Player.STATE_BUFFERING,
                sleepTimerEndMs = deadline,
                sleepTimerRemainingMs = remaining ?: 0L,
            )
        }
        if (deadline != null && remaining == 0L) {
            sleepTimerDeadlineMs = null
            _state.update { it.copy(sleepTimerEndMs = null, sleepTimerRemainingMs = 0L) }
            player.pause()
            player.stop()
            if (!released) onSleepTimerExpired()
        }
    }

    private companion object {
        const val TICK_INTERVAL_MS = 250L

        val AUDIO_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
    }
}

/**
 * 计算队列中的下一首下标，null 表示顺序播放已到末尾、应当停止。
 *
 * @param size 队列长度
 * @param currentIndex 当前曲目下标
 * @param mode 播放模式
 * @param random 随机源，测试可传入固定种子
 */
internal fun nextQueueIndex(
    size: Int,
    currentIndex: Int,
    mode: AudioPlayMode,
    random: Random = Random.Default,
): Int? {
    if (size <= 0 || currentIndex !in 0 until size) return null
    return when (mode) {
        AudioPlayMode.Sequential -> (currentIndex + 1).takeIf { it < size }
        AudioPlayMode.RepeatOne -> currentIndex
        AudioPlayMode.RepeatAll -> (currentIndex + 1) % size
        AudioPlayMode.Shuffle -> when {
            size == 1 -> currentIndex
            else -> random.nextInt(size - 1).let { candidate ->
                if (candidate >= currentIndex) candidate + 1 else candidate
            }
        }
    }
}

/**
 * 计算队列中的上一首下标，null 表示没有可用的上一首。
 */
internal fun previousQueueIndex(size: Int, currentIndex: Int): Int? {
    if (size <= 0 || currentIndex !in 0 until size) return null
    return if (currentIndex > 0) currentIndex - 1 else 0
}

private fun audioPlaybackErrorMessage(error: PlaybackException): String = when {
    error.message?.contains("MediaCodecAudioRenderer") == true -> "当前设备不支持此音频编码"
    else -> "音频加载失败，请检查文件或网络连接"
}
