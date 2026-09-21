package com.thozhilthunai.app.data.repository

import com.thozhilthunai.app.data.model.Centre
import com.thozhilthunai.app.data.model.District
import com.thozhilthunai.app.data.model.I18nStrings
import com.thozhilthunai.app.data.model.JobRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataRepository @Inject constructor(
    private val assetDataSource: AssetDataSource
) {
    private val _jobRoles = MutableStateFlow<List<JobRole>>(emptyList())
    val jobRoles: Flow<List<JobRole>> = _jobRoles.asStateFlow()

    private val _centres = MutableStateFlow<List<Centre>>(emptyList())
    val centres: Flow<List<Centre>> = _centres.asStateFlow()

    private val _districts = MutableStateFlow<List<District>>(emptyList())
    val districts: Flow<List<District>> = _districts.asStateFlow()

    private val _i18n = MutableStateFlow<I18nStrings?>(null)
    val i18n: Flow<I18nStrings?> = _i18n.asStateFlow()

    private var loaded = false

    suspend fun loadAll() {
        if (loaded) return
        _jobRoles.value = assetDataSource.loadJobRoles()
        _centres.value = assetDataSource.loadCentres()
        _districts.value = assetDataSource.loadDistricts()
        _i18n.value = assetDataSource.loadI18n()
        loaded = true
    }

    fun getJobRoleSnapshot(): List<JobRole> = _jobRoles.value
    fun getCentreSnapshot(): List<Centre> = _centres.value
    fun getDistrictSnapshot(): List<District> = _districts.value

    fun getCentresForDistrict(district: String): List<Centre> =
        _centres.value.filter { it.district.equals(district, ignoreCase = true) }

    fun getString(lang: String, key: String): String =
        _i18n.value?.strings?.get(lang)?.get(key) ?: key

    fun getInterestLabel(lang: String, key: String): String =
        _i18n.value?.interest?.get(lang)?.get(key) ?: key

    fun getAllInterestKeys(): List<String> =
        _i18n.value?.interest?.get("en")?.keys?.toList() ?: emptyList()
}
