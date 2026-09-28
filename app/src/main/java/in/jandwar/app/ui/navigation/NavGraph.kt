package `in`.jandwar.app.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Language : Screen("language")
    data object Onboarding : Screen("onboarding")
    data object Home : Screen("home")
    data object Intake : Screen("intake")
    data object Results : Screen("results")
    data object Courses : Screen("courses")
    data object Voice : Screen("voice")
    data object Settings : Screen("settings")

    data object Detail : Screen("detail/{qp_code}") {
        /**
         * Every NSQF QP code contains a slash (e.g. `AGR/Q0101`), which would
         * otherwise be read as an extra path segment and never match this
         * route. Encode it so it stays a single argument.
         */
        fun createRoute(qpCode: String): String = "detail/${Uri.encode(qpCode)}"

        /** Inverse of [createRoute]; safe to call on an already-decoded value. */
        fun decodeArg(raw: String?): String = raw?.let(Uri::decode).orEmpty()
    }
}
