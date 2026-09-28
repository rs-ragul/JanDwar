package `in`.jandwar.app.ui.viewmodel

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.jandwar.app.ai.AiConfig
import `in`.jandwar.app.ai.MicEarcon
import `in`.jandwar.app.ai.TtsSpeaker
import `in`.jandwar.app.ai.ProfileFragment
import `in`.jandwar.app.data.model.Centre
import `in`.jandwar.app.data.model.EducationLevel
import `in`.jandwar.app.data.model.InterestChip
import `in`.jandwar.app.data.model.JobRole
import `in`.jandwar.app.data.model.MatchedRole
import `in`.jandwar.app.data.model.Mobility
import `in`.jandwar.app.data.model.Preference
import `in`.jandwar.app.data.model.UserProfile
import `in`.jandwar.app.data.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val repository: AppRepository,
    private val prefs: SharedPreferences,
    private val aiConfig: AiConfig,
    private val tts: TtsSpeaker,
    private val earcon: MicEarcon
) : ViewModel() {

    // ── Language ────────────────────────────────────────────────────────────

    /**
     * Whether the user has ever picked a language. Kept separate from
     * [currentLang] so the language screen is still reachable on first launch
     * while the rest of the app can always read a usable language code.
     */
    var hasChosenLanguage by mutableStateOf(prefs.getBoolean(KEY_LANG_CHOSEN, false))
        private set

    var currentLang by mutableStateOf(prefs.getString(KEY_LANG, "en") ?: "en")
        private set

    var onboardingSeen by mutableStateOf(prefs.getBoolean(KEY_ONBOARDING_SEEN, false))
        private set

    var onboardingPage by mutableStateOf(0)

    // ── Profile & results ───────────────────────────────────────────────────

    var profile by mutableStateOf(UserProfile())
        private set

    private val _results = MutableStateFlow<List<MatchedRole>>(emptyList())
    val results: StateFlow<List<MatchedRole>> = _results.asStateFlow()

    private val _allRoles = MutableStateFlow<List<JobRole>>(emptyList())
    val allRoles: StateFlow<List<JobRole>> = _allRoles.asStateFlow()

    var isMatching by mutableStateOf(false)
        private set

    var selectedRole by mutableStateOf<MatchedRole?>(null)
        private set

    var searchQuery by mutableStateOf("")
    var sectorFilter by mutableStateOf<String?>(null)

    /** Spoken/AI narration of the results, produced by the voice flow. */
    var resultNarration by mutableStateOf("")
        private set

    // ── Settings ────────────────────────────────────────────────────────────

    private var _ttsEngine by mutableStateOf(prefs.getString(KEY_TTS_ENGINE, "auto") ?: "auto")
    val ttsEngine: String get() = _ttsEngine

    private var _micCue by mutableStateOf(prefs.getBoolean(KEY_MIC_CUE, true))
    val micCue: Boolean get() = _micCue

    val aiReady: Boolean get() = aiConfig.groqEnabled()

    // ── Init ────────────────────────────────────────────────────────────────

    init {
        aiConfig.load()
        applyTtsEngine()
        earcon.enabled = _micCue
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { repository.getJobRoles() }
            _allRoles.value = loaded
        }
    }

    // ── Strings ─────────────────────────────────────────────────────────────

    fun tr(key: String): String = repository.tr(currentLang, key)

    fun interestLabel(key: String): String = repository.interestLabel(currentLang, key)

    fun occupationLabel(raw: String): String = repository.occupationLabel(currentLang, raw)

    fun sectorLabel(sector: String): String = repository.sectorLabel(sector)

    fun getInterestChips(): List<InterestChip> = repository.getInterestChips(currentLang)

    fun availableLanguages(): List<Pair<String, String>> = repository.availableLanguages()

    fun setLanguage(code: String) {
        currentLang = code
        hasChosenLanguage = true
        prefs.edit()
            .putString(KEY_LANG, code)
            .putBoolean(KEY_LANG_CHOSEN, true)
            .apply()
    }

    fun markOnboardingSeen() {
        onboardingSeen = true
        prefs.edit().putBoolean(KEY_ONBOARDING_SEEN, true).apply()
    }

    // ── Profile edits ───────────────────────────────────────────────────────

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
        if (!current.remove(key)) current.add(key)
        profile = profile.copy(interests = current)
    }

    fun setInterests(interests: Set<String>) {
        profile = profile.copy(interests = interests)
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

    /** Folds everything the voice interview understood into the form profile. */
    fun applyProfileFragment(frag: ProfileFragment) {
        var p = profile

        EducationLevel.fromAiString(frag.edu)?.let { p = p.copy(education = it) }
        Preference.fromAiString(frag.preference)?.let { p = p.copy(preference = it) }
        Mobility.fromAiString(frag.mobility)?.let { p = p.copy(mobility = it) }

        frag.district?.takeIf { it.isNotBlank() }?.let { p = p.copy(district = it) }
        frag.familyOccupation?.takeIf { it.isNotBlank() }?.let { p = p.copy(familyOccupation = it) }
        frag.currentLivelihood?.takeIf { it.isNotBlank() }
            ?.let { p = p.copy(currentLivelihood = it) }
        frag.physicalConstraints?.takeIf { it.isNotBlank() }
            ?.let { p = p.copy(physicalConstraints = it) }
        frag.localOpportunity?.takeIf { it.isNotBlank() }
            ?.let { p = p.copy(localOpportunity = it) }

        if (frag.interests.isNotEmpty()) {
            p = p.copy(interests = p.interests + frag.interests)
        }
        if (frag.skills.isNotEmpty()) {
            p = p.copy(skills = p.skills + frag.skills)
        }
        profile = p
    }

    // ── Matching ────────────────────────────────────────────────────────────

    fun runMatching(onReady: ((List<MatchedRole>) -> Unit)? = null) {
        viewModelScope.launch {
            isMatching = true
            resultNarration = ""
            val snapshot = profile
            val lang = currentLang
            val matched = withContext(Dispatchers.Default) {
                repository.matchRoles(snapshot, lang)
            }
            _results.value = matched
            isMatching = false
            onReady?.invoke(matched)
        }
    }

    /**
     * Applies the interview result and matches in one step, so the voice screen
     * and the form screen both end up feeding the same result list.
     */
    fun completeInterview(frag: ProfileFragment, onReady: ((List<MatchedRole>) -> Unit)? = null) {
        applyProfileFragment(frag)
        runMatching(onReady)
    }

    fun updateResultNarration(text: String) {
        resultNarration = text
    }

    fun selectRole(role: MatchedRole) {
        selectedRole = role
    }

    fun selectRoleByCode(qpCode: String) {
        val existing = _results.value.firstOrNull { it.role.qp_code == qpCode }
        if (existing != null) {
            selectedRole = existing
            return
        }
        val job = repository.roleByCode(qpCode) ?: return
        selectedRole = MatchedRole(
            role = job,
            score = 0,
            confidence = 0,
            reason = repository.tr(currentLang, "reason_browse"),
            skillGapNote = repository.tr(currentLang, "gap_generic"),
            centre = repository.getCentreForDistrict(profile.district)
        )
    }

    // ── Browse ──────────────────────────────────────────────────────────────

    fun getDistricts(): List<String> = repository.getDistricts().all

    fun districtsWithCentre(): List<String> = repository.getDistricts().with_centre

    fun getCentreForDistrict(district: String): Centre? =
        repository.getCentreForDistrict(district)

    fun sectors(): List<String> = repository.sectors()

    fun fundableCount(): Int = repository.fundableCount()

    fun totalRoleCount(): Int = _allRoles.value.size

    fun filteredRoles(): List<JobRole> = repository.searchRoles(searchQuery, sectorFilter)

    // ── Settings ────────────────────────────────────────────────────────────

    fun updateTtsEngine(engine: String) {
        _ttsEngine = engine
        prefs.edit().putString(KEY_TTS_ENGINE, engine).apply()
        applyTtsEngine()
    }

    /** "device" restricts playback to voices that need no network. */
    private fun applyTtsEngine() {
        tts.setDeviceVoicesOnly(_ttsEngine == "device")
    }

    fun updateMicCue(on: Boolean) {
        _micCue = on
        prefs.edit().putBoolean(KEY_MIC_CUE, on).apply()
        earcon.enabled = on
    }

    fun resetOnboarding() {
        onboardingPage = 0
    }

    fun nextOnboarding() {
        if (onboardingPage < 2) onboardingPage++
    }

    fun prevOnboarding(): Boolean =
        if (onboardingPage > 0) {
            onboardingPage--
            true
        } else false

    fun clearProfile() {
        profile = UserProfile()
        _results.value = emptyList()
        selectedRole = null
        resultNarration = ""
    }

    companion object {
        private const val KEY_LANG = "lang"
        private const val KEY_LANG_CHOSEN = "lang_chosen"
        private const val KEY_ONBOARDING_SEEN = "onboarding_seen"
        private const val KEY_TTS_ENGINE = "tts_engine"
        private const val KEY_MIC_CUE = "mic_cue"
    }
}
