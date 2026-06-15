package com.evecorp.erp.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
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
    PROFIT("profit", "利润", Icons.Filled.AttachMoney, Icons.Outlined.AttachMoney),
    SETTINGS("settings", "设置", Icons.Filled.Settings, Icons.Outlined.Settings)
}
