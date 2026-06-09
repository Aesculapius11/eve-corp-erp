package com.evecorp.erp.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val iconOutlined: ImageVector = icon,
    val showInNavBar: Boolean = true
) {
    LOGIN("login", "登录", Icons.Filled.Person, Icons.Outlined.Person, showInNavBar = false),
    DASHBOARD("dashboard", "首页", Icons.Filled.Home, Icons.Outlined.Home),
    INDUSTRY("industry", "工业", Icons.Filled.Build, Icons.Outlined.Build),
    MARKET("market", "市场", Icons.Filled.ShoppingCart, Icons.Outlined.ShoppingCart),
    HANGAR("hangar", "机库", Icons.Filled.Inventory2, Icons.Outlined.Inventory2),
    SETTINGS("settings", "设置", Icons.Filled.Settings, Icons.Outlined.Settings)
}
