package com.mikotrack.ui.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikotrack.data.prefs.GameId
import com.mikotrack.data.prefs.GameScore
import com.mikotrack.data.prefs.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Shared view model of the easter egg games: rankings and score submission. */
class GamesViewModel(private val settingsStore: SettingsStore) : ViewModel() {

    /** The best scores of every game, keyed by game. */
    val scores: StateFlow<Map<GameId, List<GameScore>>> =
        kotlinx.coroutines.flow.combine(
            settingsStore.gameScores(GameId.ARKANOID),
            settingsStore.gameScores(GameId.TETRIS),
            settingsStore.gameScores(GameId.PACMAN),
            settingsStore.gameScores(GameId.RHYTHM),
            settingsStore.gameScores(GameId.MEMORY),
            settingsStore.gameScores(GameId.TOPO),
            settingsStore.gameScores(GameId.SOLITAIRE),
        ) { lists ->
            GameId.entries.zip(lists).toMap()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** The game the ranking of the current screen belongs to. */
    private val _currentGame = MutableStateFlow(GameId.ARKANOID)
    val currentGame: StateFlow<GameId> = _currentGame.asStateFlow()

    fun selectGame(game: GameId) {
        _currentGame.value = game
    }

    /** Records a finished game and refreshes the ranking of the screen. */
    fun recordScore(game: GameId, score: Int, level: Int) {
        viewModelScope.launch {
            settingsStore.recordGameScore(game, score, level, System.currentTimeMillis())
        }
    }

    /** The ranking of the game that is currently selected. */
    val currentRanking = kotlinx.coroutines.flow.combine(scores, currentGame) { all, game ->
        all[game].orEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
