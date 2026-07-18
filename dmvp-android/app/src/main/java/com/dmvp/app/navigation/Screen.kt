package com.dmvp.app.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Capture : Screen("capture")
    data object Verify : Screen("verify")
    data object Register : Screen("register")
    data object Search : Screen("search")
    data object Device : Screen("device")

    /**
     * Compare screen — user selects a reference (registered) media and a
     * candidate media; the app confirms whether they are the same and, if so,
     * surfaces the reference's registered metadata (device info, timestamps,
     * signer key id, trust tier).
     */
    data object Compare : Screen("compare")

    /**
     * Evidence detail — shows full metadata for a registered evidence record.
     * Route argument: evidence_id (required).
     */
    data object EvidenceDetail : Screen("evidence/{evidence_id}") {
        const val ARG_EVIDENCE_ID = "evidence_id"

        fun createRoute(evidenceId: String): String =
            "evidence/${Uri.encode(evidenceId)}"
    }

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
