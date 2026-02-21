package com.example.danmuapiapp.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.danmuapiapp.ui.navigation.Screen
import com.example.danmuapiapp.ui.navigation.SettingsRoute
import com.example.danmuapiapp.ui.navigation.ToolRoute
import com.example.danmuapiapp.ui.screen.apitest.ApiTestScreen
import com.example.danmuapiapp.ui.screen.config.ConfigScreen
import com.example.danmuapiapp.ui.screen.console.ConsoleScreen
import com.example.danmuapiapp.ui.screen.core.CoreScreen
import com.example.danmuapiapp.ui.screen.home.HomeScreen
import com.example.danmuapiapp.ui.screen.push.PushDanmuScreen
import com.example.danmuapiapp.ui.screen.records.RequestRecordsScreen
import com.example.danmuapiapp.ui.screen.settings.BackupRestoreScreen
import com.example.danmuapiapp.ui.screen.settings.GithubTokenScreen
import com.example.danmuapiapp.ui.screen.settings.NetworkSettingsScreen
import com.example.danmuapiapp.ui.screen.settings.RuntimeAndDirScreen
import com.example.danmuapiapp.ui.screen.settings.ServiceConfigScreen
import com.example.danmuapiapp.ui.screen.settings.SettingsHubScreen
import com.example.danmuapiapp.ui.screen.settings.ThemeDisplayScreen
import com.example.danmuapiapp.ui.screen.settings.WorkDirScreen
import com.example.danmuapiapp.ui.screen.tools.ToolsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DanmuApiApp() {
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
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
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
            composable(Screen.Home.route) { HomeScreen() }
            composable(Screen.Core.route) { CoreScreen() }
            composable(Screen.Tools.route) {
                ToolsScreen(
                    onOpenApiTest = { navController.navigate(ToolRoute.ApiTest) },
                    onOpenPushDanmu = { navController.navigate(ToolRoute.PushDanmu) },
                    onOpenRequestRecords = { navController.navigate(ToolRoute.RequestRecords) },
                    onOpenConsole = { navController.navigate(ToolRoute.Console) },
                    onOpenConfig = { navController.navigate(ToolRoute.Config) }
                )
            }
            composable(Screen.Settings.route) {
                SettingsHubScreen(
                    onOpenRuntimeAndDir = { navController.navigate(SettingsRoute.RuntimeAndDir) },
                    onOpenThemeDisplay = { navController.navigate(SettingsRoute.ThemeDisplay) },
                    onOpenWorkDir = { navController.navigate(SettingsRoute.WorkDir) },
                    onOpenServiceConfig = { navController.navigate(SettingsRoute.ServiceConfig) },
                    onOpenNetwork = { navController.navigate(SettingsRoute.Network) },
                    onOpenBackupRestore = { navController.navigate(SettingsRoute.BackupRestore) },
                    onOpenGithubToken = { navController.navigate(SettingsRoute.GithubToken) }
                )
            }

            composable(SettingsRoute.RuntimeAndDir) {
                RuntimeAndDirScreen(onBack = { navController.popBackStack() })
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
            composable(SettingsRoute.Network) {
                NetworkSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(SettingsRoute.GithubToken) {
                GithubTokenScreen(onBack = { navController.popBackStack() })
            }
            composable(SettingsRoute.BackupRestore) {
                BackupRestoreScreen(onBack = { navController.popBackStack() })
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
            composable(ToolRoute.RequestRecords) {
                RequestRecordsScreen(onBack = { navController.popBackStack() })
            }
            composable(ToolRoute.Config) {
                ConfigScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
