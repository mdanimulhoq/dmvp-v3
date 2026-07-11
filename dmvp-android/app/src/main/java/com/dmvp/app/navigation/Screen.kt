package com.dmvp.app.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Capture : Screen("capture")
    object Verify : Screen("verify")
    object Register : Screen("register")
    object Search : Screen("search")
    object Device : Screen("device")
}
