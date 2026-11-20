package com.mikotrack.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mikotrack.domain.CheckInPolicy
import com.mikotrack.maps.SuitabilitySearch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mycotrack_settings")

/** Theme preference for the whole application. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The zoom level used when the user has not chosen another one. */
const val DEFAULT_ZOOM_LEVEL = 16f

/** The range of zoom levels the user can pick as the default one. */
const val MIN_ZOOM_LEVEL = 8f
const val MAX_ZOOM_LEVEL = 19f

/** The radius of the proximity alarm, in metres. */
const val DEFAULT_PROXIMITY_RADIUS = 30
const val MIN_PROXIMITY_RADIUS = 10
const val MAX_PROXIMITY_RADIUS = 200

/** All user configurable settings, persisted with Jetpack DataStore. */
class SettingsStore(private val context: Context) {

    private object Keys {
        val MAP_LAYER = stringPreferencesKey("map_layer")
        val GPS_INTERVAL = intPreferencesKey("gps_interval_ms")
        val MAX_ACCURACY = intPreferencesKey("max_accuracy_m")
        val MAX_JUMP_DISTANCE = intPreferencesKey("max_jump_distance_m")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val OVERLAY_TRIPS = stringPreferencesKey("overlay_trip_ids")
        val ACTIVE_TRIP_LAYER = longPreferencesKey("active_offline_area_id")
        val BLOCKED_PERMISSIONS = stringPreferencesKey("blocked_permissions")
        val DEFAULT_ZOOM = floatPreferencesKey("default_zoom")
        val SHOW_PHOTO_BUBBLES = booleanPreferencesKey("show_photo_bubbles")
        val HIDDEN_MAP_BUTTONS = stringPreferencesKey("hidden_map_buttons")
        val PROXIMITY_ALARM = booleanPreferencesKey("proximity_alarm")
        val PROXIMITY_RADIUS = intPreferencesKey("proximity_radius_m")
        val AUTO_PARKING = booleanPreferencesKey("auto_parking_waypoint")
        val CHECKIN_ENABLED = booleanPreferencesKey("checkin_enabled")
        val CHECKIN_MINUTES = intPreferencesKey("checkin_minutes")
        val CHECKIN_DUE = longPreferencesKey("checkin_due_ms")
        val PARKING_ICON_STYLE = stringPreferencesKey("parking_icon_style")
        val PARKING_ICON_COLOR = longPreferencesKey("parking_icon_color")
        val HABITAT_ASPECT = booleanPreferencesKey("habitat_aspect_layer")
        val HABITAT_TREES = booleanPreferencesKey("habitat_trees_layer")
        val HABITAT_SOIL = booleanPreferencesKey("habitat_soil_layer")
        val HABITAT_ASPECT_CLASSES = stringPreferencesKey("habitat_aspect_classes")
        val HABITAT_HIDDEN_TREES = stringPreferencesKey("habitat_hidden_trees")
        val HABITAT_HIDDEN_SOIL = stringPreferencesKey("habitat_hidden_soil")
        val HABITAT_RAIN = booleanPreferencesKey("habitat_rain_layer")
        val HABITAT_RAIN_WINDOW = intPreferencesKey("habitat_rain_window")
        val SUITABILITY_LAYER = booleanPreferencesKey("suitability_layer")
        val SUITABILITY_SPECIES = stringPreferencesKey("suitability_species")
        val SUITABILITY_SPECIES_NAME = stringPreferencesKey("suitability_species_name")
        val SUITABILITY_LATITUDE = doublePreferencesKey("suitability_latitude")
        val SUITABILITY_LONGITUDE = doublePreferencesKey("suitability_longitude")
        val SUITABILITY_RADIUS = intPreferencesKey("suitability_radius_m")
        val AUTO_UPDATE_CHECKS = booleanPreferencesKey("auto_update_checks")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check_ms")
        val GAMES_UNLOCKED = booleanPreferencesKey("games_unlocked")
        val GAME_SCORES_ARKANOID = stringPreferencesKey("game_scores_arkanoid")
        val GAME_SCORES_TETRIS = stringPreferencesKey("game_scores_tetris")
        val GAME_SCORES_PACMAN = stringPreferencesKey("game_scores_pacman")
        val GAME_SCORES_RHYTHM = stringPreferencesKey("game_scores_rhythm")
        val GAME_SCORES_MEMORY = stringPreferencesKey("game_scores_memory")
        val GAME_SCORES_TOPO = stringPreferencesKey("game_scores_topo")
        val GAME_SCORES_SOLITAIRE = stringPreferencesKey("game_scores_solitaire")
        val QUIZ_SCORES = stringPreferencesKey("quiz_scores")
        val QUIZ_PLAYER_NAME = stringPreferencesKey("quiz_player_name")
        val QUIZ_SEEN_KEYS = stringPreferencesKey("quiz_seen_keys")
        val QUIZ_VOICE_MODE = booleanPreferencesKey("quiz_voice_mode")
        val QUIZ_AUTO_ADVANCE = booleanPreferencesKey("quiz_auto_advance")
    }

