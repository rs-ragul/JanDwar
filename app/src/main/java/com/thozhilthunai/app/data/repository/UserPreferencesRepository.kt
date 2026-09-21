package com.thozhilthunai.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val LANGUAGE_KEY = stringPreferencesKey("language")
    private val TEXT_SIZE_KEY = intPreferencesKey("text_size")
    private val ONBOARDING_DONE_KEY = stringPreferencesKey("onboarding_done")

    val selectedLanguage: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[LANGUAGE_KEY]
    }

    val textSizeIndex: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[TEXT_SIZE_KEY] ?: 1 // 0=small,1=medium,2=large
    }

    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ONBOARDING_DONE_KEY] == "true"
    }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { prefs -> prefs[LANGUAGE_KEY] = lang }
    }

    suspend fun setTextSizeIndex(index: Int) {
        context.dataStore.edit { prefs -> prefs[TEXT_SIZE_KEY] = index }
    }

    suspend fun markOnboardingDone() {
        context.dataStore.edit { prefs -> prefs[ONBOARDING_DONE_KEY] = "true" }
    }
}
