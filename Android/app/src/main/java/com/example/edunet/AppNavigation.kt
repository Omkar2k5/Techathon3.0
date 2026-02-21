package com.example.edunet

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.edunet.ui.HomeScreen
import com.example.edunet.ui.LoginScreen
import com.example.edunet.ui.SignUpScreen
import com.example.edunet.ui.SplashScreen
import com.example.edunet.ui.session.StudentSessionScreen
import com.example.edunet.ui.session.TeacherSessionScreen
import java.net.URLDecoder
import java.net.URLEncoder

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "splash") {

        composable("splash") {
            SplashScreen(onSplashFinished = { isLoggedIn ->
                if (isLoggedIn) navController.navigate("home") { popUpTo("splash") { inclusive = true } }
                else            navController.navigate("login") { popUpTo("splash") { inclusive = true } }
            })
        }

        composable("login") {
            LoginScreen(
                onLoginSuccess = { _ ->
                    navController.navigate("home") { popUpTo("login") { inclusive = true } }
                },
                onNavigateToSignUp = { navController.navigate("signup") }
            )
        }

        composable("signup") {
            SignUpScreen(
                onSignUpSuccess = { _ ->
                    navController.navigate("home") { popUpTo("signup") { inclusive = true } }
                },
                onNavigateToLogin = {
                    navController.navigate("login") { popUpTo("signup") { inclusive = true } }
                }
            )
        }

        composable("home") {
            HomeScreen(
                onLogout = {
                    navController.navigate("login") { popUpTo("home") { inclusive = true } }
                },
                onStartSession = { teacherName, subjectName, subjectCode ->
                    val tEnc = URLEncoder.encode(teacherName, "UTF-8")
                    val sEnc = URLEncoder.encode(subjectName, "UTF-8")
                    val cEnc = URLEncoder.encode(subjectCode, "UTF-8")
                    navController.navigate("teacher_session/$tEnc/$sEnc/$cEnc")
                },
                onJoinSession = { subjectCode, subjectName ->
                    val cEnc = URLEncoder.encode(subjectCode, "UTF-8")
                    val sEnc = URLEncoder.encode(subjectName, "UTF-8")
                    navController.navigate("student_session/$cEnc/$sEnc")
                }
            )
        }

        // Teacher session
        composable(
            route = "teacher_session/{teacherName}/{subjectName}/{subjectCode}",
            arguments = listOf(
                navArgument("teacherName")  { type = NavType.StringType },
                navArgument("subjectName")  { type = NavType.StringType },
                navArgument("subjectCode")  { type = NavType.StringType }
            )
        ) { backStack ->
            TeacherSessionScreen(
                teacherName = URLDecoder.decode(backStack.arguments?.getString("teacherName") ?: "", "UTF-8"),
                subjectName = URLDecoder.decode(backStack.arguments?.getString("subjectName") ?: "", "UTF-8"),
                subjectCode = URLDecoder.decode(backStack.arguments?.getString("subjectCode") ?: "", "UTF-8"),
                onBack = { navController.popBackStack() }
            )
        }

        // Student session (subjectCode + subjectName)
        composable(
            route = "student_session/{subjectCode}/{subjectName}",
            arguments = listOf(
                navArgument("subjectCode") { type = NavType.StringType },
                navArgument("subjectName") { type = NavType.StringType }
            )
        ) { backStack ->
            StudentSessionScreen(
                subjectCode = URLDecoder.decode(backStack.arguments?.getString("subjectCode") ?: "", "UTF-8"),
                subjectName = URLDecoder.decode(backStack.arguments?.getString("subjectName") ?: "", "UTF-8"),
                onBack = { navController.popBackStack() }
            )
        }
    }
}
