package `in`.jandwar.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import `in`.jandwar.app.ui.navigation.Screen
import `in`.jandwar.app.ui.screens.*
import `in`.jandwar.app.ui.theme.JanDwarTheme
import `in`.jandwar.app.ui.viewmodel.AppViewModel
import `in`.jandwar.app.ui.viewmodel.VoiceViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request audio permission early
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            JanDwarTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavHost()
                }
            }
        }
    }
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val appViewModel: AppViewModel = hiltViewModel()
    val voiceViewModel: VoiceViewModel = hiltViewModel()

    // Determine start destination
    val startDestination = remember {
        val lang = appViewModel.currentLang
        if (lang.isBlank()) Screen.Language.route else Screen.Splash.route
    }

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Screen.Splash.route) {
            SplashScreen(onFinished = {
                val lang = appViewModel.currentLang
                if (lang.isBlank()) {
                    navController.navigate(Screen.Language.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                } else {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            })
        }

        composable(Screen.Language.route) {
            LanguageScreen(
                viewModel = appViewModel,
                onLanguageSelected = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Language.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                viewModel = appViewModel,
                onFinish = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = appViewModel,
                onNavigateIntake = { navController.navigate(Screen.Intake.route) },
                onNavigateCourses = { navController.navigate(Screen.Courses.route) },
                onNavigateVoice = { navController.navigate(Screen.Voice.route) },
                onNavigateSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Intake.route) {
            IntakeScreen(
                viewModel = appViewModel,
                onBack = { navController.popBackStack() },
                onSubmit = { navController.navigate(Screen.Results.route) }
            )
        }

        composable(Screen.Results.route) {
            ResultsScreen(
                viewModel = appViewModel,
                onBack = { navController.popBackStack() },
                onDetail = { qpCode ->
                    appViewModel.selectRoleByCode(qpCode)
                    navController.navigate(Screen.Detail.createRoute(qpCode))
                }
            )
        }

        composable(
            route = Screen.Detail.route,
            arguments = listOf(navArgument("qp_code") { type = NavType.StringType })
        ) { backStackEntry ->
            val qpCode = backStackEntry.arguments?.getString("qp_code") ?: ""
            LaunchedEffect(qpCode) {
                if (appViewModel.selectedRole == null) {
                    appViewModel.selectRoleByCode(qpCode)
                }
            }
            DetailScreen(
                viewModel = appViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Courses.route) {
            CoursesScreen(
                viewModel = appViewModel,
                onBack = { navController.popBackStack() },
                onDetail = { qpCode ->
                    appViewModel.selectRoleByCode(qpCode)
                    navController.navigate(Screen.Detail.createRoute(qpCode))
                }
            )
        }

        composable(Screen.Voice.route) {
            VoiceScreen(
                appViewModel = appViewModel,
                voiceViewModel = voiceViewModel,
                onClose = { navController.popBackStack() },
                onDone = {
                    navController.navigate(Screen.Results.route) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = appViewModel,
                onBack = { navController.popBackStack() },
                onLanguageChange = { navController.navigate(Screen.Language.route) }
            )
        }
    }
}