    /** Whether a parking waypoint is added at the first accurate fix of a new trip. */
    val autoParkingEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_PARKING] ?: true }

    suspend fun setAutoParkingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_PARKING] = enabled }
    }

    /** Whether a recorded trip must ask every now and then that the user is ok. */
    val checkInEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.CHECKIN_ENABLED] ?: false }

    suspend fun setCheckInEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CHECKIN_ENABLED] = enabled }
    }

    /** How often the check-in asks, in minutes (one of [CheckInPolicy.CHOICES_MINUTES]). */
    val checkInMinutes: Flow<Int> = context.dataStore.data.map {
        it[Keys.CHECKIN_MINUTES] ?: CheckInPolicy.DEFAULT_MINUTES
    }

    suspend fun setCheckInMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.CHECKIN_MINUTES] = minutes }
    }

    /** When the running trip must ask whether the user is ok (0 when no check-in is armed). */
    val checkInDueAt: Flow<Long> = context.dataStore.data.map { it[Keys.CHECKIN_DUE] ?: 0L }

    suspend fun setCheckInDueAt(millis: Long) {
        context.dataStore.edit {
            if (millis <= 0L) it.remove(Keys.CHECKIN_DUE) else it[Keys.CHECKIN_DUE] = millis
        }
    }

    /** The icon drawn for parking waypoints on the map (see [ParkingIconStyles]). */
    val parkingIconStyle: Flow<String> = context.dataStore.data.map {
        it[Keys.PARKING_ICON_STYLE] ?: ParkingIconStyles.DEFAULT
    }

    suspend fun setParkingIconStyle(style: String) {
        context.dataStore.edit { it[Keys.PARKING_ICON_STYLE] = style }
    }

    /** The colour of the parking waypoint badge, as an ARGB Long. */
    val parkingIconColor: Flow<Long> = context.dataStore.data.map {
        it[Keys.PARKING_ICON_COLOR] ?: ParkingIconStyles.DEFAULT_COLOR
    }

    suspend fun setParkingIconColor(color: Long) {
        context.dataStore.edit { it[Keys.PARKING_ICON_COLOR] = color }
    }

    /** Whether an alarm sounds when the user reaches a waypoint of an overlaid trip. */
    val proximityAlarmEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.PROXIMITY_ALARM] ?: true }

    suspend fun setProximityAlarmEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PROXIMITY_ALARM] = enabled }
    }

    /** The distance to a waypoint, in metres, at which the proximity alarm sounds. */
    val proximityRadiusMeters: Flow<Int> = context.dataStore.data.map {
        (it[Keys.PROXIMITY_RADIUS] ?: DEFAULT_PROXIMITY_RADIUS).coerceIn(MIN_PROXIMITY_RADIUS, MAX_PROXIMITY_RADIUS)
    }

    suspend fun setProximityRadius(meters: Int) {
        context.dataStore.edit {
            it[Keys.PROXIMITY_RADIUS] = meters.coerceIn(MIN_PROXIMITY_RADIUS, MAX_PROXIMITY_RADIUS)
        }
    }

    /** Whether the photographs are shown as bubbles on the map. */
    val showPhotoBubbles: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHOW_PHOTO_BUBBLES] ?: true }

    suspend fun setShowPhotoBubbles(visible: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_PHOTO_BUBBLES] = visible }
    }

    /** The keys of the buttons the user removed from the map screen. */
    val hiddenMapButtons: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.HIDDEN_MAP_BUTTONS]
            ?.split(',')
            ?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            ?.toSet()
            .orEmpty()
    }

    suspend fun setMapButtonVisible(key: String, visible: Boolean) {
        context.dataStore.edit { prefs ->
            val hidden = prefs[Keys.HIDDEN_MAP_BUTTONS]
                ?.split(',')
                ?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                ?.toMutableSet()
                ?: mutableSetOf()
            if (visible) hidden -= key else hidden += key
            prefs[Keys.HIDDEN_MAP_BUTTONS] = hidden.joinToString(",")
        }
    }

    /** The zoom level the camera uses by default, and returns to on request. */
    val defaultZoom: Flow<Float> = context.dataStore.data.map {
        (it[Keys.DEFAULT_ZOOM] ?: DEFAULT_ZOOM_LEVEL).coerceIn(MIN_ZOOM_LEVEL, MAX_ZOOM_LEVEL)
    }

    suspend fun setDefaultZoom(zoom: Float) {
        context.dataStore.edit { it[Keys.DEFAULT_ZOOM] = zoom.coerceIn(MIN_ZOOM_LEVEL, MAX_ZOOM_LEVEL) }
    }

    /** Identifier of the selected base map layer. */
    val selectedLayerId: Flow<String> = context.dataStore.data.map { it[Keys.MAP_LAYER] ?: "OSM_STREET" }

    /** Identifier of the offline area that is used as the base map, if any. */
    val activeOfflineLayerId: Flow<Long?> = context.dataStore.data.map { it[Keys.ACTIVE_TRIP_LAYER] }

    /** Requested GPS update interval in milliseconds. */
    val gpsIntervalMillis: Flow<Int> = context.dataStore.data.map { it[Keys.GPS_INTERVAL] ?: 2_000 }

    /** Maximum accepted horizontal accuracy, in metres. */
    val maxAccuracyMeters: Flow<Int> = context.dataStore.data.map { it[Keys.MAX_ACCURACY] ?: 30 }

    /** Maximum accepted jump between two consecutive fixes, in metres. */
    val maxJumpMeters: Flow<Int> = context.dataStore.data.map { it[Keys.MAX_JUMP_DISTANCE] ?: 100 }

    /** Theme mode. */
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map {
        runCatching { ThemeMode.valueOf(it[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    /** Whether the screen should stay on while recording. */
    val keepScreenOn: Flow<Boolean> = context.dataStore.data.map { it[Keys.KEEP_SCREEN_ON] ?: true }

    /** Comma separated list of trip identifiers overlaid on the map. */
    val overlayTripIds: Flow<List<Long>> = context.dataStore.data.map { prefs ->
        prefs[Keys.OVERLAY_TRIPS]
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            .orEmpty()
    }

    /**
     * Identifiers of the permissions the user has permanently denied.
     *
     * Android stops showing the system dialog after a permanent refusal, so the
     * application has to remember the state to be able to send the user to the
     * system settings instead of asking again.
     */
    val blockedPermissionIds: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.BLOCKED_PERMISSIONS]
            ?.split(',')
            ?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            ?.toSet()
            .orEmpty()
    }

    /** Records that a permission can no longer be requested with a system dialog. */
    suspend fun setPermissionBlocked(id: String, blocked: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.BLOCKED_PERMISSIONS]
                ?.split(',')
                ?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                ?.toMutableSet()
                ?: mutableSetOf()
            if (blocked) current += id else current -= id
            prefs[Keys.BLOCKED_PERMISSIONS] = current.joinToString(",")
        }
    }

    suspend fun setSelectedLayer(id: String) {
        context.dataStore.edit { it[Keys.MAP_LAYER] = id }
    }

    suspend fun setActiveOfflineLayer(id: Long?) {
        context.dataStore.edit {
            if (id == null) it.remove(Keys.ACTIVE_TRIP_LAYER) else it[Keys.ACTIVE_TRIP_LAYER] = id
        }
    }

    suspend fun setGpsInterval(millis: Int) {
        context.dataStore.edit { it[Keys.GPS_INTERVAL] = millis.coerceIn(500, 60_000) }
    }

    suspend fun setMaxAccuracy(meters: Int) {
        context.dataStore.edit { it[Keys.MAX_ACCURACY] = meters.coerceIn(1, 500) }
    }

    suspend fun setMaxJumpDistance(meters: Int) {
        context.dataStore.edit { it[Keys.MAX_JUMP_DISTANCE] = meters.coerceIn(5, 5_000) }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { it[Keys.KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setOverlayTripIds(ids: List<Long>) {
        context.dataStore.edit {
            it[Keys.OVERLAY_TRIPS] = ids.joinToString(",")
        }
    }

    /** Whether the orientation (north/south slopes) habitat overlay is shown on the map. */
    val habitatAspectLayer: Flow<Boolean> = context.dataStore.data.map { it[Keys.HABITAT_ASPECT] ?: false }

    suspend fun setHabitatAspectLayer(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HABITAT_ASPECT] = enabled }
    }

    /** Whether the tree species habitat overlay is shown on the map. */
    val habitatTreesLayer: Flow<Boolean> = context.dataStore.data.map { it[Keys.HABITAT_TREES] ?: false }

    suspend fun setHabitatTreesLayer(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HABITAT_TREES] = enabled }
    }

    /** Whether the soil (acid or basic substrate) habitat overlay is shown on the map. */
    val habitatSoilLayer: Flow<Boolean> = context.dataStore.data.map { it[Keys.HABITAT_SOIL] ?: false }

    suspend fun setHabitatSoilLayer(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HABITAT_SOIL] = enabled }
    }

    /**
     * The code of the orientation classes drawn by the habitat overlay (see
     * `AspectHatch.encode`), or `null` while the user has not picked any.
     */
    val habitatAspectClasses: Flow<String?> = context.dataStore.data.map { it[Keys.HABITAT_ASPECT_CLASSES] }

    suspend fun setHabitatAspectClasses(code: String) {
        context.dataStore.edit { it[Keys.HABITAT_ASPECT_CLASSES] = code }
    }

    /** The keys of the tree formations hidden in the habitat overlay; new ones show up. */
    val habitatHiddenTrees: Flow<Set<String>> = context.dataStore.data.map { decodeKeys(it[Keys.HABITAT_HIDDEN_TREES]) }

    suspend fun setHabitatHiddenTrees(keys: Set<String>) {
        context.dataStore.edit { it[Keys.HABITAT_HIDDEN_TREES] = keys.sorted().joinToString(",") }
    }

    /** The keys of the lithologies hidden in the soil overlay; new ones show up. */
    val habitatHiddenSoil: Flow<Set<String>> = context.dataStore.data.map { decodeKeys(it[Keys.HABITAT_HIDDEN_SOIL]) }

    suspend fun setHabitatHiddenSoil(keys: Set<String>) {
        context.dataStore.edit { it[Keys.HABITAT_HIDDEN_SOIL] = keys.sorted().joinToString(",") }
    }

    /** Whether the rainfall habitat overlay is shown on the map. */
    val habitatRainLayer: Flow<Boolean> = context.dataStore.data.map { it[Keys.HABITAT_RAIN] ?: false }

    suspend fun setHabitatRainLayer(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HABITAT_RAIN] = enabled }
    }

    /** The length of the rainfall accumulation window, in days (3, 7, 15 or 30). */
    val habitatRainWindow: Flow<Int> = context.dataStore.data.map { it[Keys.HABITAT_RAIN_WINDOW] ?: 7 }

    suspend fun setHabitatRainWindow(days: Int) {
        context.dataStore.edit { it[Keys.HABITAT_RAIN_WINDOW] = days }
    }

    private fun decodeKeys(value: String?): Set<String> =
        value?.split(',')?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }?.toSet().orEmpty()

    /** Whether the likely areas of [suitabilitySearch] are shown on the map. */
    val suitabilityLayer: Flow<Boolean> = context.dataStore.data.map { it[Keys.SUITABILITY_LAYER] ?: false }

    suspend fun setSuitabilityLayer(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SUITABILITY_LAYER] = enabled }
    }

    /** The last search of likely areas started from the mushroom guide, if any. */
    val suitabilitySearch: Flow<SuitabilitySearch?> = context.dataStore.data.map { prefs ->
        val id = prefs[Keys.SUITABILITY_SPECIES] ?: return@map null
        val latitude = prefs[Keys.SUITABILITY_LATITUDE] ?: return@map null
        val longitude = prefs[Keys.SUITABILITY_LONGITUDE] ?: return@map null
        SuitabilitySearch(
            speciesId = id,
            speciesName = prefs[Keys.SUITABILITY_SPECIES_NAME] ?: id,
            latitude = latitude,
            longitude = longitude,
            radiusMeters = (prefs[Keys.SUITABILITY_RADIUS] ?: SuitabilitySearch.DEFAULT_RADIUS_KM * 1000)
                .coerceIn(1, SuitabilitySearch.MAX_RADIUS_METERS),
        )
    }

    /** Stores a new search of likely areas and shows its layer. */
    suspend fun startSuitabilitySearch(search: SuitabilitySearch) {
        context.dataStore.edit {
            it[Keys.SUITABILITY_SPECIES] = search.speciesId
            it[Keys.SUITABILITY_SPECIES_NAME] = search.speciesName
            it[Keys.SUITABILITY_LATITUDE] = search.latitude
            it[Keys.SUITABILITY_LONGITUDE] = search.longitude
            it[Keys.SUITABILITY_RADIUS] = search.radiusMeters.coerceIn(1, SuitabilitySearch.MAX_RADIUS_METERS)
            it[Keys.SUITABILITY_LAYER] = true
        }
    }

    /** Forgets the search of likely areas and hides its layer. */
    suspend fun clearSuitabilitySearch() {
        context.dataStore.edit {
            it.remove(Keys.SUITABILITY_SPECIES)
            it.remove(Keys.SUITABILITY_SPECIES_NAME)
            it.remove(Keys.SUITABILITY_LATITUDE)
            it.remove(Keys.SUITABILITY_LONGITUDE)
            it.remove(Keys.SUITABILITY_RADIUS)
            it[Keys.SUITABILITY_LAYER] = false
        }
    }

    /** Whether the application checks for new releases on start (once a day). */
    val autoUpdateChecks: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.AUTO_UPDATE_CHECKS] ?: true }

    suspend fun setAutoUpdateChecks(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_UPDATE_CHECKS] = enabled }
    }

    /** The time of the last update check, in epoch milliseconds (0 when never). */
    val lastUpdateCheck: Flow<Long> =
        context.dataStore.data.map { it[Keys.LAST_UPDATE_CHECK] ?: 0L }

    suspend fun setLastUpdateCheck(millis: Long) {
        context.dataStore.edit { it[Keys.LAST_UPDATE_CHECK] = millis }
    }

    /** Whether the easter egg of the games has been unlocked (5 taps on the banner). */
    val gamesUnlocked: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.GAMES_UNLOCKED] ?: false }

    suspend fun setGamesUnlocked(unlocked: Boolean) {
        context.dataStore.edit { it[Keys.GAMES_UNLOCKED] = unlocked }
    }

    /** The best scores of one of the games, most points first. */
    fun gameScores(game: GameId): Flow<List<GameScore>> =
        context.dataStore.data.map { GameScores.decode(it[gameKey(game)]) }

    /** Adds a finished game and keeps the best [GameScores.MAX_RANK] scores. */
    suspend fun recordGameScore(game: GameId, score: Int, level: Int, atMillis: Long) {
        context.dataStore.edit {
            it[gameKey(game)] = GameScores.insert(it[gameKey(game)], GameScore(score, level, atMillis))
        }
    }

    private fun gameKey(game: GameId) = when (game) {
        GameId.ARKANOID -> Keys.GAME_SCORES_ARKANOID
        GameId.TETRIS -> Keys.GAME_SCORES_TETRIS
        GameId.PACMAN -> Keys.GAME_SCORES_PACMAN
        GameId.RHYTHM -> Keys.GAME_SCORES_RHYTHM
        GameId.MEMORY -> Keys.GAME_SCORES_MEMORY
        GameId.TOPO -> Keys.GAME_SCORES_TOPO
        GameId.SOLITAIRE -> Keys.GAME_SCORES_SOLITAIRE
    }

    /** The quiz leaderboard, best score first. */
    val quizScores: Flow<List<QuizScore>> =
        context.dataStore.data.map { QuizScores.decode(it[Keys.QUIZ_SCORES]) }

    /** Adds a finished quiz run and keeps the best entries on the leaderboard. */
    suspend fun recordQuizScore(name: String, score: Int, atMillis: Long) {
        context.dataStore.edit {
            it[Keys.QUIZ_SCORES] = QuizScores.insert(it[Keys.QUIZ_SCORES], QuizScore(name, score, atMillis))
        }
    }

    /** The last player name used, offered as the default for the next run. */
    val quizPlayerName: Flow<String> =
        context.dataStore.data.map { it[Keys.QUIZ_PLAYER_NAME] ?: "" }

    suspend fun setQuizPlayerName(name: String) {
        context.dataStore.edit { it[Keys.QUIZ_PLAYER_NAME] = name }
    }

    /** Question keys already asked, so recent questions do not repeat. */
    val quizSeenKeys: Flow<List<String>> =
        context.dataStore.data.map { it[Keys.QUIZ_SEEN_KEYS]?.split(';')?.filter { key -> key.isNotBlank() } ?: emptyList() }

    suspend fun setQuizSeenKeys(keys: List<String>) {
        context.dataStore.edit { it[Keys.QUIZ_SEEN_KEYS] = keys.joinToString(";") }
    }

    /** Whether the quiz reads questions aloud and accepts spoken answers. */
    val quizVoiceMode: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.QUIZ_VOICE_MODE] ?: false }

    suspend fun setQuizVoiceMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.QUIZ_VOICE_MODE] = enabled }
    }

    /** Whether the quiz taps "next" by itself a few seconds per question. */
    val quizAutoAdvance: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.QUIZ_AUTO_ADVANCE] ?: false }

    suspend fun setQuizAutoAdvance(enabled: Boolean) {
        context.dataStore.edit { it[Keys.QUIZ_AUTO_ADVANCE] = enabled }
    }
}
