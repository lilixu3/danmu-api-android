package com.example.danmuapiapp.ui.compat

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.R
import com.example.danmuapiapp.data.util.AppAppearancePrefs
import com.example.danmuapiapp.domain.model.NightModePreference
import com.example.danmuapiapp.ui.theme.DanmuApiTheme

class CompatModeActivity : ComponentActivity() {

    private lateinit var compatViewModel: CompatModeViewModel

    override fun attachBaseContext(newBase: Context?) {
        if (newBase == null) {
            super.attachBaseContext(null)
            return
        }
        super.attachBaseContext(AppAppearancePrefs.wrapContextWithAppDpi(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_DanmuApiApp)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        compatViewModel = ViewModelProvider(
            this,
            CompatModeViewModel.Factory(applicationContext)
        )[CompatModeViewModel::class.java]

        setContent {
            val uiState by compatViewModel.uiState.collectAsStateWithLifecycle()
            val darkTheme = when (uiState.nightMode) {
                NightModePreference.FollowSystem -> isSystemInDarkTheme()
                NightModePreference.Light -> false
                NightModePreference.Dark -> true
            }

            DanmuApiTheme(darkTheme = darkTheme) {
                val view = LocalView.current
                val systemBarColor = MaterialTheme.colorScheme.surface.toArgb()
                SideEffect {
                    val insetsController = WindowCompat.getInsetsController(window, view)
                    window.statusBarColor = systemBarColor
                    window.navigationBarColor = systemBarColor
                    insetsController.isAppearanceLightStatusBars = !darkTheme
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        insetsController.isAppearanceLightNavigationBars = !darkTheme
                    }
                }

                LaunchedEffect(compatViewModel) {
                    compatViewModel.events.collect { message ->
                        Toast.makeText(
                            this@CompatModeActivity,
                            message,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                CompatModeScreen(
                    uiState = uiState,
                    proxyPickerState = CompatProxyPickerState(
                        currentLabel = compatViewModel.currentProxyLabel(),
                        options = compatViewModel.proxyOptions,
                        selectedId = compatViewModel.proxySelectedId,
                        testingIds = compatViewModel.proxyTestingIds,
                        latencyMap = compatViewModel.proxyLatencyMap,
                        isVisible = compatViewModel.showProxyPickerDialog
                    ),
                    actions = CompatModeActions(
                        onStartService = compatViewModel::startService,
                        onRestartService = compatViewModel::restartService,
                        onStopService = compatViewModel::stopService,
                        onRefreshCoreInfo = compatViewModel::refreshCoreInfo,
                        onSwitchVariant = compatViewModel::switchVariant,
                        onInstallCore = compatViewModel::installCore,
                        onUpdateCore = compatViewModel::updateCore,
                        onCheckCoreUpdate = compatViewModel::checkCoreUpdate,
                        onDeleteCore = compatViewModel::deleteCore,
                        onSaveCustomCore = compatViewModel::saveCustomCore,
                        onToggleKeepAliveProfile = compatViewModel::toggleKeepAliveProfile,
                        onCheckAppUpdate = compatViewModel::checkAppUpdate,
                        onDownloadAppUpdate = compatViewModel::downloadAppUpdate,
                        onInstallAppUpdate = {
                            compatViewModel.installAppUpdate(this@CompatModeActivity)
                        },
                        onToggleNightMode = compatViewModel::toggleNightMode,
                        onOpenProxyPicker = compatViewModel::openProxyPicker,
                        onSelectProxy = compatViewModel::selectProxy,
                        onRetestProxySpeed = compatViewModel::retestProxySpeed,
                        onConfirmProxySelection = compatViewModel::confirmProxySelection,
                        onDismissProxyPicker = compatViewModel::dismissProxyPickerDialog
                    )
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::compatViewModel.isInitialized) {
            compatViewModel.onActivityResumed(this)
        }
    }
}
