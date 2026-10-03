package com.example.danmuapiapp.ui.compat

import com.example.danmuapiapp.ui.component.CorePullRequestUpdateChoiceHost
import android.Manifest
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
import androidx.core.view.doOnPreDraw
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.ui.screen.settings.applyInterfaceMode
import com.example.danmuapiapp.R
import com.example.danmuapiapp.data.util.AppAppearancePrefs
import com.example.danmuapiapp.data.service.RuntimeWarmupCoordinator
import com.example.danmuapiapp.data.service.AppDiagnosticLogger
import com.example.danmuapiapp.data.service.NodeKeepAlivePrefs
import com.example.danmuapiapp.ui.screen.home.NormalModeKeepAliveGuideNavigator
import com.example.danmuapiapp.domain.model.GlassMaterialPreference
import com.example.danmuapiapp.domain.model.NightModePreference
import com.example.danmuapiapp.ui.startup.LocalNetworkPermissionAction
import com.example.danmuapiapp.ui.startup.LocalNetworkPermissionPolicy
import com.example.danmuapiapp.ui.startup.StartupPermissionGatePrefs
import com.example.danmuapiapp.ui.theme.DanmuApiTheme

class CompatModeActivity : ComponentActivity() {

    private lateinit var compatViewModel: CompatModeViewModel
    private var runtimeInitialized by mutableStateOf(false)
    private var startupError by mutableStateOf<String?>(null)
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

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        if (::compatViewModel.isInitialized) compatViewModel.refreshBackgroundPermissions()
    }

    private fun openNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NodeKeepAlivePrefs.hasPostNotificationsPermission(this) &&
            (!StartupPermissionGatePrefs.hasRequestedNotificationPermission(this) ||
                ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS))
        ) {
            StartupPermissionGatePrefs.markNotificationPermissionRequested(this)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        val opened = runCatching {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            })
            true
        }.getOrDefault(false)
        if (!opened) {
            runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())) }
                .onFailure { Toast.makeText(this, "当前系统未提供权限设置入口", Toast.LENGTH_SHORT).show() }
        }
    }

    override fun attachBaseContext(newBase: Context?) {
        if (newBase == null) {
            super.attachBaseContext(null)
            return
        }
        super.attachBaseContext(AppAppearancePrefs.wrapContextWithAppDpi(newBase, compat = true))
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_DanmuApiApp)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        refreshLocalNetworkPermissionState()

        val startupNightMode = AppAppearancePrefs.readNightMode(
            getSharedPreferences(AppAppearancePrefs.PREFS_UI_LEGACY, MODE_PRIVATE)
        )
        setContent {
            if (!runtimeInitialized) {
                val darkTheme = when (startupNightMode) {
                    NightModePreference.FollowSystem -> isSystemInDarkTheme()
                    NightModePreference.Light -> false
                    NightModePreference.Dark -> true
                }
                CompatStartupScreen(darkTheme, RuntimeWarmupCoordinator.UiState.NotStarted,
                    startupError, ::initializeRuntime)
                return@setContent
            }
            val startupState by compatViewModel.startupCoordinator.uiState.collectAsStateWithLifecycle()
            val uiState by compatViewModel.uiState.collectAsStateWithLifecycle()
            val darkTheme = when (uiState.nightMode) {
                NightModePreference.FollowSystem -> isSystemInDarkTheme()
                NightModePreference.Light -> false
                NightModePreference.Dark -> true
            }

            DanmuApiTheme(
                darkTheme = darkTheme,
                glassMaterial = GlassMaterialPreference.Off,
                glassTuning = uiState.glassTuning,
                appBackground = uiState.appBackground
            ) {
                CompatConsoleTheme(dark = darkTheme) {
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

                    if (startupState != RuntimeWarmupCoordinator.UiState.Ready) {
                        CompatStartupContent(startupState)
                        return@CompatConsoleTheme
                    }
                    CorePullRequestUpdateChoiceHost(compatViewModel.coreUpdateChoice)
                    val management by compatViewModel.managementState.collectAsStateWithLifecycle()
                    val logs by compatViewModel.logs.collectAsStateWithLifecycle()
                    CompatModeScreen(
                        uiState = uiState,
                        management = management,
                        logs = logs,
                        onPageOpened = compatViewModel::onPageOpened,
                        onRefreshLogs = compatViewModel::refreshLogs,
                        onRefreshConfig = compatViewModel.managementController::refresh,
                        onAdminLogin = compatViewModel.managementController::login,
                        onSaveConfig = compatViewModel.managementController::save,
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
                            onOpenNotificationPermission = ::openNotificationPermission,
                            onOpenBatterySettings = {
                                val opened = NormalModeKeepAliveGuideNavigator.requestIgnoreBatteryOptimization(this@CompatModeActivity)
                                if (!opened) Toast.makeText(this@CompatModeActivity, "当前系统未提供后台权限设置入口", Toast.LENGTH_SHORT).show()
                            },
                            onCheckAppUpdate = compatViewModel::checkAppUpdate,
                            onDownloadAppUpdate = compatViewModel::downloadAppUpdate,
                            onInstallAppUpdate = {
                                compatViewModel.installAppUpdate(this@CompatModeActivity)
                            },
                            onToggleNightMode = compatViewModel::toggleNightMode,
                            onSetIpv6Enabled = compatViewModel::setIpv6Enabled,
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
                            onSetInterfaceMode = { mode ->
                                applyInterfaceMode(this@CompatModeActivity, mode)
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
        // Post repository creation after the first draw, so slow storage never
        // leaves the user staring at an empty window while dependencies are prepared.
        window.decorView.doOnPreDraw { view -> view.post { initializeRuntime() } }
    }

    private fun initializeRuntime() {
        if (isFinishing || isDestroyed || runtimeInitialized) return
        startupError = null
        try {
            compatViewModel = ViewModelProvider(
                this,
                CompatModeViewModel.Factory(applicationContext)
            )[CompatModeViewModel::class.java]
            // onResume may have already run while the first frame was being drawn.
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                compatViewModel.onActivityResumed(this)
            }
            runtimeInitialized = true
        } catch (error: Exception) {
            AppDiagnosticLogger.w(this, "CompatStartup", "兼容界面初始化失败", error)
            startupError = "初始化未完成，请重试。若仍失败，请检查设备剩余存储空间。"
        }
    }

    override fun onResume() {
        super.onResume()
        refreshLocalNetworkPermissionState()
        if (::compatViewModel.isInitialized) {
            compatViewModel.onActivityResumed(this)
        }
    }

    override fun onStop() {
        if (::compatViewModel.isInitialized) {
            compatViewModel.onActivityStopped()
        }
        super.onStop()
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
