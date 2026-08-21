package com.example.danmuapiapp.ui.compat

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.MainActivity
import com.example.danmuapiapp.R
import com.example.danmuapiapp.data.util.AppAppearancePrefs
import com.example.danmuapiapp.data.util.DeviceCompatMode
import com.example.danmuapiapp.domain.model.NightModePreference
import com.example.danmuapiapp.ui.startup.LocalNetworkPermissionAction
import com.example.danmuapiapp.ui.startup.LocalNetworkPermissionPolicy
import com.example.danmuapiapp.ui.startup.StartupPermissionGatePrefs
import com.example.danmuapiapp.ui.theme.DanmuApiTheme

class CompatModeActivity : ComponentActivity() {

    private lateinit var compatViewModel: CompatModeViewModel
    private var localNetworkPermissionState by mutableStateOf(
        LocalNetworkPermissionPolicy.stateFor(
            sdkInt = 0,
            granted = true,
            requestAttempted = false
        )
    )
    private var localNetworkShouldShowRationale by mutableStateOf(false)
    private val localNetworkPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshLocalNetworkPermissionState()
        if (granted) {
            Toast.makeText(this, "已允许局域网访问", Toast.LENGTH_SHORT).show()
        }
    }

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
        refreshLocalNetworkPermissionState()

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

            DanmuApiTheme(
                darkTheme = darkTheme,
                glassMaterial = uiState.glassMaterial
            ) {
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
                    showDependencyRequiredPrompt = compatViewModel.showDependencyRequiredPrompt,
                    showDependencyRepairDialog = compatViewModel.showDependencyRepairDialog,
                    showLocalNetworkPermissionHint =
                        LocalNetworkPermissionPolicy.shouldShowAddressHint(localNetworkPermissionState),
                    onOpenLocalNetworkPermission = ::openLocalNetworkPermissionFlow,
                    actions = CompatModeActions(
                        onStartService = compatViewModel::startService,
                        onRestartService = compatViewModel::restartService,
                        onStopService = compatViewModel::stopService,
                        onRefreshCoreInfo = compatViewModel::refreshCoreInfo,
                        onSwitchVariant = compatViewModel::switchVariant,
                        onInstallCore = compatViewModel::installCore,
                        onUpdateCore = compatViewModel::updateCore,
                        onCheckCoreUpdate = compatViewModel::checkCoreUpdate,
                        onOpenBranchPicker = compatViewModel::openBranchDialog,
                        onRetryBranches = compatViewModel::retryLoadBranches,
                        onSwitchCoreBranch = compatViewModel::switchCoreBranch,
                        onDismissBranchPicker = compatViewModel::dismissBranchDialog,
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
                        onOpenDependencyRepair = compatViewModel::openDependencyRepairDialog,
                        onDismissDependencyRequired = compatViewModel::dismissDependencyRequiredPrompt,
                        onRepairDependenciesOnline = compatViewModel::repairPendingDependenciesOnline,
                        onRepairDependenciesFromArchive =
                            compatViewModel::repairPendingDependenciesFromArchive,
                        onCancelPendingCoreMutation = compatViewModel::discardPendingCoreMutation,
                        onDismissDependencyRepair = compatViewModel::dismissDependencyRepairDialog,
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

                if (
                    LocalNetworkPermissionPolicy.shouldShowCompatGuide(
                        state = localNetworkPermissionState,
                        dismissedThisLaunch = uiState.localNetworkGuideDismissedThisLaunch
                    )
                ) {
                    val action = resolveLocalNetworkPermissionAction()
                    CompatLocalNetworkPermissionDialog(
                        openSettings = action == LocalNetworkPermissionAction.Settings,
                        onGrant = ::openLocalNetworkPermissionFlow,
                        onContinueLocalOnly = compatViewModel::dismissLocalNetworkGuideForThisLaunch
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshLocalNetworkPermissionState()
        if (::compatViewModel.isInitialized) {
            compatViewModel.onActivityResumed(this)
        }
    }

    private fun refreshLocalNetworkPermissionState() {
        val required = Build.VERSION.SDK_INT >= LocalNetworkPermissionPolicy.ANDROID_17_API_LEVEL
        val granted = required.not() || ContextCompat.checkSelfPermission(
            this,
            LocalNetworkPermissionPolicy.PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
        localNetworkShouldShowRationale = required && granted.not() &&
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                LocalNetworkPermissionPolicy.PERMISSION
            )
        localNetworkPermissionState = LocalNetworkPermissionPolicy.stateFor(
            sdkInt = Build.VERSION.SDK_INT,
            granted = granted,
            requestAttempted = StartupPermissionGatePrefs.hasRequestedLocalNetworkPermission(this)
        )
    }

    private fun resolveLocalNetworkPermissionAction(): LocalNetworkPermissionAction? {
        if (localNetworkPermissionState.ready) return null
        return LocalNetworkPermissionPolicy.resolveAction(
            state = localNetworkPermissionState,
            hasActivity = true,
            shouldShowRationale = localNetworkShouldShowRationale
        )
    }

    private fun openLocalNetworkPermissionFlow() {
        when (resolveLocalNetworkPermissionAction()) {
            LocalNetworkPermissionAction.Request -> {
                StartupPermissionGatePrefs.markLocalNetworkPermissionRequested(this)
                localNetworkPermissionLauncher.launch(LocalNetworkPermissionPolicy.PERMISSION)
            }

            LocalNetworkPermissionAction.Settings -> {
                val opened = runCatching {
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = "package:$packageName".toUri()
                        }
                    )
                    true
                }.getOrDefault(false)
                if (opened.not()) {
                    Toast.makeText(this, "请在应用设置中开启局域网访问权限", Toast.LENGTH_SHORT).show()
                }
            }

            null -> refreshLocalNetworkPermissionState()
        }
    }
}
