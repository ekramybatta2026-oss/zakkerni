package com.ultimateiptv.sports

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimateiptv.sports.data.settings.SettingsRepository
import com.ultimateiptv.sports.ui.components.TimeFormat
import com.ultimateiptv.sports.ui.navigation.AppRoot
import com.ultimateiptv.sports.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _pendingMatch = MutableStateFlow<String?>(null)
    val pendingMatch: StateFlow<String?> = _pendingMatch.asStateFlow()

    init {
        viewModelScope.launch {
            settings.settings.collect {
                TimeFormat.use24h = it.use24h
                _ready.value = true
            }
        }
    }

    fun openMatch(id: String?) { _pendingMatch.value = id }
    fun consumeMatch() { _pendingMatch.value = null }
}

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !viewModel.ready.value }
        enableEdgeToEdge()
        viewModel.openMatch(intent?.getStringExtra(EXTRA_MATCH_ID))
        setContent { AppTheme { AppRoot(viewModel) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_MATCH_ID)?.let(viewModel::openMatch)
    }

    companion object {
        const val EXTRA_MATCH_ID = "match_id"
    }
}
