package com.xingyiyao.mathinterviewvocab.audio

import android.content.Context
import android.net.Uri
import android.os.Handler
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.BandwidthMeter
import androidx.media3.datasource.AssetDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class SpeechUiState(val status: String = "idle", val message: String = "")

/** 只有本地 asset URI，没有网络客户端或系统 TTS 回退。调用方在主线程操作。 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class SpeechController(context: Context) {
    private val assets = context.applicationContext.assets
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(SpeechUiState())
    val state: StateFlow<SpeechUiState> = mutableState.asStateFlow()
    private var inventory: AudioInventory? = null
    private var request: Job? = null
    private val resourceLock = Mutex()
    private val verifiedFiles = mutableSetOf<String>()
    private val player = ExoPlayer.Builder(context.applicationContext)
        .setMediaSourceFactory(DefaultMediaSourceFactory(DataSource.Factory { AssetDataSource(context.applicationContext) }))
        .setBandwidthMeter(LocalBandwidthMeter)
        .build().apply {
        setAudioAttributes(
            AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .setUsage(C.USAGE_MEDIA).build(), true,
        )
        setHandleAudioBecomingNoisy(true)
        addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) mutableState.value = SpeechUiState()
            }
            override fun onPlayerError(error: PlaybackException) {
                mutableState.value = SpeechUiState("error", "离线发音播放失败，请重试。")
            }
        })
    }

    fun speak(text: String, accent: String, speed: Double) {
        stop()
        if (accent !in AudioInventory.accents || speed !in setOf(0.75, 1.0, 1.25)) {
            mutableState.value = SpeechUiState("error", "无效的发音设置。")
            return
        }
        request = scope.launch {
            try {
                val entry = withContext(Dispatchers.IO) { resourceLock.withLock {
                    val index = inventory ?: assets.open("audio-manifest.json").bufferedReader().use {
                        AudioInventory.parse(it.readText())
                    }.also { inventory = it }
                    val found = index.find(text, accent) ?: return@withLock null
                    if (found.file !in verifiedFiles) {
                        val bytes = assets.open("audio/${found.file}").use { it.readBytes() }
                        check(bytes.size.toLong() == found.bytes && AudioInventory.sha256(bytes) == found.sha256)
                        verifiedFiles += found.file
                    }
                    found
                } }
                if (entry == null) {
                    mutableState.value = SpeechUiState("missing", "所选内容未收录离线音频，请选择完整单词、词组或朗读完整词条。")
                } else {
                    player.setMediaItem(MediaItem.fromUri(Uri.parse("asset:///audio/${entry.file}")))
                    player.setPlaybackSpeed(speed.toFloat())
                    player.prepare()
                    player.play()
                    mutableState.value = SpeechUiState("playing", "正在播放${if (accent == "en-US") "美式" else "英式"}离线发音")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.value = SpeechUiState("error", "离线音频读取或校验失败，请重新下载完整安装包。")
            }
        }
    }

    fun stop() {
        request?.cancel()
        request = null
        player.stop()
        player.clearMediaItems()
        mutableState.value = SpeechUiState()
    }

    // 本地 MP3 不需要监听网络类型或测量网络带宽。
    private object LocalBandwidthMeter : BandwidthMeter {
        override fun getBitrateEstimate(): Long = 0
        override fun getTransferListener(): TransferListener? = null
        override fun addEventListener(eventHandler: Handler, eventListener: BandwidthMeter.EventListener) = Unit
        override fun removeEventListener(eventListener: BandwidthMeter.EventListener) = Unit
    }
}
