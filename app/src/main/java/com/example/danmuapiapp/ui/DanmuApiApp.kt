package com.example.danmuapiapp.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.danmuapiapp.data.service.RuntimeWarmupCoordinator
import com.example.danmuapiapp.ui.navigation.Screen
import com.example.danmuapiapp.ui.navigation.SettingsRoute
import com.example.danmuapiapp.ui.navigation.ToolRoute
import com.example.danmuapiapp.ui.screen.apitest.ApiTestScreen
import com.example.danmuapiapp.ui.screen.cache.CacheManagementScreen
import com.example.danmuapiapp.ui.screen.config.ConfigScreen
import com.example.danmuapiapp.ui.screen.console.ConsoleScreen
import com.example.danmuapiapp.ui.screen.core.CoreScreen
import com.example.danmuapiapp.ui.screen.deviceaccess.DeviceAccessScreen
import com.example.danmuapiapp.ui.screen.download.DanmuDownloadScreen
import com.example.danmuapiapp.ui.screen.home.HomeScreen
import com.example.danmuapiapp.ui.screen.push.PushDanmuScreen
import com.example.danmuapiapp.ui.screen.records.RequestRecordsScreen
import com.example.danmuapiapp.ui.screen.settings.AboutScreen
import com.example.danmuapiapp.ui.screen.settings.AdminModeScreen
import com.example.danmuapiapp.ui.screen.settings.BackupRestoreScreen
import com.example.danmuapiapp.ui.screen.settings.DownloadSettingsScreen
import com.example.danmuapiapp.ui.screen.settings.GithubTokenScreen
import com.example.danmuapiapp.ui.screen.settings.HarmonyGuideScreen
import com.example.danmuapiapp.ui.screen.settings.NetworkSettingsScreen
import com.example.danmuapiapp.ui.screen.settings.RuntimeAndDirScreen
import com.example.danmuapiapp.ui.screen.settings.ServiceConfigScreen
import com.example.danmuapiapp.ui.screen.settings.SettingsHubScreen
import com.example.danmuapiapp.ui.screen.settings.ThemeDisplayScreen
import com.example.danmuapiapp.ui.screen.settings.WorkDirScreen
import com.example.danmuapiapp.ui.screen.tools.ToolsScreen
import com.example.danmuapiapp.ui.startup.StartupPermissionGateHost

