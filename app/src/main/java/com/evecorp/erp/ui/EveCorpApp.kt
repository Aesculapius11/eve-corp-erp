package com.evecorp.erp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.evecorp.erp.auth.EsiAuthManager
import com.evecorp.erp.auth.TokenManager
import com.evecorp.erp.ui.navigation.Screen
import com.evecorp.erp.ui.screens.bills.BillsScreen
import com.evecorp.erp.ui.screens.dashboard.DashboardScreen
import com.evecorp.erp.ui.screens.hangar.HangarScreen
import com.evecorp.erp.ui.screens.industry.IndustryScreen
import com.evecorp.erp.ui.screens.login.LoginScreen
import com.evecorp.erp.ui.screens.market.MarketScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EveCorpApp(
    esiAuthManager: EsiAuthManager,
    tokenManager: TokenManager
) {
    val navController = rememberNavController()
    val isLoggedIn = tokenManager.isLoggedIn()
    val screens = Screen.entries.filter { it.showInNavBar }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                screens.forEach { screen ->
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
            startDestination = if (isLoggedIn) Screen.DASHBOARD.route else Screen.LOGIN.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.LOGIN.route) {
                LoginScreen(
                    onLoginClick = { esiAuthManager.openLogin() }
                )
            }
            composable(Screen.DASHBOARD.route) { DashboardScreen() }
            composable(Screen.INDUSTRY.route) { IndustryScreen() }
            composable(Screen.MARKET.route) { MarketScreen() }
            composable(Screen.HANGAR.route) { HangarScreen() }
            composable(Screen.BILLS.route) { BillsScreen() }
        }
    }
}
