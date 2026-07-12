package com.dmvp.app.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Capture : Screen("capture")
    data object Verify : Screen("verify")
    data object Register : Screen("register")
    data object Search : Screen("search")
    data object Device : Screen("device")
}
