package com.example.edunet

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.edunet.ui.HomeScreen
import com.example.edunet.ui.LoginScreen
import com.example.edunet.ui.SignUpScreen
import com.example.edunet.ui.SplashScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "splash") {

        composable("splash") {
            SplashScreen(onSplashFinished = { isLoggedIn ->
                if (isLoggedIn) {
                    // Already logged in → go straight to Home
                    navController.navigate("home") {
                        popUpTo("splash") { inclusive = true }
                    }
                } else {
                    // Not logged in → go to Login
                    navController.navigate("login") {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            })
        }

        composable("login") {
            LoginScreen(
                onLoginSuccess = { _ ->
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onNavigateToSignUp = {
                    navController.navigate("signup")
                }
            )
        }

        composable("signup") {
            SignUpScreen(
                onSignUpSuccess = { _ ->
                    navController.navigate("home") {
                        popUpTo("signup") { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate("login") {
                        popUpTo("signup") { inclusive = true }
                    }
                }
            )
        }

        composable("home") {
            HomeScreen(onLogout = {
                navController.navigate("login") {
                    popUpTo("home") { inclusive = true }
                }
            })
        }
    }
}
