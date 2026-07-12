package com.dmvp.app.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Capture : Screen("capture")
    data object Verify : Screen("verify")
    data object Register : Screen("register")
    data object Search : Screen("search")
    data object Device : Screen("device")

    data object Error : Screen("error?title={title}&message={message}") {
        const val ARG_TITLE = "title"
        const val ARG_MESSAGE = "message"

        fun createRoute(
            title: String,
            message: String
        ): String {
            return "error?title=${Uri.encode(title)}&message=${Uri.encode(message)}"
        }
    }
}
