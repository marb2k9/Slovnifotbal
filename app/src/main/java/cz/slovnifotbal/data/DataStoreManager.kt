package cz.slovnifotbal.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import cz.slovnifotbal.domain.ChRule
import cz.slovnifotbal.domain.Difficulty
import cz.slovnifotbal.domain.GameResult
import cz.slovnifotbal.domain.GameRules
import cz.slovnifotbal.domain.GameSettings
import cz.slovnifotbal.domain.GameSnapshot
import cz.slovnifotbal.domain.GameStatistics
import cz.slovnifotbal.domain.LinkRule
import cz.slovnifotbal.domain.PartOfSpeech
import cz.slovnifotbal.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Lokální úložiště nastavení, statistik a rozehrané hry (Preferences DataStore).
 * Vše zůstává v zařízení – aplikace nepoužívá žádnou síť.
 */
class DataStoreManager(private val dataStore: DataStore<Preferences>) {

    private val preferences: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val settingsFlow: Flow<GameSettings> = preferences.map { it.toSettings() }.distinctUntilChanged()

    val statisticsFlow: Flow<GameStatistics> = preferences.map { it.toStatistics() }.distinctUntilChanged()

    suspend fun settings(): GameSettings = settingsFlow.first()

    suspend fun statistics(): GameStatistics = statisticsFlow.first()

    suspend fun updateSettings(transform: (GameSettings) -> GameSettings) {
        dataStore.edit { prefs ->
            val updated = transform(prefs.toSettings())
            val r = updated.rules.sanitized()
            prefs[DIFFICULTY] = r.difficulty.key
            prefs[PROPER_NAMES] = r.properNames
            prefs[MIN_LENGTH] = r.minWordLength
            prefs[CH_RULE] = r.chRule.key
            prefs[LINK_RULE] = r.linkRule.key
            prefs[POS_MASK] = r.partsOfSpeechMask
            prefs[INFLECTED_FORMS] = r.inflectedForms
            prefs[SOUND] = updated.soundEffects
            prefs[THEME] = updated.themeMode.key
        }
    }

    suspend fun resetSettings() {
        dataStore.edit { prefs ->
            for (key in SETTINGS_KEYS) prefs.remove(key)
        }
    }

    /** Započte dokončenou hru do statistik (atomicky). */
    suspend fun recordGame(result: GameResult) {
        dataStore.edit { prefs ->
            val s = prefs.toStatistics().record(result)
            prefs[GAMES] = s.gamesPlayed
            prefs[WINS] = s.wins
            prefs[LOSSES] = s.losses
            prefs[LONGEST_CHAIN] = s.longestChain
            prefs[MOST_PLAYER_WORDS] = s.mostPlayerWordsInGame
            prefs[TOTAL_WORDS] = s.totalWords
            prefs[TOTAL_PLAYER_WORDS] = s.totalPlayerWords
            prefs[STREAK] = s.currentWinStreak
            prefs[BEST_STREAK] = s.bestWinStreak
            for (d in Difficulty.entries) {
                prefs[gamesKey(d)] = s.gamesOn(d)
                prefs[winsKey(d)] = s.winsOn(d)
            }
        }
    }

    suspend fun resetStatistics() {
        dataStore.edit { prefs ->
            for (key in STAT_KEYS) prefs.remove(key)
        }
    }

    /** Uloží rozehranou hru (null = žádná rozehraná hra). */
    suspend fun saveGame(snapshot: GameSnapshot?) {
        dataStore.edit { prefs ->
            if (snapshot == null) prefs.remove(SAVED_GAME) else prefs[SAVED_GAME] = snapshot.encode()
        }
    }

    suspend fun loadGame(): GameSnapshot? = GameSnapshot.decode(preferences.first()[SAVED_GAME])

