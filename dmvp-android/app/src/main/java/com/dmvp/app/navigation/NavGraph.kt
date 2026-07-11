package com.dmvp.app.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@Composable
fun NavGraph(navController: NavHostController, startDestination: String) {
    NavHost(navController, startDestination) {
        composable(Screen.Home.route) { Text("Home") }
        composable(Screen.Capture.route) { Text("Capture") }
        composable(Screen.Verify.route) { Text("Verify") }
    }
}
