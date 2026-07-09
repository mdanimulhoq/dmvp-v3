/**
 * app/src/main/java/com/dmvp/app/navigation/NavGraph.kt
 *
 * Navigation graph for DMVP v3.0 Android app.
 * Defines all screen destinations and navigation routes using Jetpack Compose Navigation.
 *
 * Screens:
 *   - Home: Main dashboard
 *   - Capture: Media capture and selection
 *   - Register: Evidence registration flow
 *   - Verify: Media verification flow
 *   - Search: Evidence search
 *   - Device: Device key management
 *   - EvidenceDetail: View evidence details
 *   - VerdictDetail: View full verdict breakdown
 *
 * Uses type-safe navigation with NavType arguments.
 */

package com.dmvp.app.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dmvp.app.ui.screens.*

/**
 * Sealed class representing all navigation destinations.
 * Provides route strings and argument definitions.
 */
sealed class Screen(val route: String) {
    // Main screens
    object Home : Screen("home")
    object Capture : Screen("capture")
    object Register : Screen("register")
    object Verify : Screen("verify")
    object Search : Screen("search")
    object Device : Screen("device")

    // Detail screens with arguments
    object EvidenceDetail : Screen("evidence_detail/{evidenceId}") {
        fun passArguments(evidenceId: String) = "evidence_detail/$evidenceId"
    }

    object VerdictDetail : Screen("verdict_detail/{evidenceId}") {
        fun passArguments(evidenceId: String) = "verdict_detail/$evidenceId"
    }

    // For nested navigation or argument parsing
    companion object {
        const val ARG_EVIDENCE_ID = "evidenceId"
    }
}

/**
 * Main navigation graph.
 * @param modifier Modifier for the NavHost.
 * @param startDestination Optional start destination (default: Home).
 */
@Composable
fun NavGraph(
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Home.route
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // Home Screen
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToCapture = { navController.navigate(Screen.Capture.route) },
                onNavigateToVerify = { navController.navigate(Screen.Verify.route) },
                onNavigateToSearch = { navController.navigate(Screen.Search.route) },
                onNavigateToDevice = { navController.navigate(Screen.Device.route) }
            )
        }

        // Capture Screen
        composable(Screen.Capture.route) {
            CaptureScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRegister = { file, mediaType ->
                    // Navigate to Register with file info (using a shared ViewModel or arguments)
                    // We'll pass data via a shared ViewModel or saved state handle.
                    // For simplicity, we'll navigate and the RegisterScreen will read from ViewModel.
                    navController.navigate(Screen.Register.route)
                },
                onNavigateToVerify = { file, mediaType ->
                    navController.navigate(Screen.Verify.route)
                }
            )
        }

        // Register Screen
        composable(Screen.Register.route) {
            RegisterScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEvidenceDetail = { evidenceId ->
                    navController.navigate(Screen.EvidenceDetail.passArguments(evidenceId))
                },
                onNavigateToHome = { navController.navigate(Screen.Home.route) { popUpTo(Screen.Home.route) { inclusive = false } } }
            )
        }

        // Verify Screen
        composable(Screen.Verify.route) {
            VerifyScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToVerdictDetail = { evidenceId ->
                    navController.navigate(Screen.VerdictDetail.passArguments(evidenceId))
                }
            )
        }

        // Search Screen
        composable(Screen.Search.route) {
            SearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEvidenceDetail = { evidenceId ->
                    navController.navigate(Screen.EvidenceDetail.passArguments(evidenceId))
                }
            )
        }

        // Device Management Screen
        composable(Screen.Device.route) {
            DeviceScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Evidence Detail Screen
        composable(
            route = Screen.EvidenceDetail.route,
            arguments = listOf(
                navArgument(Screen.Companion.ARG_EVIDENCE_ID) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val evidenceId = backStackEntry.arguments?.getString(Screen.Companion.ARG_EVIDENCE_ID) ?: ""
            EvidenceDetailScreen(
                evidenceId = evidenceId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToVerdict = { id ->
                    navController.navigate(Screen.VerdictDetail.passArguments(id))
                }
            )
        }

        // Verdict Detail Screen
        composable(
            route = Screen.VerdictDetail.route,
            arguments = listOf(
                navArgument(Screen.Companion.ARG_EVIDENCE_ID) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val evidenceId = backStackEntry.arguments?.getString(Screen.Companion.ARG_EVIDENCE_ID) ?: ""
            VerdictDetailScreen(
                evidenceId = evidenceId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

/**
 * Extension function to navigate with a screen object.
 * Useful for type-safe navigation.
 */
fun androidx.navigation.NavController.navigateTo(screen: Screen) {
    this.navigate(screen.route)
}

/**
 * Extension to navigate to EvidenceDetail with evidence ID.
 */
fun androidx.navigation.NavController.navigateToEvidenceDetail(evidenceId: String) {
    this.navigate(Screen.EvidenceDetail.passArguments(evidenceId))
}

/**
 * Extension to navigate to VerdictDetail with evidence ID.
 */
fun androidx.navigation.NavController.navigateToVerdictDetail(evidenceId: String) {
    this.navigate(Screen.VerdictDetail.passArguments(evidenceId))
}
