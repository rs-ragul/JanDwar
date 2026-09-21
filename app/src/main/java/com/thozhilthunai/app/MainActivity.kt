package com.thozhilthunai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import com.thozhilthunai.app.data.repository.UserPreferencesRepository
import com.thozhilthunai.app.ui.AppNavigation
import com.thozhilthunai.app.ui.AppViewModel
import com.thozhilthunai.app.ui.theme.ThozhilThunaiTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    @Inject
    lateinit var prefsRepository: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val textSizeIndex by appViewModel.textSizeIndex.collectAsState()
            ThozhilThunaiTheme(textScaleIndex = textSizeIndex) {
                AppNavigation(
                    appViewModel = appViewModel,
                    prefsRepository = prefsRepository
                )
            }
        }
    }
}
