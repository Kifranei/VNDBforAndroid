package app.vndb.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.vndb.data.model.ColorMode
import app.vndb.data.model.FavoriteItem
import app.vndb.data.model.NsfwPolicy
import app.vndb.data.model.TitlePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("vndb_prefs")

data class UserSettings(
    val colorMode: ColorMode = ColorMode.SYSTEM,
    val titlePreference: TitlePreference = TitlePreference.CHINESE,
    val nsfwPolicy: NsfwPolicy = NsfwPolicy.HIDE,
    val apiToken: String = "",
    val spoilerLevel: Int = 0,
    val userId: String = "",
    val username: String = "",
    val liquidGlassBar: Boolean = false,
)

class PreferencesRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    val settings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            colorMode = ColorMode.entries.getOrElse(prefs[COLOR_MODE] ?: 0) { ColorMode.SYSTEM },
            titlePreference = TitlePreference.entries.getOrElse(prefs[TITLE_PREF] ?: 3) { TitlePreference.CHINESE },
            nsfwPolicy = NsfwPolicy.entries.getOrElse(prefs[NSFW] ?: 0) { NsfwPolicy.HIDE },
            apiToken = prefs[API_TOKEN].orEmpty(),
            spoilerLevel = prefs[SPOILER] ?: 0,
            userId = prefs[USER_ID].orEmpty(),
            username = prefs[USERNAME].orEmpty(),
            liquidGlassBar = prefs[LIQUID_GLASS] ?: false,
        )
    }

    val favorites: Flow<List<FavoriteItem>> = context.dataStore.data.map { prefs ->
        val raw = prefs[FAVORITES].orEmpty()
        if (raw.isBlank()) emptyList()
        else runCatching { json.decodeFromString<List<FavoriteItem>>(raw) }.getOrDefault(emptyList())
    }

    val history: Flow<List<FavoriteItem>> = context.dataStore.data.map { prefs ->
        val raw = prefs[HISTORY].orEmpty()
        if (raw.isBlank()) emptyList()
        else runCatching { json.decodeFromString<List<FavoriteItem>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun setColorMode(mode: ColorMode) {
        context.dataStore.edit { it[COLOR_MODE] = mode.ordinal }
    }

    suspend fun setTitlePreference(pref: TitlePreference) {
        context.dataStore.edit { it[TITLE_PREF] = pref.ordinal }
    }

    suspend fun setNsfwPolicy(policy: NsfwPolicy) {
        context.dataStore.edit { it[NSFW] = policy.ordinal }
    }

    suspend fun setSpoilerLevel(level: Int) {
        context.dataStore.edit { it[SPOILER] = level.coerceIn(0, 2) }
    }

    suspend fun setLiquidGlassBar(enabled: Boolean) {
        context.dataStore.edit { it[LIQUID_GLASS] = enabled }
    }

    suspend fun setApiToken(token: String) {
        context.dataStore.edit { it[API_TOKEN] = token.trim() }
    }

    suspend fun setAuthUser(id: String, name: String) {
        context.dataStore.edit {
            it[USER_ID] = id
            it[USERNAME] = name
        }
    }

    suspend fun clearAuth() {
        context.dataStore.edit {
            it[API_TOKEN] = ""
            it[USER_ID] = ""
            it[USERNAME] = ""
        }
    }

    suspend fun toggleFavorite(item: FavoriteItem): Boolean {
        var added = false
        context.dataStore.edit { prefs ->
            val current = prefs[FAVORITES]
                ?.let { runCatching { json.decodeFromString<List<FavoriteItem>>(it) }.getOrNull() }
                .orEmpty()
                .toMutableList()
            val index = current.indexOfFirst { it.id == item.id && it.type == item.type }
            if (index >= 0) {
                current.removeAt(index)
            } else {
                current.add(0, item)
                added = true
            }
            prefs[FAVORITES] = json.encodeToString(current)
        }
        return added
    }

    suspend fun isFavorite(id: String, type: String): Boolean {
        // used only after collecting flow; kept for completeness
        return false
    }

    suspend fun addHistory(item: FavoriteItem) {
        context.dataStore.edit { prefs ->
            val current = prefs[HISTORY]
                ?.let { runCatching { json.decodeFromString<List<FavoriteItem>>(it) }.getOrNull() }
                .orEmpty()
                .toMutableList()
            current.removeAll { it.id == item.id && it.type == item.type }
            current.add(0, item)
            prefs[HISTORY] = json.encodeToString(current.take(50))
        }
    }

    suspend fun removeHistory(item: FavoriteItem) {
        context.dataStore.edit { prefs ->
            val current = prefs[HISTORY]
                ?.let { runCatching { json.decodeFromString<List<FavoriteItem>>(it) }.getOrNull() }
                .orEmpty()
                .filterNot { it.id == item.id && it.type == item.type }
            prefs[HISTORY] = json.encodeToString(current)
        }
    }

    suspend fun clearHistory() {
        context.dataStore.edit { it[HISTORY] = json.encodeToString(emptyList<FavoriteItem>()) }
    }

    companion object {
        private val COLOR_MODE = intPreferencesKey("color_mode")
        private val TITLE_PREF = intPreferencesKey("title_pref")
        private val NSFW = intPreferencesKey("nsfw")
        private val API_TOKEN = stringPreferencesKey("api_token")
        private val SPOILER = intPreferencesKey("spoiler")
        private val USER_ID = stringPreferencesKey("user_id")
        private val USERNAME = stringPreferencesKey("username")
        private val FAVORITES = stringPreferencesKey("favorites")
        private val HISTORY = stringPreferencesKey("history")
        private val LIQUID_GLASS = booleanPreferencesKey("liquid_glass_bar")
    }
}
