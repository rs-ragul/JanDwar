package `in`.jandwar.app.ui.viewmodel

import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.jandwar.app.ai.AiConfig
import `in`.jandwar.app.data.model.*
import `in`.jandwar.app.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val repository: AppRepository,
    private val prefs: SharedPreferences,
    private val aiConfig: AiConfig
) : ViewModel() {

    var currentLang by mutableStateOf(prefs.getString("lang", "") ?: "")
        private set

    var onboardingPage by mutableStateOf(0)

    var profile by mutableStateOf(UserProfile())
        private set

    private val _results = MutableStateFlow<List<MatchedRole>>(emptyList())
    val results: StateFlow<List<MatchedRole>> = _results

    private val _allRoles = MutableStateFlow<List<JobRole>>(emptyList())
    val allRoles: StateFlow<List<JobRole>> = _allRoles

    var selectedRole by mutableStateOf<MatchedRole?>(null)
        private set

    var searchQuery by mutableStateOf("")

    // Use backing properties to avoid JVM setter clash with setXxx functions
    private var _offlineAiInstalled by mutableStateOf(prefs.getBoolean("offline_ai_installed", false))
    val offlineAiInstalled: Boolean get() = _offlineAiInstalled
    val isOfflineAiInstalled: Boolean get() = _offlineAiInstalled

    private var _ttsEngine by mutableStateOf(prefs.getString("tts_engine", "auto") ?: "auto")
    val ttsEngine: String get() = _ttsEngine

    init {
        aiConfig.load()
        viewModelScope.launch {
            _allRoles.value = repository.getJobRoles()
        }
        if (currentLang.isBlank()) {
            currentLang = "en"
        }
    }

    fun tr(key: String): String {
        val lang = if (currentLang.isBlank()) "en" else currentLang
        return repository.tr(lang, key)
    }

    fun interestLabel(key: String): String {
        val lang = if (currentLang.isBlank()) "en" else currentLang
        return repository.interestLabel(lang, key)
    }

    fun getInterestChips(): List<InterestChip> {
        val lang = if (currentLang.isBlank()) "en" else currentLang
        return repository.getInterestChips(lang)
    }

    fun setLanguage(code: String) {
        currentLang = code
        prefs.edit().putString("lang", code).apply()
    }

    fun updateEducation(edu: EducationLevel) {
        profile = profile.copy(education = edu)
    }

    fun updatePreference(pref: Preference) {
        profile = profile.copy(preference = pref)
    }

    fun updateMobility(mob: Mobility) {
        profile = profile.copy(mobility = mob)
    }

    fun updateDistrict(district: String) {
        profile = profile.copy(district = district)
    }

    fun toggleInterest(key: String) {
        val current = profile.interests.toMutableSet()
        if (current.contains(key)) current.remove(key) else current.add(key)
        profile = profile.copy(interests = current)
    }

    fun setInterests(interests: Set<String>) {
        profile = profile.copy(interests = interests)
    }

    fun applyProfileFragment(frag: `in`.jandwar.app.ai.ProfileFragment) {
        var newProfile = profile

        frag.edu?.let { aiEdu ->
            EducationLevel.fromAiString(aiEdu)?.let { edu ->
                newProfile = newProfile.copy(education = edu)
            }
        }
        frag.preference?.let { aiPref ->
            Preference.fromAiString(aiPref)?.let { pref ->
                newProfile = newProfile.copy(preference = pref)
            }
        }
        frag.mobility?.let { aiMob ->
            Mobility.fromAiString(aiMob)?.let { mob ->
                newProfile = newProfile.copy(mobility = mob)
            }
        }
        frag.district?.let { d ->
            if (d.isNotBlank()) newProfile = newProfile.copy(district = d)
        }
        frag.familyOccupation?.let { fo ->
            if (fo.isNotBlank()) newProfile = newProfile.copy(familyOccupation = fo)
        }
        frag.currentLivelihood?.let { cl ->
            if (cl.isNotBlank()) newProfile = newProfile.copy(currentLivelihood = cl)
        }
        frag.physicalConstraints?.let { pc ->
            if (pc.isNotBlank()) newProfile = newProfile.copy(physicalConstraints = pc)
        }
        frag.localOpportunity?.let { lo ->
            if (lo.isNotBlank()) newProfile = newProfile.copy(localOpportunity = lo)
        }
        if (frag.hasInterests()) {
            val merged = newProfile.interests.toMutableSet()
            merged.addAll(frag.interests)
            newProfile = newProfile.copy(interests = merged)
        }
        if (frag.hasSkills()) {
            val merged = newProfile.skills.toMutableSet()
            merged.addAll(frag.skills)
            newProfile = newProfile.copy(skills = merged)
        }

        profile = newProfile
    }

    fun updateFamilyOccupation(occ: String) {
        profile = profile.copy(familyOccupation = occ)
    }

    fun updateCurrentLivelihood(liv: String) {
        profile = profile.copy(currentLivelihood = liv)
    }

    fun updatePhysicalConstraints(constraints: String) {
        profile = profile.copy(physicalConstraints = constraints)
    }

    fun updateLocalOpportunity(opp: String) {
        profile = profile.copy(localOpportunity = opp)
    }

    fun runMatching() {
        viewModelScope.launch {
            _results.value = repository.matchRoles(profile)
        }
    }

    fun selectRole(role: MatchedRole) {
        selectedRole = role
    }

    fun selectRoleByCode(qpCode: String) {
        val all = _allRoles.value
        val job = all.find { it.qp_code == qpCode } ?: return
        val centre = repository.getCentreForDistrict(profile.district)
        val matched = MatchedRole(
            role = job,
            score = 100,
            reason = tr("reason_base"),
            skillGapNote = "Check eligibility with centre",
            centre = centre
        )
        selectedRole = matched
    }

    fun getDistricts(): List<String> {
        return repository.getDistricts().all
    }

    fun getCentreForDistrict(district: String): Centre? {
        return repository.getCentreForDistrict(district)
    }

    fun filteredRoles(): List<JobRole> {
        val q = searchQuery
        return if (q.isBlank()) _allRoles.value else repository.searchRoles(q, currentLang)
    }

    fun updateTtsEngine(engine: String) {
        _ttsEngine = engine
        prefs.edit().putString("tts_engine", engine).apply()
    }

    fun updateOfflineAiInstalled(installed: Boolean) {
        _offlineAiInstalled = installed
        prefs.edit().putBoolean("offline_ai_installed", installed).apply()
    }

    fun resetOnboarding() {
        onboardingPage = 0
    }

    fun nextOnboarding() {
        if (onboardingPage < 2) onboardingPage++
    }

    fun prevOnboarding(): Boolean {
        return if (onboardingPage > 0) {
            onboardingPage--
            true
        } else false
    }

    fun clearProfile() {
        profile = UserProfile()
        _results.value = emptyList()
    }
}
