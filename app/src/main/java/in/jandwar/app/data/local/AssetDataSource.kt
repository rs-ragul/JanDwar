package `in`.jandwar.app.data.local

import android.content.Context
import `in`.jandwar.app.data.model.Centre
import `in`.jandwar.app.data.model.DistrictsData
import `in`.jandwar.app.data.model.I18nData
import `in`.jandwar.app.data.model.JobRole
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetDataSource @Inject constructor(
    private val context: Context
) {
    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

    fun loadJobRoles(): List<JobRole> {
        return try {
            val text = readAsset("job_roles.json")
            jsonParser.decodeFromString<List<JobRole>>(text).filter { it.isValidName() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun loadCentres(): List<Centre> {
        return try {
            val text = readAsset("centres.json")
            jsonParser.decodeFromString<List<Centre>>(text)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun loadDistricts(): DistrictsData {
        return try {
            val text = readAsset("districts.json")
            jsonParser.decodeFromString<DistrictsData>(text)
        } catch (e: Exception) {
            // Fallback parse via JSONObject for older format
            try {
                val obj = JSONObject(readAsset("districts.json"))
                val all = mutableListOf<String>()
                val withCentre = mutableListOf<String>()
                val withoutCentre = mutableListOf<String>()
                obj.optJSONArray("all")?.let { arr ->
                    for (i in 0 until arr.length()) all.add(arr.optString(i))
                }
                obj.optJSONArray("with_centre")?.let { arr ->
                    for (i in 0 until arr.length()) withCentre.add(arr.optString(i))
                }
                obj.optJSONArray("without_centre")?.let { arr ->
                    for (i in 0 until arr.length()) withoutCentre.add(arr.optString(i))
                }
                DistrictsData(all, withCentre, withoutCentre)
            } catch (e2: Exception) {
                DistrictsData()
            }
        }
    }

    /**
     * Read a `lang -> key -> text` object out of [obj] under [name] into [into].
     * Absent or malformed sections are skipped rather than failing the load.
     */
    private fun readLangMap(
        obj: JSONObject,
        name: String,
        into: MutableMap<String, Map<String, String>>
    ) {
        val section = obj.optJSONObject(name) ?: return
        val langs = section.keys()
        while (langs.hasNext()) {
            val lang = langs.next()
            val inner = section.optJSONObject(lang) ?: continue
            val map = mutableMapOf<String, String>()
            val keys = inner.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = inner.optString(key)
            }
            into[lang] = map
        }
    }

    fun loadI18n(): I18nData {
        return try {
            val text = readAsset("i18n.json")
            val obj = JSONObject(text)
            val langs = mutableListOf<Pair<String, String>>()
            obj.optJSONArray("langs")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val inner = arr.optJSONArray(i)
                    if (inner != null && inner.length() >= 2) {
                        langs.add(inner.optString(0) to inner.optString(1))
                    }
                }
            }
            val strings = mutableMapOf<String, Map<String, String>>()
            obj.optJSONObject("strings")?.let { stringsObj ->
                val keys = stringsObj.keys()
                while (keys.hasNext()) {
                    val lang = keys.next()
                    val map = mutableMapOf<String, String>()
                    val inner = stringsObj.optJSONObject(lang)
                    inner?.keys()?.let { k ->
                        while (k.hasNext()) {
                            val key = k.next()
                            map[key] = inner.optString(key)
                        }
                    }
                    strings[lang] = map
                }
            }
            val interests = mutableMapOf<String, Map<String, String>>()
            // "interest" is the current spelling; "interests" is accepted too so an
            // older asset file still loads.
            readLangMap(obj, "interest", interests)
            readLangMap(obj, "interests", interests)

            val occupations = mutableMapOf<String, Map<String, String>>()
            readLangMap(obj, "occupation", occupations)

            I18nData(langs, strings, interests, occupations)
        } catch (e: Exception) {
            e.printStackTrace()
            I18nData()
        }
    }

    fun loadConfigJson(): JSONObject {
        return try {
            val text = readAsset("config.json")
            JSONObject(text)
        } catch (e: Exception) {
            JSONObject()
        }
    }

    /** Multilingual surface forms for the on-device NLU. */
    fun loadLexicon(): JSONObject {
        return try {
            JSONObject(readAsset("lexicon.json"))
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun readAsset(name: String): String {
        val input = context.assets.open(name)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            output.write(buffer, 0, read)
        }
        input.close()
        return output.toString("UTF-8")
    }
}
