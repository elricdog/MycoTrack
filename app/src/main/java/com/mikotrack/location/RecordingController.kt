package com.mikotrack.location

import com.mikotrack.domain.model.TripStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The current position reported by the GPS. */
data class CurrentPosition(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val accuracy: Float?,
    val speed: Float?,
    val bearing: Float?,
    val timestamp: Long,
)

/** Everything the UI needs to know about the running recording. */
data class RecordingState(
    val isRecording: Boolean = false,
    val tripId: Long? = null,
    val tripName: String = "",
    val startedAt: Long = 0,
    val position: CurrentPosition? = null,
    val stats: TripStats = TripStats(),
    val isGpsEnabled: Boolean = true,
)

/**
 * Bridge between the foreground service that owns the GPS and the user interface.
 *
 * Both live in the same process, so a small observable singleton is the simplest and most
 * reliable way to share the recording state.
 */
object RecordingController {

    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    /** Called by the service when the recording starts. */
    fun onRecordingStarted(tripId: Long, tripName: String, startedAt: Long) {
        _state.update {
            it.copy(
                isRecording = true,
                tripId = tripId,
                tripName = tripName,
                startedAt = startedAt,
                stats = TripStats(),
            )
        }
    }

    /** Called by the service for every accepted position. */
    fun onPosition(position: CurrentPosition) {
        _state.update { it.copy(position = position) }
    }

    /** Called by the service whenever the statistics changed. */
    fun onStats(stats: TripStats) {
        _state.update { it.copy(stats = stats) }
    }

    /** Called by the service when the GPS providers change state. */
    fun onGpsAvailability(enabled: Boolean) {
        _state.update { it.copy(isGpsEnabled = enabled) }
    }

    /**
     * Called by the service when the recording stops.
     *
     * Nothing of the finished trip is kept: its last position would freeze the marker of
     * the map where the trip ended, and its route is available in the history.
     */
    fun onRecordingStopped() {
        _state.update { RecordingState(isGpsEnabled = it.isGpsEnabled) }
    }

    /** Resets the shared state, used when the application starts. */
    fun reset() {
        _state.value = RecordingState()
    }
}
