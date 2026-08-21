package com.example.danmuapiapp.ui.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import com.example.danmuapiapp.ui.component.liquid.LiquidBottomTab
import com.example.danmuapiapp.ui.component.liquid.LiquidBottomTabs
import com.example.danmuapiapp.ui.navigation.Screen
import com.kyant.backdrop.Backdrop

@Composable
fun FloatingBottomBarContentSpacer(modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(80.dp)
    )
}

@Composable
fun GlassBottomNavigationBar(
    backdrop: Backdrop,
    currentDestination: NavDestination?,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val screens = Screen.entries
    val selectedIndex = currentDestination.topLevelScreenIndex()
    var requestedIndex by rememberSaveable { mutableIntStateOf(selectedIndex) }
    val contentColor = MaterialTheme.colorScheme.onSurface

    LaunchedEffect(selectedIndex) {
        requestedIndex = selectedIndex
    }

    LiquidBottomTabs(
        selectedTabIndex = { requestedIndex },
        onTabSelected = { index ->
            requestedIndex = index
            if (index != selectedIndex) {
                screens.getOrNull(index)?.let(onNavigate)
            }
        },
        backdrop = backdrop,
        tabsCount = screens.size,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 36.dp, vertical = 8.dp)
    ) {
        screens.forEachIndexed { index, screen ->
            LiquidBottomTab(
                selected = index == requestedIndex,
                onClick = {
                    requestedIndex = index
                    if (index != selectedIndex) {
                        onNavigate(screen)
                    }
                }
            ) {
                Icon(
                    imageVector = screen.icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = contentColor
                )
                Text(
                    text = screen.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

private fun NavDestination?.topLevelScreenIndex(): Int {
    val directIndex = Screen.entries.indexOfFirst { screen ->
        this?.hierarchy?.any { destination -> destination.route == screen.route } == true
    }
    if (directIndex >= 0) return directIndex

    val route = this?.route.orEmpty()
    val screen = when {
        route.startsWith("core_") -> Screen.Core
        route.startsWith("tool_") -> Screen.Tools
        route.startsWith("settings_") -> Screen.Settings
        else -> Screen.Home
    }
    return Screen.entries.indexOf(screen)
}
