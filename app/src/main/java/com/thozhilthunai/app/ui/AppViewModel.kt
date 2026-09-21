package com.thozhilthunai.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thozhilthunai.app.data.repository.DataRepository
import com.thozhilthunai.app.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val dataRepository: DataRepository,
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    private val _currentLang = MutableStateFlow("en")
    val currentLang: StateFlow<String> = _currentLang.asStateFlow()

    private val _textSizeIndex = MutableStateFlow(1)
    val textSizeIndex: StateFlow<Int> = _textSizeIndex.asStateFlow()

    private val _dataLoaded = MutableStateFlow(false)
    val dataLoaded: StateFlow<Boolean> = _dataLoaded.asStateFlow()

    init {
        viewModelScope.launch {
            dataRepository.loadAll()
            _dataLoaded.value = true
            // Restore persisted language
            val lang = prefsRepository.selectedLanguage.first()
            if (lang != null) _currentLang.value = lang
            // Restore text size
            _textSizeIndex.value = prefsRepository.textSizeIndex.first()
        }
    }

    fun setLanguage(lang: String) {
        _currentLang.value = lang
        viewModelScope.launch { prefsRepository.setLanguage(lang) }
    }

    fun setTextSizeIndex(index: Int) {
        _textSizeIndex.value = index
        viewModelScope.launch { prefsRepository.setTextSizeIndex(index) }
    }

    fun str(key: String): String = dataRepository.getString(_currentLang.value, key)

    fun interestLabel(key: String): String =
        dataRepository.getInterestLabel(_currentLang.value, key)

    fun getAllInterestKeys(): List<String> = dataRepository.getAllInterestKeys()

    fun totalRoles(): Int = (dataRepository.jobRoles as kotlinx.coroutines.flow.StateFlow).value.size
    fun totalCentres(): Int = (dataRepository.centres as kotlinx.coroutines.flow.StateFlow).value.size
}
