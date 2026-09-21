package com.thozhilthunai.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.thozhilthunai.app.data.model.Centre
import com.thozhilthunai.app.data.model.District
import com.thozhilthunai.app.data.model.I18nStrings
import com.thozhilthunai.app.data.model.JobRole
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    private fun readAsset(fileName: String): String =
        context.assets.open("data/$fileName").bufferedReader().use { it.readText() }

    suspend fun loadJobRoles(): List<JobRole> = withContext(Dispatchers.IO) {
        val json = readAsset("job_roles.json")
        val type = object : TypeToken<List<JobRole>>() {}.type
        gson.fromJson(json, type)
    }

    suspend fun loadCentres(): List<Centre> = withContext(Dispatchers.IO) {
        val json = readAsset("centres.json")
        val type = object : TypeToken<List<Centre>>() {}.type
        gson.fromJson(json, type)
    }

    suspend fun loadDistricts(): List<District> = withContext(Dispatchers.IO) {
        val json = readAsset("districts.json")
        val type = object : TypeToken<List<District>>() {}.type
        gson.fromJson(json, type)
    }

    suspend fun loadI18n(): I18nStrings = withContext(Dispatchers.IO) {
        val json = readAsset("i18n.json")
        gson.fromJson(json, I18nStrings::class.java)
    }
}