private fun NavController.navigateToTopLevelRoute(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun DanmuApiApp(
    startupUiState: RuntimeWarmupCoordinator.UiState = RuntimeWarmupCoordinator.UiState.Ready
) {
    when (startupUiState) {
        RuntimeWarmupCoordinator.UiState.Ready -> StartupPermissionGateHost {
            DanmuApiMainContent()
        }

        is RuntimeWarmupCoordinator.UiState.Running -> StartupWarmupOverlay(
            title = startupUiState.title,
            detail = startupUiState.detail
        )

        RuntimeWarmupCoordinator.UiState.NotStarted -> StartupWarmupOverlay(
            title = "正在准备首页",
            detail = "请稍候，马上进入"
        )
    }
}

@Composable
private fun StartupWarmupOverlay(title: String, detail: String) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
            ) {
                Row(
                    modifier = Modifier
                        .widthIn(min = 240.dp, max = 520.dp)
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Autorenew,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        CircularProgressIndicator(
                            strokeWidth = 2.4.dp,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Start
                        )
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Start
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DanmuApiMainContent() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val navItemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primary.copy(
            alpha = if (isDarkTheme) 0.22f else 0.14f
        ),
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f),
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f),
        disabledIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    )

    Scaffold(
        containerColor = if (isDarkTheme) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.background
        },
        bottomBar = {
            NavigationBar(
                tonalElevation = NavigationBarDefaults.Elevation,
                containerColor = if (isDarkTheme) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    NavigationBarDefaults.containerColor
                }
            ) {
                Screen.entries.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.route == screen.route
                    } == true

                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.label
                            )
                        },
                        label = { Text(screen.label) },
                        selected = selected,
                        onClick = {
                            navController.navigateToTopLevelRoute(screen.route)
                        },
                        colors = navItemColors
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                fadeIn(animationSpec = tween(200)) +
                    slideInHorizontally(animationSpec = tween(200)) { it / 6 }
            },
            exitTransition = {
                fadeOut(animationSpec = tween(200))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(200))
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(200)) +
                    slideOutHorizontally(animationSpec = tween(200)) { it / 6 }
            }
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onOpenDanmuDownload = { navController.navigate(ToolRoute.DanmuDownload) },
                    onOpenCacheManagement = { navController.navigate(ToolRoute.CacheManagement) },
                    onOpenAnnouncementRoute = { route ->
                        val isTopLevelRoute = Screen.entries.any { screen -> screen.route == route }
                        if (isTopLevelRoute) {
                            navController.navigateToTopLevelRoute(route)
                        } else {
                            navController.navigate(route) {
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }
            composable(Screen.Core.route) { CoreScreen() }
            composable(Screen.Tools.route) {
                ToolsScreen(
                    onOpenApiTest = { navController.navigate(ToolRoute.ApiTest) },
                    onOpenPushDanmu = { navController.navigate(ToolRoute.PushDanmu) },
                    onOpenDanmuDownload = { navController.navigate(ToolRoute.DanmuDownload) },
                    onOpenRequestRecords = { navController.navigate(ToolRoute.RequestRecords) },
                    onOpenConsole = { navController.navigate(ToolRoute.Console) },
                    onOpenConfig = { navController.navigate(ToolRoute.Config) },
                    onOpenDeviceAccess = { navController.navigate(ToolRoute.DeviceAccess) },
                    onOpenAdminMode = { navController.navigate(SettingsRoute.AdminMode) },
                    onOpenCacheManagement = { navController.navigate(ToolRoute.CacheManagement) }
                )
            }
            composable(Screen.Settings.route) {
                SettingsHubScreen(
                    onOpenRuntimeAndDir = { navController.navigate(SettingsRoute.RuntimeAndDir) },
                    onOpenThemeDisplay = { navController.navigate(SettingsRoute.ThemeDisplay) },
                    onOpenWorkDir = { navController.navigate(SettingsRoute.WorkDir) },
                    onOpenServiceConfig = { navController.navigate(SettingsRoute.ServiceConfig) },
                    onOpenDanmuDownload = { navController.navigate(SettingsRoute.DanmuDownload) },
                    onOpenNetwork = { navController.navigate(SettingsRoute.Network) },
                    onOpenBackupRestore = { navController.navigate(SettingsRoute.BackupRestore) },
                    onOpenGithubToken = { navController.navigate(SettingsRoute.GithubToken) },
                    onOpenAdminMode = { navController.navigate(SettingsRoute.AdminMode) },
                    onOpenAbout = { navController.navigate(SettingsRoute.About) },
                    onOpenHarmonyGuide = { navController.navigate(SettingsRoute.HarmonyGuide) }
                )
            }

            composable(SettingsRoute.RuntimeAndDir) {
                RuntimeAndDirScreen(
                    onBack = { navController.popBackStack() },
                    onOpenHarmonyGuide = { navController.navigate(SettingsRoute.HarmonyGuide) }
                )
            }
            composable(SettingsRoute.ThemeDisplay) {
                ThemeDisplayScreen(onBack = { navController.popBackStack() })
            }
            composable(SettingsRoute.WorkDir) {
                WorkDirScreen(onBack = { navController.popBackStack() })
            }
            composable(SettingsRoute.ServiceConfig) {
                ServiceConfigScreen(onBack = { navController.popBackStack() })
            }
            composable(SettingsRoute.DanmuDownload) {
                DownloadSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(SettingsRoute.Network) {
                NetworkSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(SettingsRoute.GithubToken) {
                GithubTokenScreen(onBack = { navController.popBackStack() })
            }
            composable(SettingsRoute.BackupRestore) {
                BackupRestoreScreen(onBack = { navController.popBackStack() })
            }
            composable(SettingsRoute.AdminMode) {
                AdminModeScreen(onBack = { navController.popBackStack() })
            }
            composable(SettingsRoute.About) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
            composable(SettingsRoute.HarmonyGuide) {
                HarmonyGuideScreen(onBack = { navController.popBackStack() })
            }

            composable(ToolRoute.Console) {
                ConsoleScreen()
            }
            composable(ToolRoute.ApiTest) {
                ApiTestScreen(onBack = { navController.popBackStack() })
            }
            composable(ToolRoute.PushDanmu) {
                PushDanmuScreen(onBack = { navController.popBackStack() })
            }
            composable(ToolRoute.DanmuDownload) {
                DanmuDownloadScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDownloadSettings = { navController.navigate(SettingsRoute.DanmuDownload) }
                )
            }
            composable(ToolRoute.RequestRecords) {
                RequestRecordsScreen(onBack = { navController.popBackStack() })
            }
            composable(ToolRoute.Config) {
                ConfigScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAdminMode = { navController.navigate(SettingsRoute.AdminMode) }
                )
            }
            composable(ToolRoute.DeviceAccess) {
                DeviceAccessScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAdminMode = { navController.navigate(SettingsRoute.AdminMode) }
                )
            }
            composable(ToolRoute.CacheManagement) {
                CacheManagementScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
