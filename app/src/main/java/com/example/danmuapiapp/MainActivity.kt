package com.example.danmuapiapp

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.danmuapiapp.data.service.AppForegroundUpdateChecker
import com.example.danmuapiapp.data.service.AppForegroundAnnouncementChecker
import com.example.danmuapiapp.data.service.AppUpdateService
import com.example.danmuapiapp.data.service.RuntimeWarmupCoordinator
import com.example.danmuapiapp.data.service.UpdateChecker
import com.example.danmuapiapp.data.util.AppAppearancePrefs
import com.example.danmuapiapp.data.util.DeviceCompatMode
import com.example.danmuapiapp.domain.model.NightModePreference
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.domain.repository.SettingsRepository
import com.example.danmuapiapp.ui.DanmuApiApp
import com.example.danmuapiapp.ui.compat.CompatModeActivity
import com.example.danmuapiapp.ui.theme.DanmuApiTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var updateChecker: UpdateChecker
    @Inject lateinit var appForegroundUpdateChecker: AppForegroundUpdateChecker
    @Inject lateinit var appForegroundAnnouncementChecker: AppForegroundAnnouncementChecker
    @Inject lateinit var appUpdateService: AppUpdateService
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var runtimeRepository: RuntimeRepository
    @Inject lateinit var runtimeWarmupCoordinator: RuntimeWarmupCoordinator

    override fun attachBaseContext(newBase: Context?) {
        if (newBase == null) {
            super.attachBaseContext(null)
            return
        }
        super.attachBaseContext(AppAppearancePrefs.wrapContextWithAppDpi(newBase))
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        if (DeviceCompatMode.shouldUseCompatMode(this)) {
            super.onCreate(savedInstanceState)
            startActivity(Intent(this, CompatModeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            })
            finish()
            overridePendingTransition(0, 0)
            return
        }

        val splashScreen = installSplashScreen()
        val splashStartedAt = System.currentTimeMillis()
        splashScreen.setKeepOnScreenCondition {
            runtimeWarmupCoordinator.uiState.value is RuntimeWarmupCoordinator.UiState.NotStarted &&
                System.currentTimeMillis() - splashStartedAt < 1_500L
        }
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        runtimeWarmupCoordinator.startIfNeeded()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsRepository.hideFromRecents.collect { hide ->
                    applyHideFromRecents(hide)
                }
            }
        }

        setContent {
            val nightMode by settingsRepository.nightMode.collectAsStateWithLifecycle()
            val glassMaterial by settingsRepository.glassMaterial.collectAsStateWithLifecycle()
            val appBackground by settingsRepository.appBackground.collectAsStateWithLifecycle()
            val startupUiState by runtimeWarmupCoordinator.uiState.collectAsStateWithLifecycle()
            val darkTheme = when (nightMode) {
                NightModePreference.FollowSystem -> isSystemInDarkTheme()
                NightModePreference.Light -> false
                NightModePreference.Dark -> true
            }
            DanmuApiTheme(
                darkTheme = darkTheme,
                glassMaterial = glassMaterial,
                appBackground = appBackground
            ) {
                val view = LocalView.current
                SideEffect {
                    val insetsController = WindowCompat.getInsetsController(window, view)
                    window.statusBarColor = Color.Transparent.toArgb()
                    window.navigationBarColor = Color.Transparent.toArgb()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        window.isNavigationBarContrastEnforced = false
                    }
                    insetsController.isAppearanceLightStatusBars = !darkTheme
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        insetsController.isAppearanceLightNavigationBars = !darkTheme
                    }
                }
                DanmuApiApp(startupUiState = startupUiState)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        runtimeRepository.setAppForeground(true)
        updateChecker.onAppResume()
        appForegroundUpdateChecker.onAppResume()
        appForegroundAnnouncementChecker.onAppResume()
        appUpdateService.tryResumePendingInstall(this)
    }

    override fun onStop() {
        runtimeRepository.setAppForeground(false)
        super.onStop()
    }

    private fun applyHideFromRecents(hide: Boolean) {
        runCatching {
            val am = getSystemService(ACTIVITY_SERVICE) as? ActivityManager ?: return@runCatching
            am.appTasks.forEach { task ->
                task.setExcludeFromRecents(hide)
            }
        }
    }
}
