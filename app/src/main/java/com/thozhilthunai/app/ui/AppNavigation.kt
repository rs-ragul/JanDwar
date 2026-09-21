package com.thozhilthunai.app.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.thozhilthunai.app.data.repository.UserPreferencesRepository
import com.thozhilthunai.app.ui.screens.detail.DetailScreen
import com.thozhilthunai.app.ui.screens.home.HomeScreen
import com.thozhilthunai.app.ui.screens.intake.IntakeScreen
import com.thozhilthunai.app.ui.screens.language.LanguageScreen
import com.thozhilthunai.app.ui.screens.onboarding.OnboardingScreen
import com.thozhilthunai.app.ui.screens.results.ResultsScreen
import com.thozhilthunai.app.ui.screens.settings.SettingsScreen
import com.thozhilthunai.app.ui.screens.splash.SplashScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

// Route keys
object Routes {
    const val SPLASH = "splash"
    const val LANGUAGE = "language"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val INTAKE = "intake"
    const val RESULTS = "results"
    const val SETTINGS = "settings"
    fun detail(qpCode: String) = "detail/$qpCode"
}

@Composable
fun AppNavigation(
    appViewModel: AppViewModel,
    prefsRepository: UserPreferencesRepository
) {
    val backStack = rememberNavBackStack(Routes.SPLASH)

    NavDisplay(backStack = backStack) { key ->
        when {
            key == Routes.SPLASH -> NavEntry(key) {
                SplashScreen(
                    onDone = {
                        // Decide next screen based on persisted state
                        val lang = runBlocking { prefsRepository.selectedLanguage.first() }
                        val onboardingDone = runBlocking { prefsRepository.onboardingDone.first() }
                        backStack.clear()
                        if (lang == null) {
                            backStack.add(Routes.LANGUAGE)
                        } else if (!onboardingDone) {
                            backStack.add(Routes.ONBOARDING)
                        } else {
                            backStack.add(Routes.HOME)
                        }
                    }
                )
            }
            key == Routes.LANGUAGE -> NavEntry(key) {
                LanguageScreen(
                    appViewModel = appViewModel,
                    onLanguageSelected = {
                        // After language picked, check if onboarding needed
                        val onboardingDone = runBlocking { prefsRepository.onboardingDone.first() }
                        if (onboardingDone) backStack.add(Routes.HOME)
                        else backStack.add(Routes.ONBOARDING)
                    }
                )
            }
            key == Routes.ONBOARDING -> NavEntry(key) {
                OnboardingScreen(
                    appViewModel = appViewModel,
                    onDone = {
                        runBlocking { prefsRepository.markOnboardingDone() }
                        backStack.clear()
                        backStack.add(Routes.HOME)
                    }
                )
            }
            key == Routes.HOME -> NavEntry(key) {
                HomeScreen(
                    appViewModel = appViewModel,
                    onFindCourse = { backStack.add(Routes.INTAKE) },
                    onSettings = { backStack.add(Routes.SETTINGS) }
                )
            }
            key == Routes.INTAKE -> NavEntry(key) {
                IntakeScreen(
                    appViewModel = appViewModel,
                    onResults = { qpCodes -> backStack.add("${Routes.RESULTS}/$qpCodes") },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            key.toString().startsWith(Routes.RESULTS) -> NavEntry(key) {
                ResultsScreen(
                    appViewModel = appViewModel,
                    onRoleClick = { qpCode -> backStack.add(Routes.detail(qpCode)) },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            key.toString().startsWith("detail/") -> NavEntry(key) {
                val qpCode = key.toString().removePrefix("detail/")
                DetailScreen(
                    appViewModel = appViewModel,
                    qpCode = qpCode,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            key == Routes.SETTINGS -> NavEntry(key) {
                SettingsScreen(
                    appViewModel = appViewModel,
                    onBack = { backStack.removeLastOrNull() },
                    onChangeLanguage = {
                        backStack.clear()
                        backStack.add(Routes.LANGUAGE)
                    }
                )
            }
            else -> NavEntry(key) { /* fallback */ }
        }
    }
}

private fun NavBackStack.clear() {
    while (size > 0) removeLastOrNull()
}
