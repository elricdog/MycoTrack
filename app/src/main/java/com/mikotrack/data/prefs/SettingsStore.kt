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
        val DISMISSED_UPDATE_TAG = stringPreferencesKey("dismissed_update_tag")
    }

    /** Whether a parking waypoint is added at the first accurate fix of a new trip. */
    val autoParkingEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_PARKING] ?: true }

    suspend fun setAutoParkingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_PARKING] = enabled }
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

    /** The tag of the release the user declined, so silent checks skip it. */
    val dismissedUpdateTag: Flow<String> =
        context.dataStore.data.map { it[Keys.DISMISSED_UPDATE_TAG] ?: "" }

    suspend fun setDismissedUpdateTag(tag: String) {
        context.dataStore.edit { it[Keys.DISMISSED_UPDATE_TAG] = tag }
    }
}
