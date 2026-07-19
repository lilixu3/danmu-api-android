package com.example.danmuapiapp.ui.compat

import android.content.Context
import android.content.Intent
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
import com.example.danmuapiapp.MainActivity
import com.example.danmuapiapp.R
import com.example.danmuapiapp.data.util.AppAppearancePrefs
import com.example.danmuapiapp.data.util.DeviceCompatMode
import com.example.danmuapiapp.domain.model.NightModePreference
import com.example.danmuapiapp.ui.theme.DanmuApiTheme

class CompatModeActivity : ComponentActivity() {

    private lateinit var compatViewModel: CompatModeViewModel

    override fun attachBaseContext(newBase: Context?) {
        if (newBase == null) {
            super.attachBaseContext(null)
            return
        }
        super.attachBaseContext(AppAppearancePrefs.wrapContextWithAppDpi(newBase, includeCompatMode = true))
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
                        onSetAppDpiOverride = { dpi ->
                            compatViewModel.setAppDpiOverride(this@CompatModeActivity, dpi)
                        },
                        onOpenProxyPicker = compatViewModel::openProxyPicker,
                        onSelectProxy = compatViewModel::selectProxy,
                        onRetestProxySpeed = compatViewModel::retestProxySpeed,
                        onConfirmProxySelection = compatViewModel::confirmProxySelection,
                        onDismissProxyPicker = compatViewModel::dismissProxyPickerDialog,
                        onDismissDependencyBlockedPrompt =
                            compatViewModel::dismissDependencyBlockedPrompt,
                        onExitToBackground = {
                            moveTaskToBack(true)
                        },
                        onStopServiceAndExit = {
                            compatViewModel.stopService()
                            finishAndRemoveTask()
                        },
                        onExitCompatMode = {
                            DeviceCompatMode.setNormalModeForced(this@CompatModeActivity, true)
                            Toast.makeText(
                                this@CompatModeActivity,
                                "已退出兼容模式，正在进入普通首页",
                                Toast.LENGTH_SHORT
                            ).show()
                            startActivity(
                                Intent(this@CompatModeActivity, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                }
                            )
                            overridePendingTransition(0, 0)
                            finish()
                        }
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
