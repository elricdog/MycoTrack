package com.mikotrack.maps

import android.content.Context
import com.mikotrack.ui.mushrooms.MushroomCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The result of the search of the hotspots of the current multi-species search. */
sealed interface HotspotState {
    /** There is no multi-species search or its hotspots were not asked for. */
    data object Idle : HotspotState

    /** The habitat of the circle is being scanned. */
    data object Searching : HotspotState

    /** The marks to draw on the map, best first. */
    data class Ready(val hotspots: List<Hotspot>) : HotspotState

    /** The scan finished but no place of the circle suits the selection well enough. */
    data object None : HotspotState

    /** The habitat sources could not be read. */
    data object Failed : HotspotState
}

/**
 * Runs the hotspot scan of the active multi-species search and keeps its state, so the
 * map can draw the marks whenever it is redrawn or rebuilt. [start] replaces any previous
 * scan; [clear] forgets it, and is called when the suitability overlay goes away.
 */
object HotspotSearches {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<HotspotState>(HotspotState.Idle)
    val state: StateFlow<HotspotState> = _state.asStateFlow()

    private var job: Job? = null

    /** The search being scanned, or the one the current result belongs to. */
    var search: SuitabilitySearch? = null
        private set

    /** Scans [search] for hotspots, replacing the state of any earlier scan. */
    fun start(context: Context, search: SuitabilitySearch, language: String) {
        job?.cancel()
        this.search = search
        _state.value = HotspotState.Searching
        val appContext = context.applicationContext
        job = scope.launch {
            try {
                val candidates = withContext(Dispatchers.IO) {
                    HotspotFinder.pick(
                        HotspotScanner.get(appContext).scan(search),
                        minSeparationMeters = (search.radiusMeters / 5.0).coerceIn(300.0, 5_000.0),
                    )
                }
                val names = runCatching {
                    MushroomCatalog.load(appContext, language).associate { it.id to it.commonName }
                }.getOrDefault(emptyMap())
                val hotspots = candidates.mapIndexed { index, candidate ->
                    Hotspot(
                        rank = index + 1,
                        latitude = candidate.latitude,
                        longitude = candidate.longitude,
                        score = candidate.score,
                        speciesNames = HotspotFinder.speciesAt(candidate).mapNotNull { (id, _) -> names[id] },
                    )
                }
                _state.value = if (hotspots.isEmpty()) HotspotState.None else HotspotState.Ready(hotspots)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = HotspotState.Failed
            }
        }
    }

    /** Forgets the scan and its result. */
    fun clear() {
        job?.cancel()
        job = null
        search = null
        _state.value = HotspotState.Idle
    }
}

/** A pending request to scan the hotspots of a new multi-species search. */
data class HotspotRequest(val search: SuitabilitySearch, val language: String)

/**
 * A pending request to scan the hotspots of a multi-species search, posted when the
 * search starts from the mushroom guide and consumed by the map screen, which shows the
 * marks.
 */
object HotspotRequests {
    private val _pending = MutableStateFlow<HotspotRequest?>(null)
    val pending: StateFlow<HotspotRequest?> = _pending.asStateFlow()

    fun post(request: HotspotRequest) {
        _pending.value = request
    }

    /** Clears the request, unless a newer one arrived meanwhile. */
    fun consume(request: HotspotRequest) {
        _pending.compareAndSet(request, null)
    }

    fun clear() {
        _pending.value = null
    }
}
