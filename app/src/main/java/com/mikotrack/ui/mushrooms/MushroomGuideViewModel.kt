package com.mikotrack.ui.mushrooms

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/** Loads the mushroom guide and filters it for the list screen. */
class MushroomGuideViewModel(application: Application) : AndroidViewModel(application) {

    private val _species = MutableStateFlow<List<Mushroom>?>(null)

    /** All the species in the current language, or null while they are loading. */
    val species: StateFlow<List<Mushroom>?> = _species.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _onlyInSeason = MutableStateFlow(false)
    val onlyInSeason: StateFlow<Boolean> = _onlyInSeason.asStateFlow()

    private val _selected = MutableStateFlow<Set<String>>(emptySet())

    /** The species picked with a long press to look for them all at once. */
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    /** Adds or removes a species from the multi-species selection. */
    fun toggleSelected(id: String) {
        _selected.update { if (id in it) it - id else it + id }
    }

    /** Empties the multi-species selection. */
    fun clearSelected() {
        _selected.value = emptySet()
    }

    /** The current month, from 1 to 12. */
    val currentMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1

    val visibleSpecies: StateFlow<List<Mushroom>> =
        combine(_species, _query, _onlyInSeason) { all, query, inSeason ->
            all.orEmpty().filter { it.matches(query) && (!inSeason || it.isInSeason(currentMonth)) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var loadedLanguage: String? = null

    /** Loads the guide in [language]; it does nothing if that language is already loaded. */
    fun load(language: String) {
        if (language == loadedLanguage) return
        loadedLanguage = language
        viewModelScope.launch {
            _species.value = withContext(Dispatchers.IO) {
                MushroomCatalog.load(getApplication(), language)
            }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setOnlyInSeason(value: Boolean) {
        _onlyInSeason.value = value
    }
}
