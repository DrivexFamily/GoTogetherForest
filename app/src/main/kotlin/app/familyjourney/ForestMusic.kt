package app.familyjourney

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 숲 배경 음악 "숲속 오후"(결정 #47, 2026-11-10 마스터 지시, 음원은 직접 합성 `tools/media/make_bgm.py`).
 *
 * - **숲 화면 + 주차 중 + 앱이 앞에 있을 때 + 켜짐**일 때만 작은 소리로 반복한다. 주행이 시작되거나 다른 화면·백그라운드로 가면 멈춘다.
 * - 가족이 이미 다른 음악·라디오를 듣고 있으면(다른 앱 재생 중) 시작하지 않는다. 남의 음악을 끊지 않기 위해서다.
 * - 오디오 포커스: 내비 안내 등에 잠깐 뺏기면 쉬고, 작게 해도 되면 더 작게, 아예 뺏기면 멈춘다.
 * - 켜짐/꺼짐은 기기에만 저장(아이 정보 아님). 처음은 켜짐.
 * - 음악이 실패해도 숲 화면은 그대로 동작한다.
 */
class ForestMusic(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("forest_music", Context.MODE_PRIVATE)
    private val audio = app.getSystemService(AudioManager::class.java)
    private val onState = MutableStateFlow(prefs.getBoolean(KEY_ON, true))
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var wanted = false

    /** 켜짐 설정 */
    val on: StateFlow<Boolean> = onState.asStateFlow()

    fun toggle() {
        val next = !onState.value
        onState.value = next
        prefs.edit().putBoolean(KEY_ON, next).apply()
        update(wanted)
    }

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> player?.let { it.setVolume(VOLUME, VOLUME); if (wanted && onState.value) it.start() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.setVolume(DUCK, DUCK)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> player?.takeIf { it.isPlaying }?.pause()
            AudioManager.AUDIOFOCUS_LOSS -> stop()
        }
    }

    /** 화면 쪽에서 "지금 틀어도 되는 상황인가"를 알린다 */
    fun update(shouldPlay: Boolean) {
        wanted = shouldPlay
        if (shouldPlay && onState.value) start() else stop()
    }

    private fun start() {
        if (player?.isPlaying == true) return
        try {
            // 다른 앱 음악이 나오고 있으면 끼어들지 않는다(우리 것은 아직 안 나옴)
            if (player == null && audio?.isMusicActive == true) return
            val request = focus ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(focusListener)
                .build().also { focus = it }
            if (audio?.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
            val p = player ?: MediaPlayer.create(app, R.raw.ggf_forest_bgm, attributes, audio.generateAudioSessionId())
                ?.apply { isLooping = true }
                ?.also { player = it }
                ?: return
            p.setVolume(VOLUME, VOLUME)
            p.start()
        } catch (e: RuntimeException) {
            Log.w(TAG, "숲 음악을 틀지 못함: ${e.javaClass.simpleName}")
            stop()
        }
    }

    private fun stop() {
        player?.let { p ->
            runCatching { if (p.isPlaying) p.stop() }
            p.release()
        }
        player = null
        focus?.let { runCatching { audio?.abandonAudioFocusRequest(it) } }
    }

    private companion object {
        const val TAG = "ForestMusic"
        const val KEY_ON = "on"
        const val VOLUME = 0.35f
        const val DUCK = 0.12f
    }
}

/** 이 화면이 보이는 동안 [active]면 음악을 틀고, 화면을 떠나거나 앱이 뒤로 가면 멈춘다 */
@Composable
internal fun ForestMusicEffect(music: ForestMusic, active: Boolean) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activeNow by rememberUpdatedState(active)
    DisposableEffect(lifecycle, music, active) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> music.update(activeNow)
                Lifecycle.Event.ON_STOP -> music.update(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        music.update(active && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose {
            lifecycle.removeObserver(observer)
            music.update(false)
        }
    }
}
