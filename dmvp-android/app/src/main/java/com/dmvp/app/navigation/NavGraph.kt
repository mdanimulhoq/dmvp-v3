package com.dmvp.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.dmvp.app.ui.screens.CaptureScreen
import com.dmvp.app.ui.screens.CompareScreen
import com.dmvp.app.ui.screens.DeviceScreen
import com.dmvp.app.ui.screens.ErrorScreen
import com.dmvp.app.ui.screens.EvidenceDetailScreen
import com.dmvp.app.ui.screens.HomeScreen
import com.dmvp.app.ui.screens.RegisterScreen
import com.dmvp.app.ui.screens.SearchScreen
import com.dmvp.app.ui.screens.VerifyScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Home.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
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
                onNavigateToSearch = {
                    navController.navigate(Screen.Search.route)
                },
                onNavigateToDevice = {
                    navController.navigate(Screen.Device.route)
                }
            )
        }

        composable(Screen.Compare.route) {
            CompareScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEvidenceDetail = { evidenceId ->
                    navController.navigate(Screen.EvidenceDetail.createRoute(evidenceId))
                }
            )
        }

        composable(
            route = Screen.EvidenceDetail.route,
            arguments = listOf(
                navArgument(Screen.EvidenceDetail.ARG_EVIDENCE_ID) {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments
                ?.getString(Screen.EvidenceDetail.ARG_EVIDENCE_ID)
                .orEmpty()
            EvidenceDetailScreen(
                evidenceId = id,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Capture.route) {
            CaptureScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToRegister = { _, _ ->
                    navController.navigate(Screen.Register.route)
                },
                onNavigateToVerify = { _, _ ->
                    navController.navigate(Screen.Verify.route)
                }
            )
        }

        composable(Screen.Verify.route) {
            VerifyScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToVerdictDetail = { evidenceId ->
                    if (evidenceId.isNotBlank()) {
                        navController.navigate(
                            Screen.EvidenceDetail.createRoute(evidenceId)
                        )
                    }
                }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToEvidenceDetail = { evidenceId ->
                    if (evidenceId.isNotBlank()) {
                        navController.navigate(
                            Screen.EvidenceDetail.createRoute(evidenceId)
                        )
                    }
                },
                onNavigateToHome = {
                    navController.popBackStack(
                        route = Screen.Home.route,
                        inclusive = false
                    )
                }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToEvidenceDetail = { evidenceId ->
                    if (evidenceId.isNotBlank()) {
                        navController.navigate(
                            Screen.EvidenceDetail.createRoute(evidenceId)
                        )
                    }
                }
            )
        }

        composable(Screen.Device.route) {
            DeviceScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
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
                }
            )
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
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}
