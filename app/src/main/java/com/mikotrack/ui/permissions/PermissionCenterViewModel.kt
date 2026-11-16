package com.mikotrack.ui.permissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikotrack.data.prefs.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Drives the permission centre. */
class PermissionCenterViewModel(
    private val settingsStore: SettingsStore,
) : ViewModel() {

    /**
     * Identifiers of the permissions the user has permanently denied.
     *
     * Android refuses to show the system dialog again for those, so the screen
     * sends the user to the system settings instead.
     */
    val blockedPermissionIds: StateFlow<Set<String>> = settingsStore.blockedPermissionIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Records that a permission can no longer be requested with a system dialog. */
    fun setBlocked(id: String, blocked: Boolean) {
        viewModelScope.launch { settingsStore.setPermissionBlocked(id, blocked) }
    }
}
