package cz.slovnifotbal.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import cz.slovnifotbal.domain.ChRule
import cz.slovnifotbal.domain.Difficulty
import cz.slovnifotbal.domain.GamePhase
import cz.slovnifotbal.domain.GameResult
import cz.slovnifotbal.domain.GameRules
import cz.slovnifotbal.domain.GameSettings
import cz.slovnifotbal.domain.GameSnapshot
import cz.slovnifotbal.domain.LinkRule
import cz.slovnifotbal.domain.Move
import cz.slovnifotbal.domain.PartOfSpeech
import cz.slovnifotbal.domain.Player
import cz.slovnifotbal.domain.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Testy 17 a 18: ukládání statistik a nastavení (DataStore). */
class DataStoreManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.store(name: String = "test"): DataStoreManager = DataStoreManager(
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "$name.preferences_pb") }),
    )

    private val customSettings = GameSettings(
        rules = GameRules(
            difficulty = Difficulty.HARD,
            properNames = true,
            minWordLength = 4,
            chRule = ChRule.SINGLE_LETTER,
            linkRule = LinkRule.STRICT,
            partsOfSpeech = setOf(PartOfSpeech.NOUN, PartOfSpeech.VERB),
            inflectedForms = true,
        ),
        soundEffects = false,
        themeMode = ThemeMode.DARK,
    )

    @Test
    fun defaultsWhenNothingIsStored() = runTest {
        val s = store()
        assertEquals(GameSettings(), s.settings())
        assertEquals(0, s.statistics().gamesPlayed)
        assertNull(s.loadGame())
    }

    @Test
    fun settingsAreSaved() = runTest {
        val s = store()
        s.updateSettings { customSettings }
        assertEquals(customSettings, s.settings())
        s.resetSettings()
        assertEquals(GameSettings(), s.settings())
    }

    @Test
    fun settingsAndStatisticsSurviveRestart() = runTest {
        val file = File(tmp.root, "persist.preferences_pb")
        val job1 = Job()
        val first = DataStoreManager(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(StandardTestDispatcher(testScheduler) + job1), produceFile = { file }),
        )
        first.updateSettings { customSettings }
        first.recordGame(GameResult(won = true, difficulty = Difficulty.HARD, totalWords = 12, playerWords = 6))
        job1.cancelAndJoin()

        val job2 = Job()
        val second = DataStoreManager(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(StandardTestDispatcher(testScheduler) + job2), produceFile = { file }),
        )
        assertEquals(customSettings, second.settings())
        val stats = second.statistics()
        assertEquals(1, stats.gamesPlayed)
        assertEquals(1, stats.wins)
        assertEquals(12, stats.longestChain)
        assertEquals(1, stats.gamesOn(Difficulty.HARD))
        job2.cancelAndJoin()
    }

    @Test
    fun statisticsAreRecordedAndReset() = runTest {
        val s = store()
        s.recordGame(GameResult(won = true, difficulty = Difficulty.EASY, totalWords = 10, playerWords = 5))
        s.recordGame(GameResult(won = false, difficulty = Difficulty.MEDIUM, totalWords = 4, playerWords = 2))
        val stats = s.statistics()
        assertEquals(2, stats.gamesPlayed)
        assertEquals(1, stats.wins)
        assertEquals(1, stats.losses)
        assertEquals(50, stats.winRatePercent)
        assertEquals(10, stats.longestChain)
        assertEquals(5, stats.mostPlayerWordsInGame)
        assertEquals(14, stats.totalWords)
        s.resetStatistics()
        assertEquals(0, s.statistics().gamesPlayed)
    }

    @Test
    fun savedGameRoundTrip() = runTest {
        val s = store()
        val snapshot = GameSnapshot(
            rules = customSettings.rules,
            moves = listOf(Move(Player.HUMAN, "kolo"), Move(Player.COMPUTER, "okno")),
            phase = GamePhase.PLAYER_TURN,
            startedAtMillis = 99L,
        )
        s.saveGame(snapshot)
        assertEquals(snapshot, s.loadGame())
        s.saveGame(null)
        assertNull(s.loadGame())
    }
}
