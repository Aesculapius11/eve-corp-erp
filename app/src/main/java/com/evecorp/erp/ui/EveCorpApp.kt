package com.evecorp.erp.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.evecorp.erp.auth.EsiAuthManager
import com.evecorp.erp.auth.TokenManager
import com.evecorp.erp.ui.navigation.Screen
import com.evecorp.erp.ui.screens.dashboard.DashboardScreen
import com.evecorp.erp.ui.screens.industry.IndustryScreen
import com.evecorp.erp.ui.screens.login.LoginScreen
import com.evecorp.erp.ui.screens.market.MarketScreen
import com.evecorp.erp.ui.screens.settings.SettingsScreen
import com.evecorp.erp.ui.theme.ThemeManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EveCorpApp(
    esiAuthManager: EsiAuthManager,
    tokenManager: TokenManager,
    themeManager: ThemeManager
) {
    val navController = rememberNavController()
    val isLoggedIn = tokenManager.isLoggedIn()
    val screens = Screen.entries.filter { it.showInNavBar }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // 隐藏登录页的底部导航栏
    val showBottomBar = currentDestination?.route != Screen.LOGIN.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(tween(300)) { it } + fadeIn(tween(300)),
                exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200))
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    shadowElevation = 8.dp
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        modifier = Modifier.clip(RoundedCornerShape(24.dp))
                    ) {
                        screens.forEach { screen ->
                            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        if (selected) screen.icon else screen.iconOutlined,
                                        contentDescription = screen.label
                                    )
                                },
                                label = {
                                    Text(
                                        screen.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
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
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (isLoggedIn) Screen.DASHBOARD.route else Screen.LOGIN.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                fadeIn(tween(250)) + slideInVertically(tween(250)) { 30 }
            },
            exitTransition = {
                fadeOut(tween(200))
            },
            popEnterTransition = {
                fadeIn(tween(250)) + slideInVertically(tween(250)) { 30 }
            },
            popExitTransition = {
                fadeOut(tween(200))
            }
        ) {
            composable(Screen.LOGIN.route) {
                LoginScreen(onLoginClick = { esiAuthManager.openLogin() })
            }
            composable(Screen.DASHBOARD.route) { DashboardScreen() }
            composable(Screen.INDUSTRY.route) { IndustryScreen() }
            composable(Screen.MARKET.route) { MarketScreen() }
            composable(Screen.SETTINGS.route) {
                SettingsScreen(
                    onLogout = {
                        navController.navigate(Screen.LOGIN.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    themeManager = themeManager
                )
            }
        }
    }
}
