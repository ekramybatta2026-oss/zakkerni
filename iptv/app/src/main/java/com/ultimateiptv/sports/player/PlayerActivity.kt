package com.ultimateiptv.sports.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ultimateiptv.sports.BuildConfig
import com.ultimateiptv.sports.R
import com.ultimateiptv.sports.data.iptv.ChannelContext
import com.ultimateiptv.sports.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PlayerActivity : AppCompatActivity() {

    private val viewModel: PlayerViewModel by viewModels()
    private var inPip by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            val externalReady by viewModel.externalReady.collectAsState()
            LaunchedEffect(externalReady) {
                if (externalReady) { openExternal(); finish() }
            }
            AppTheme {
                PlayerScreen(
                    viewModel = viewModel,
                    inPip = inPip,
                    onBack = { finish() },
                    onExternal = ::openExternal,
                )
            }
        }
    }

    /** Debug builds log key routing (helps diagnose remote-control navigation). */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val handled = super.dispatchKeyEvent(event)
        if (BuildConfig.DEBUG) android.util.Log.d("KeyDebug", "${javaClass.simpleName} ${android.view.KeyEvent.keyCodeToString(event.keyCode)} action=${event.action} handled=$handled")
        return handled
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPipIfPossible()
    }

    private fun enterPipIfPossible() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        if (!viewModel.player.isPlaying) return
        runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        // Do not keep downloading video in the background (unless shown in picture-in-picture).
        if (!inPip) viewModel.player.pause()
    }

    override fun onStart() {
        super.onStart()
        if (viewModel.player.playbackState != androidx.media3.common.Player.STATE_IDLE) viewModel.player.play()
    }

    /** Opens the current stream in another installed player (MX Player, VLC, Just Player...). */
    private fun openExternal() {
        val url = viewModel.currentAttemptUrl() ?: return
        val headers = viewModel.currentHeaders()
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), "video/*")
            putExtra("title", viewModel.state.value.title)
            // MX Player / Just Player convention: alternating name/value array.
            putExtra("headers", headers.flatMap { listOf(it.key, it.value) }.toTypedArray())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            viewModel.player.pause()
            startActivity(Intent.createChooser(intent, getString(R.string.player_open_with)))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.player_no_external, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val EXTRA_UID = "uid"
        const val EXTRA_CONTEXT = "context"

        fun intent(context: Context, uid: String, channelContext: ChannelContext): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_UID, uid)
                .putExtra(EXTRA_CONTEXT, channelContext.encode())
    }
}
