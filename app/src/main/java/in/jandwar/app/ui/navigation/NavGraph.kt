package `in`.jandwar.app.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Language : Screen("language")
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object Intake : Screen("intake")
    object Results : Screen("results")
    object Detail : Screen("detail/{qp_code}") {
        fun createRoute(qpCode: String) = "detail/$qpCode"
    }
    object Courses : Screen("courses")
    object Voice : Screen("voice")
    object Settings : Screen("settings")
}
