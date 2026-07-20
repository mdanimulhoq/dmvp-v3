/**
 * app/src/main/java/com/dmvp/app/navigation/NavGraph.kt
 *
 * UDOVP V2 — Navigation Graph
 * PR 2: Added auth flow (Login → Signup → OTP → EmailVerify) before main app.
 * PR 3: Added BottomNav for main app screens (Home, Register, Verify, Compare, Assets).
 *
 * Auth flow:
 *   Login → (sign in success) → Home
 *   Login → (requires OTP) → OTP → Home
 *   Login → Sign Up → Signup → EmailVerify → Login
 *
 * Main app (with BottomNav):
 *   Home, Register, Verify, Compare, Assets
 *
 * Sub-screens (no BottomNav):
 *   Capture, Search, Device, EvidenceDetail, Error
 */

package com.dmvp.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.dmvp.app.ui.auth.EmailVerifyScreen
import com.dmvp.app.ui.auth.LoginScreen
import com.dmvp.app.ui.auth.OTPScreen
import com.dmvp.app.ui.auth.SignupScreen
import com.dmvp.app.ui.components.DmvpBottomNav
import com.dmvp.app.ui.screens.AccountScreen
import com.dmvp.app.ui.screens.AssetsScreen
import com.dmvp.app.ui.screens.CaptureScreen
import com.dmvp.app.ui.screens.ClaimsScreen
import com.dmvp.app.ui.screens.CompareScreen
import com.dmvp.app.ui.screens.DeviceScreen
import com.dmvp.app.ui.screens.ErrorScreen
import com.dmvp.app.ui.screens.EvidenceDetailScreen
import com.dmvp.app.ui.screens.HomeScreen
import com.dmvp.app.ui.screens.RegisterScreen
import com.dmvp.app.ui.screens.SearchScreen
import com.dmvp.app.ui.screens.VerifyScreen
import com.dmvp.app.ui.theme.*

/**
 * Routes that show the BottomNav
 */
