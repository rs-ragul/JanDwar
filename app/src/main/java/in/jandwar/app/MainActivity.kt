package `in`.jandwar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import `in`.jandwar.app.ui.navigation.Screen
import `in`.jandwar.app.ui.screens.CoursesScreen
import `in`.jandwar.app.ui.screens.DetailScreen
import `in`.jandwar.app.ui.screens.HomeScreen
import `in`.jandwar.app.ui.screens.IntakeScreen
import `in`.jandwar.app.ui.screens.LanguageScreen
import `in`.jandwar.app.ui.screens.OnboardingScreen
import `in`.jandwar.app.ui.screens.ResultsScreen
import `in`.jandwar.app.ui.screens.SettingsScreen
import `in`.jandwar.app.ui.screens.SplashScreen
import `in`.jandwar.app.ui.screens.VoiceScreen
import `in`.jandwar.app.ui.theme.JanDwarTheme
import `in`.jandwar.app.ui.viewmodel.AppViewModel
import `in`.jandwar.app.ui.viewmodel.VoiceViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            JanDwarTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavHost()
                }
            }
        }
    }
}

private const val ANIM = 280

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val appViewModel: AppViewModel = hiltViewModel()
    val voiceViewModel: VoiceViewModel = hiltViewModel()

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                tween(ANIM)
            ) + fadeIn(tween(ANIM))
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                tween(ANIM)
            ) + fadeOut(tween(ANIM))
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                tween(ANIM)
            ) + fadeIn(tween(ANIM))
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                tween(ANIM)
            ) + fadeOut(tween(ANIM))
        }
    ) {

        composable(Screen.Splash.route) {
            SplashScreen(
                viewModel = appViewModel,
                onFinished = {
                    val next = when {
                        !appViewModel.hasChosenLanguage -> Screen.Language.route
                        !appViewModel.onboardingSeen -> Screen.Onboarding.route
                        else -> Screen.Home.route
                    }
                    navController.navigate(next) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Language.route) {
            LanguageScreen(
                viewModel = appViewModel,
                // Only offer "back" when this was opened from Settings.
                onBack = if (appViewModel.onboardingSeen) {
                    { navController.popBackStack() }
                } else null,
                onLanguageSelected = {
                    if (appViewModel.onboardingSeen) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(Screen.Onboarding.route) {
                            popUpTo(Screen.Language.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                viewModel = appViewModel,
                onFinish = {
                    appViewModel.markOnboardingSeen()
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
                onBrowseAll = { navController.navigate(Screen.Courses.route) },
                onDetail = { matched ->
                    appViewModel.selectRole(matched)
                    navController.navigate(Screen.Detail.createRoute(matched.role.qp_code))
                }
            )
        }

        composable(
            route = Screen.Detail.route,
            arguments = listOf(navArgument("qp_code") { type = NavType.StringType })
        ) { backStackEntry ->
            val qpCode = remember(backStackEntry) {
                Screen.Detail.decodeArg(backStackEntry.arguments?.getString("qp_code"))
            }
            LaunchedEffect(qpCode) {
                if (appViewModel.selectedRole?.role?.qp_code != qpCode) {
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
