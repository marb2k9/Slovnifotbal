package cz.slovnifotbal.data

import cz.slovnifotbal.domain.GameResult
import cz.slovnifotbal.domain.GameSettings
import cz.slovnifotbal.domain.GameSnapshot
import cz.slovnifotbal.domain.GameStatistics
import kotlinx.coroutines.flow.Flow

/**
 * Úložiště nastavení, statistik a rozehrané hry.
 * V aplikaci ho implementuje [DataStoreManager]; ViewModely závisí jen na tomto rozhraní,
 * takže je lze testovat s deterministickým úložištěm v paměti.
 */
interface GameStorage {
    val settingsFlow: Flow<GameSettings>
    val statisticsFlow: Flow<GameStatistics>

    suspend fun settings(): GameSettings
    suspend fun statistics(): GameStatistics
    suspend fun updateSettings(transform: (GameSettings) -> GameSettings)
    suspend fun resetSettings()
    suspend fun recordGame(result: GameResult)
    suspend fun resetStatistics()

    /** Uloží rozehranou hru (null = žádná rozehraná hra). */
    suspend fun saveGame(snapshot: GameSnapshot?)
    suspend fun loadGame(): GameSnapshot?
}
