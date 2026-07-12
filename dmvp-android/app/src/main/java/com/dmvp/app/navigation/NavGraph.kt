package com.dmvp.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.dmvp.app.ui.screens.CaptureScreen
import com.dmvp.app.ui.screens.DeviceScreen
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
                onNavigateToSearch = {
                    navController.navigate(Screen.Search.route)
                },
                onNavigateToDevice = {
                    navController.navigate(Screen.Device.route)
                }
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
                onNavigateToVerdictDetail = {
                    // Phase 3/4: verdict detail route will be added when verdict IDs exist.
                }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToEvidenceDetail = {
                    // Phase 3: evidence detail route will be added when evidence IDs exist.
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
                onNavigateToEvidenceDetail = {
                    // Phase 3/5: evidence detail route will be added later.
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
    }
}
