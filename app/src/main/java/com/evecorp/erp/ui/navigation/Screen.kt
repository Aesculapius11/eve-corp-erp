package com.evecorp.erp.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val showInNavBar: Boolean = true
) {
    LOGIN("login", "登录", Icons.Filled.Person, showInNavBar = false),
    DASHBOARD("dashboard", "首页", Icons.Filled.Home),
    INDUSTRY("industry", "工业", Icons.Filled.Build),
    MARKET("market", "市场", Icons.Filled.ShoppingCart),
    HANGAR("hangar", "机库", Icons.Filled.Inventory),
    BILLS("bills", "账单", Icons.Filled.Receipt)
}
