package com.example.danmuapiapp

import android.app.ActivityManager
import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.danmuapiapp.data.service.AppForegroundUpdateChecker
import com.example.danmuapiapp.data.service.AppUpdateService
import com.example.danmuapiapp.data.service.UpdateChecker
import com.example.danmuapiapp.data.util.AppAppearancePrefs
import com.example.danmuapiapp.domain.model.NightModePreference
import com.example.danmuapiapp.domain.repository.SettingsRepository
import com.example.danmuapiapp.ui.DanmuApiApp
import com.example.danmuapiapp.ui.theme.DanmuApiTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var updateChecker: UpdateChecker
    @Inject lateinit var appForegroundUpdateChecker: AppForegroundUpdateChecker
    @Inject lateinit var appUpdateService: AppUpdateService
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun attachBaseContext(newBase: Context?) {
        if (newBase == null) {
            super.attachBaseContext(null)
            return
        }
        super.attachBaseContext(AppAppearancePrefs.wrapContextWithAppDpi(newBase))
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsRepository.hideFromRecents.collect { hide ->
                    applyHideFromRecents(hide)
                }
            }
        }
        setContent {
            val nightMode by settingsRepository.nightMode.collectAsStateWithLifecycle()
            val darkTheme = when (nightMode) {
                NightModePreference.FollowSystem -> isSystemInDarkTheme()
                NightModePreference.Light -> false
                NightModePreference.Dark -> true
            }
            val view = LocalView.current
            SideEffect {
                val insetsController = WindowCompat.getInsetsController(window, view)
                window.statusBarColor = AndroidColor.TRANSPARENT
                window.navigationBarColor = AndroidColor.TRANSPARENT
                insetsController.isAppearanceLightStatusBars = !darkTheme
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    insetsController.isAppearanceLightNavigationBars = !darkTheme
                }
            }
            DanmuApiTheme(darkTheme = darkTheme) {
                DanmuApiApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateChecker.onAppResume()
        appForegroundUpdateChecker.onAppResume()
        appUpdateService.tryResumePendingInstall(this)
    }

    private fun applyHideFromRecents(hide: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        runCatching {
            val am = getSystemService(ACTIVITY_SERVICE) as? ActivityManager ?: return@runCatching
            am.appTasks.forEach { task ->
                task.setExcludeFromRecents(hide)
            }
        }
    }
}