    private fun Preferences.toSettings(): GameSettings {
        val rules = GameRules(
            difficulty = Difficulty.fromKey(this[DIFFICULTY]),
            properNames = this[PROPER_NAMES] ?: false,
            minWordLength = this[MIN_LENGTH] ?: GameRules.DEFAULT_MIN_LENGTH,
            chRule = ChRule.fromKey(this[CH_RULE]),
            linkRule = LinkRule.fromKey(this[LINK_RULE]),
            partsOfSpeech = PartOfSpeech.fromMask(this[POS_MASK] ?: PartOfSpeech.maskOf(PartOfSpeech.ALL)),
            inflectedForms = this[INFLECTED_FORMS] ?: false,
        ).sanitized()
        return GameSettings(
            rules = rules,
            soundEffects = this[SOUND] ?: true,
            themeMode = ThemeMode.fromKey(this[THEME]),
        )
    }

    private fun Preferences.toStatistics(): GameStatistics = GameStatistics(
        gamesPlayed = this[GAMES] ?: 0,
        wins = this[WINS] ?: 0,
        losses = this[LOSSES] ?: 0,
        longestChain = this[LONGEST_CHAIN] ?: 0,
        mostPlayerWordsInGame = this[MOST_PLAYER_WORDS] ?: 0,
        totalWords = this[TOTAL_WORDS] ?: 0,
        totalPlayerWords = this[TOTAL_PLAYER_WORDS] ?: 0,
        currentWinStreak = this[STREAK] ?: 0,
        bestWinStreak = this[BEST_STREAK] ?: 0,
        gamesByDifficulty = Difficulty.entries.associateWith { this[gamesKey(it)] ?: 0 }.filterValues { it > 0 },
        winsByDifficulty = Difficulty.entries.associateWith { this[winsKey(it)] ?: 0 }.filterValues { it > 0 },
    )

    companion object {
        const val FILE_NAME = "slovni_fotbal"

        private val DIFFICULTY = stringPreferencesKey("difficulty")
        private val PROPER_NAMES = booleanPreferencesKey("proper_names")
        private val MIN_LENGTH = intPreferencesKey("min_word_length")
        private val CH_RULE = stringPreferencesKey("ch_rule")
        private val LINK_RULE = stringPreferencesKey("link_rule")
        private val POS_MASK = intPreferencesKey("parts_of_speech")
        private val INFLECTED_FORMS = booleanPreferencesKey("inflected_forms")
        private val SOUND = booleanPreferencesKey("sound_effects")
        private val THEME = stringPreferencesKey("theme")
        private val SETTINGS_KEYS = listOf(DIFFICULTY, PROPER_NAMES, MIN_LENGTH, CH_RULE, LINK_RULE, POS_MASK, INFLECTED_FORMS, SOUND, THEME)

        private val GAMES = intPreferencesKey("stats_games")
        private val WINS = intPreferencesKey("stats_wins")
        private val LOSSES = intPreferencesKey("stats_losses")
        private val LONGEST_CHAIN = intPreferencesKey("stats_longest_chain")
        private val MOST_PLAYER_WORDS = intPreferencesKey("stats_most_player_words")
        private val TOTAL_WORDS = intPreferencesKey("stats_total_words")
        private val TOTAL_PLAYER_WORDS = intPreferencesKey("stats_total_player_words")
        private val STREAK = intPreferencesKey("stats_streak")
        private val BEST_STREAK = intPreferencesKey("stats_best_streak")
        private fun gamesKey(d: Difficulty) = intPreferencesKey("stats_games_${d.key}")
        private fun winsKey(d: Difficulty) = intPreferencesKey("stats_wins_${d.key}")
        private val STAT_KEYS = listOf(GAMES, WINS, LOSSES, LONGEST_CHAIN, MOST_PLAYER_WORDS, TOTAL_WORDS, TOTAL_PLAYER_WORDS, STREAK, BEST_STREAK) +
            Difficulty.entries.flatMap { listOf(gamesKey(it), winsKey(it)) }

        private val SAVED_GAME = stringPreferencesKey("saved_game")

        @Volatile
        private var instance: DataStoreManager? = null

        /** Jediná instance pro celou aplikaci (DataStore nesmí mít pro jeden soubor více instancí). */
        fun getInstance(context: Context): DataStoreManager =
            instance ?: synchronized(this) {
                instance ?: DataStoreManager(
                    PreferenceDataStoreFactory.create(
                        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                        produceFile = { context.applicationContext.preferencesDataStoreFile(FILE_NAME) },
                    ),
                ).also { instance = it }
            }
    }
}
