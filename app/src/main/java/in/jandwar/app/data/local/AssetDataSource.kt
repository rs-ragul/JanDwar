package `in`.jandwar.app.data.local

import android.content.Context
import android.util.Log
import `in`.jandwar.app.data.model.Centre
import `in`.jandwar.app.data.model.DistrictEconomy
import `in`.jandwar.app.data.model.DistrictsData
import `in`.jandwar.app.data.model.StateDistricts
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
    /**
     * `coerceInputValues` is load-bearing, not decoration.
     *
     * The researched catalogue carries `"nsqf_level": null` on 9 rows and
     * `"notional_hours": null` on 19. kotlinx.serialization treats a null for
     * a non-nullable property as a hard error, and because the whole file is
     * decoded in one call, those 28 values were emptying all 540 roles — the
     * app then showed "no match found" on every screen with no other symptom.
     * Coercion maps such a null onto the property default instead.
     */
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Never let one malformed record empty the catalogue.
     *
     * The strict decode runs first because it is fast and type-checked. If it
     * still throws, we fall back to a row-by-row `org.json` read that skips
     * only the rows it cannot understand. A skipped row costs one course; a
     * thrown exception used to cost all of them.
     */
    fun loadJobRoles(): List<JobRole> {
        try {
            val text = readAsset("job_roles.json")
            val strict = jsonParser.decodeFromString<List<JobRole>>(text)
            if (strict.isNotEmpty()) return strict.filter { it.isValidName() }
        } catch (e: Exception) {
            Log.e(TAG, "job_roles.json strict decode failed: ${e.message}")
        }
        return loadJobRolesLenient()
    }

    private fun loadJobRolesLenient(): List<JobRole> {
        return try {
            val arr = JSONArray(readAsset("job_roles.json"))
            val out = ArrayList<JobRole>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(
                    JobRole(
                        qp_code = o.str("qp_code"),
                        job_role = o.str("job_role"),
                        nsqf_level = o.str("nsqf_level"),
                        notional_hours = o.str("notional_hours"),
                        ssc = o.str("ssc"),
                        sector = o.str("sector")
                    )
                )
            }
            Log.w(TAG, "job_roles.json read leniently: ${out.size} rows")
            out.filter { it.isValidName() }
        } catch (e: Exception) {
            Log.e(TAG, "job_roles.json unreadable: ${e.message}")
            emptyList()
        }
    }

    fun loadCentres(): List<Centre> {
        try {
            val text = readAsset("centres.json")
            val strict = jsonParser.decodeFromString<List<Centre>>(text)
            if (strict.isNotEmpty()) return strict
        } catch (e: Exception) {
            Log.e(TAG, "centres.json strict decode failed: ${e.message}")
        }
        return try {
            val arr = JSONArray(readAsset("centres.json"))
            val out = ArrayList<Centre>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(
                    Centre(
                        state = o.str("state"),
                        district = o.str("district"),
                        name = o.str("name"),
                        address = o.strOrNull("address"),
                        phone = o.strOrNull("phone"),
                        trades = o.strOrNull("trades"),
                        dairy_course = if (o.isNull("dairy_course")) null else o.optBoolean("dairy_course"),
                        confidence = o.str("confidence"),
                        confidence_note = o.strOrNull("confidence_note"),
                        source = o.strOrNull("source"),
                        retrieved = o.strOrNull("retrieved")
                    )
                )
            }
            Log.w(TAG, "centres.json read leniently: ${out.size} rows")
            out
        } catch (e: Exception) {
            Log.e(TAG, "centres.json unreadable: ${e.message}")
            emptyList()
        }
    }

    /** `""` for absent or JSON-null, never the literal string "null". */
    private fun JSONObject.str(key: String): String =
        if (isNull(key)) "" else optString(key, "")

    private fun JSONObject.strOrNull(key: String): String? =
        if (isNull(key)) null else optString(key, "").takeIf { it.isNotBlank() }

    /**
     * Flat lists **and** the per-state breakdown.
     *
     * `by_state` arrived with the five-state research drop and was being
     * dropped on the floor by `ignoreUnknownKeys`, which is why the picker
     * still offered one undifferentiated list of 187 districts. Parsed with
     * `org.json` throughout so a sixth state with a slightly different shape
     * degrades to "no per-state list" rather than to "no districts at all".
     */
    fun loadDistricts(): DistrictsData {
        return try {
            val obj = JSONObject(readAsset("districts.json"))
            val byState = LinkedHashMap<String, StateDistricts>()
            obj.optJSONObject("by_state")?.let { bs ->
                val keys = bs.keys()
                while (keys.hasNext()) {
                    val state = keys.next()
                    val inner = bs.optJSONObject(state) ?: continue
                    byState[state] = StateDistricts(
                        all = inner.stringList("all"),
                        with_centre = inner.stringList("with_centre"),
                        without_centre = inner.stringList("without_centre")
                    )
                }
            }
            DistrictsData(
                all = obj.stringList("all"),
                with_centre = obj.stringList("with_centre"),
                without_centre = obj.stringList("without_centre"),
                by_state = byState
            )
        } catch (e: Exception) {
            Log.e(TAG, "districts.json: ${e.message}")
            DistrictsData()
        }
    }

    private fun JSONObject.stringList(key: String): List<String> {
        val arr = optJSONArray(key) ?: return emptyList()
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            arr.optString(i).takeIf { it.isNotBlank() }?.let(out::add)
        }
        return out
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

    /**
     * Per-district livelihood notes: what the local economy actually runs on.
     *
     * Parsed loosely with JSONObject rather than a strict serializer because
     * the shape varies by state and a new state should never be able to stop
     * the app from starting. A missing or malformed file yields an empty map
     * and the region line simply falls back to the centre name.
     */
    fun loadDistrictEconomy(): Map<String, DistrictEconomy> {
        return try {
            val obj = JSONObject(readAsset("district_economy.json"))
            val out = HashMap<String, DistrictEconomy>()
            val it = obj.keys()
            while (it.hasNext()) {
                val k = it.next()
                val o = obj.optJSONObject(k) ?: continue
                val arr = o.optJSONArray("strong_sectors")
                val sectors = ArrayList<String>()
                if (arr != null) for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf { v -> v.isNotBlank() }?.let(sectors::add)
                }
                out[k] = DistrictEconomy(
                    district = k,
                    note = o.optString("note", ""),
                    strongSectors = sectors
                )
            }
            out
        } catch (e: Exception) {
            Log.e(TAG, "district_economy.json: ${e.message}")
            emptyMap()
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

    private companion object {
        const val TAG = "AssetDataSource"
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
