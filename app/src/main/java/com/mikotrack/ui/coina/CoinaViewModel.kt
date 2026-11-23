package com.mikotrack.ui.coina

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale

/** Loads the recipe bank and filters it for the list screen. */
class CoinaViewModel(application: Application) : AndroidViewModel(application) {

    private val _recipes = MutableStateFlow<List<Recipe>?>(null)

    /** All the recipes, or null while they are loading. */
    val recipes: StateFlow<List<Recipe>?> = _recipes.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    fun setQuery(value: String) {
        _query.value = value
    }

    val visibleRecipes: StateFlow<List<Recipe>> =
        kotlinx.coroutines.flow.combine(_recipes, _query) { all, query ->
            all.orEmpty().filter { it.matches(query) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var loaded = false

    /** Loads the bank in [language]; it does nothing if it is already loaded. */
    fun load(language: String) {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            _recipes.value = withContext(Dispatchers.IO) {
                CoinaCatalog.load(getApplication(), language)
            }
        }
    }

    /** Finds a loaded recipe by id, for the detail screen. */
    fun recipeById(id: String): Recipe? = _recipes.value?.firstOrNull { it.id == id }

    /** Suspends until the bank is loaded and returns the recipe [id]. */
    suspend fun awaitRecipe(id: String): Recipe? {
        if (_recipes.value == null) {
            load(Locale.getDefault().language)
        }
        return _recipes.value?.firstOrNull { it.id == id }
    }
}

/** Accent and case insensitive match on name, ingredients and history. */
private fun Recipe.matches(query: String): Boolean {
    val needle = query.normalizeForSearch()
    if (needle.isBlank()) return true
    val haystack = buildString {
        append(name.normalizeForSearch())
        append(' ')
        ingredients.forEach { append(it.normalizeForSearch()).append(' ') }
        append(history.normalizeForSearch())
    }
    return haystack.contains(needle)
}

private fun String.normalizeForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .trim()
