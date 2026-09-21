package com.thozhilthunai.app.ui

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
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
import kotlinx.serialization.Serializable

// Route keys
@Serializable
data object Splash : NavKey

@Serializable
data object Language : NavKey

@Serializable
data object Onboarding : NavKey

@Serializable
data object Home : NavKey

@Serializable
data object Intake : NavKey

@Serializable
data class Results(val qpCodes: String) : NavKey

@Serializable
data class Detail(val qpCode: String) : NavKey

@Serializable
data object Settings : NavKey

@Composable
fun AppNavigation(
    appViewModel: AppViewModel,
    prefsRepository: UserPreferencesRepository
) {
    val backStack = rememberNavBackStack(Splash)

    NavDisplay(backStack = backStack) { key ->
        when (key) {
            is Splash -> NavEntry(key) {
                SplashScreen(
                    onDone = {
                        // Decide next screen based on persisted state
                        val lang = runBlocking { prefsRepository.selectedLanguage.first() }
                        val onboardingDone = runBlocking { prefsRepository.onboardingDone.first() }
                        backStack.clear()
                        if (lang == null) {
                            backStack.add(Language)
                        } else if (!onboardingDone) {
                            backStack.add(Onboarding)
                        } else {
                            backStack.add(Home)
                        }
                    }
                )
            }
            is Language -> NavEntry(key) {
                LanguageScreen(
                    appViewModel = appViewModel,
                    onLanguageSelected = {
                        // After language picked, check if onboarding needed
                        val onboardingDone = runBlocking { prefsRepository.onboardingDone.first() }
                        if (onboardingDone) backStack.add(Home)
                        else backStack.add(Onboarding)
                    }
                )
            }
            is Onboarding -> NavEntry(key) {
                OnboardingScreen(
                    appViewModel = appViewModel,
                    onDone = {
                        runBlocking { prefsRepository.markOnboardingDone() }
                        backStack.clear()
                        backStack.add(Home)
                    }
                )
            }
            is Home -> NavEntry(key) {
                HomeScreen(
                    appViewModel = appViewModel,
                    onFindCourse = { backStack.add(Intake) },
                    onSettings = { backStack.add(Settings) }
                )
            }
            is Intake -> NavEntry(key) {
                IntakeScreen(
                    appViewModel = appViewModel,
                    onResults = { qpCodes -> backStack.add(Results(qpCodes)) },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            is Results -> NavEntry(key) {
                ResultsScreen(
                    appViewModel = appViewModel,
                    onRoleClick = { qpCode -> backStack.add(Detail(qpCode)) },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            is Detail -> NavEntry(key) {
                DetailScreen(
                    appViewModel = appViewModel,
                    qpCode = key.qpCode,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            is Settings -> NavEntry(key) {
                SettingsScreen(
                    appViewModel = appViewModel,
                    onBack = { backStack.removeLastOrNull() },
                    onChangeLanguage = {
                        backStack.clear()
                        backStack.add(Language)
                    }
                )
            }
            else -> NavEntry(key) { /* fallback */ }
        }
    }
}

private fun <T : NavKey> NavBackStack<T>.clear() {
    while (size > 0) removeLastOrNull()
}