private val bottomNavRoutes = setOf(
    Screen.Home.route,
    Screen.Register.route,
    Screen.Verify.route,
    Screen.Compare.route,
    Screen.Assets.route,
)

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Login.route,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomNavRoutes

    Scaffold(
        containerColor = BgBase,
        bottomBar = {
            if (showBottomBar) {
                DmvpBottomNav(
                    currentRoute = currentRoute,
                    onItemSelected = { item ->
                        navController.navigate(item.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(paddingValues),
        ) {
            // ═══════════════════════════════════════════════════════
            // Auth Flow
            // ═══════════════════════════════════════════════════════

            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onNavigateToSignup = {
                        navController.navigate(Screen.Signup.route)
                    },
                    onNavigateToOTP = { email ->
                        navController.navigate(Screen.OTP.createRoute(email))
                    },
                )
            }

            composable(Screen.Signup.route) {
                SignupScreen(
                    onSignupSuccess = { email ->
                        navController.navigate(Screen.EmailVerify.createRoute(email)) {
                            popUpTo(Screen.Signup.route) { inclusive = true }
                        }
                    },
                    onNavigateToLogin = {
                        navController.popBackStack()
                    },
                )
            }

            composable(
                route = Screen.OTP.route,
                arguments = listOf(
                    navArgument(Screen.OTP.ARG_EMAIL) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { backStackEntry ->
                val email = backStackEntry.arguments
                    ?.getString(Screen.OTP.ARG_EMAIL)
                    .orEmpty()

                OTPScreen(
                    email = email,
                    onOTPVerified = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onBack = {
                        navController.popBackStack()
                    },
                )
            }

            composable(
                route = Screen.EmailVerify.route,
                arguments = listOf(
                    navArgument(Screen.EmailVerify.ARG_EMAIL) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { backStackEntry ->
                val email = backStackEntry.arguments
                    ?.getString(Screen.EmailVerify.ARG_EMAIL)
                    .orEmpty()

                EmailVerifyScreen(
                    email = email,
                    onNavigateToLogin = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }

            // ═══════════════════════════════════════════════════════
            // Main App Screens (with BottomNav)
            // ═══════════════════════════════════════════════════════

            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToCapture = {
                        navController.navigate(Screen.Capture.route)
                    },
                    onNavigateToVerify = {
                        navController.navigate(Screen.Verify.route)
                    },
                    onNavigateToCompare = {
                        navController.navigate(Screen.Compare.route)
                    },
                    onNavigateToDevice = {
                        navController.navigate(Screen.Device.route)
                    },
                    onNavigateToAccount = {
                        navController.navigate(Screen.Account.route)
                    },
                    onNavigateToAssets = {
                        navController.navigate(Screen.Assets.route)
                    },
                    onNavigateToClaims = {
                        navController.navigate(Screen.Claims.route)
                    },
                    onNavigateToSearch = {
                        navController.navigate(Screen.Search.route)
                    },
                )
            }

            composable(Screen.Register.route) {
                RegisterScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEvidenceDetail = { evidenceId ->
                        if (evidenceId.isNotBlank()) {
                            navController.navigate(Screen.EvidenceDetail.createRoute(evidenceId))
                        }
                    },
                    onNavigateToHome = {
                        navController.popBackStack(
                            route = Screen.Home.route,
                            inclusive = false,
                        )
                    },
                )
            }

            composable(Screen.Verify.route) {
                VerifyScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToVerdictDetail = { evidenceId ->
                        if (evidenceId.isNotBlank()) {
                            navController.navigate(Screen.EvidenceDetail.createRoute(evidenceId))
                        }
                    },
                )
            }

            composable(Screen.Compare.route) {
                CompareScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEvidenceDetail = { evidenceId ->
                        navController.navigate(Screen.EvidenceDetail.createRoute(evidenceId))
                    },
                )
            }

            composable(Screen.Assets.route) {
                AssetsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAssetDetail = { assetId ->
                        // TODO: Navigate to asset detail
                    },
                )
            }

            composable(Screen.Claims.route) {
                ClaimsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToClaimDetail = { claimId ->
                        // TODO: Navigate to claim detail
                    },
                )
            }

            composable(Screen.Account.route) {
                AccountScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDevices = {
                        navController.navigate(Screen.Device.route)
                    },
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }

            // ═══════════════════════════════════════════════════════
            // Sub-Screens (no BottomNav)
            // ═══════════════════════════════════════════════════════

            composable(Screen.Capture.route) {
                CaptureScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToRegister = { _, _ ->
                        navController.navigate(Screen.Register.route)
                    },
                )
            }

            composable(Screen.Search.route) {
                SearchScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEvidenceDetail = { evidenceId ->
                        if (evidenceId.isNotBlank()) {
                            navController.navigate(Screen.EvidenceDetail.createRoute(evidenceId))
                        }
                    },
                )
            }

            composable(Screen.Device.route) {
                DeviceScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Screen.EvidenceDetail.route,
                arguments = listOf(
                    navArgument(Screen.EvidenceDetail.ARG_EVIDENCE_ID) {
                        type = NavType.StringType
                    },
                ),
            ) { backStackEntry ->
                val id = backStackEntry.arguments
                    ?.getString(Screen.EvidenceDetail.ARG_EVIDENCE_ID)
                    .orEmpty()
                EvidenceDetailScreen(
                    evidenceId = id,
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Screen.Error.route,
                arguments = listOf(
                    navArgument(Screen.Error.ARG_TITLE) {
                        type = NavType.StringType
                        defaultValue = "Something went wrong"
                    },
                    navArgument(Screen.Error.ARG_MESSAGE) {
                        type = NavType.StringType
                        defaultValue = "Please go back and try again."
                    },
                ),
            ) { backStackEntry ->
                val title = backStackEntry.arguments
                    ?.getString(Screen.Error.ARG_TITLE)
                    ?: "Something went wrong"

                val message = backStackEntry.arguments
                    ?.getString(Screen.Error.ARG_MESSAGE)
                    ?: "Please go back and try again."

                ErrorScreen(
                    title = title,
                    message = message,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
            }
        }
    }
}
