package com.realdebrid.downloader.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.realdebrid.downloader.ui.screens.cache.CacheScreen
import com.realdebrid.downloader.ui.screens.downloads.DownloadsScreen
import com.realdebrid.downloader.ui.screens.home.HomeScreen
import com.realdebrid.downloader.ui.screens.settings.SettingsScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object Cache : Screen("cache", "Cache", Icons.Default.Cloud)
    data object Downloads : Screen("downloads", "Downloads", Icons.Default.Download)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun AppNavigation(sharedLink: String? = null) {
    val navController = rememberNavController()
    val items = listOf(Screen.Home, Screen.Cache, Screen.Downloads, Screen.Settings)

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(sharedLink = sharedLink)
            }
            composable(Screen.Cache.route) {
                CacheScreen()
            }
            composable(Screen.Downloads.route) {
                DownloadsScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
