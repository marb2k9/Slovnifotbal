package cz.slovnifotbal.data

import cz.slovnifotbal.domain.GameResult
import cz.slovnifotbal.domain.GameSettings
import cz.slovnifotbal.domain.GameSnapshot
import cz.slovnifotbal.domain.GameStatistics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Deterministické úložiště v paměti pro testy ViewModelů (rozehraná hra se ukládá zakódovaná jako v DataStore). */
class InMemoryGameStorage(initial: GameSettings = GameSettings()) : GameStorage {
    private val settingsState = MutableStateFlow(initial)
    private val statisticsState = MutableStateFlow(GameStatistics())
    private var savedGame: String? = null

    override val settingsFlow: Flow<GameSettings> = settingsState
    override val statisticsFlow: Flow<GameStatistics> = statisticsState

    override suspend fun settings(): GameSettings = settingsState.value
    override suspend fun statistics(): GameStatistics = statisticsState.value

    override suspend fun updateSettings(transform: (GameSettings) -> GameSettings) {
        settingsState.update { current ->
            val updated = transform(current)
            updated.copy(rules = updated.rules.sanitized())
        }
    }

    override suspend fun resetSettings() {
        settingsState.value = GameSettings()
    }

    override suspend fun recordGame(result: GameResult) {
        statisticsState.update { it.record(result) }
    }

    override suspend fun resetStatistics() {
        statisticsState.value = GameStatistics()
    }

    override suspend fun saveGame(snapshot: GameSnapshot?) {
        savedGame = snapshot?.encode()
    }

    override suspend fun loadGame(): GameSnapshot? = GameSnapshot.decode(savedGame)
}
