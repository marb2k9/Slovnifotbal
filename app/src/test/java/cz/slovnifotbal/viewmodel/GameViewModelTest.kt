package cz.slovnifotbal.viewmodel

import cz.slovnifotbal.data.DictionaryRepository
import cz.slovnifotbal.data.DictionarySource
import cz.slovnifotbal.data.GameStorage
import cz.slovnifotbal.data.InMemoryGameStorage
import cz.slovnifotbal.domain.EndReason
import cz.slovnifotbal.domain.GamePhase
import cz.slovnifotbal.domain.Player
import cz.slovnifotbal.engine.WordDictionary
import cz.slovnifotbal.engine.WordMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.FileNotFoundException
import kotlin.random.Random

/** Testy ViewModelu: tahy, opakované odeslání, restart, obnovení hry, chybějící slovník, statistiky. */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeSource(private val words: ByteArray?, private val meta: ByteArray?) : DictionarySource {
        override fun readWords(): ByteArray = words ?: throw FileNotFoundException("cs_words.txt")
        override fun readMeta(): ByteArray? = meta
    }

    private fun nouns(vararg words: String): DictionarySource {
        val sorted = words.toSortedSet()
        val text = sorted.joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8)
        val meta = WordDictionary.buildMeta(sorted.map { WordMeta.build(basePos = 1, tier = 10) }, text)
        return FakeSource(text, meta)
    }

    private val standardWords = nouns(
        "kolo", "okno", "oko", "ovoce", "otázka", "auto", "opice", "eso", "osel", "lampa", "ananas",
        "sova", "anketa", "obrys", "slon", "noha", "hrad", "domov", "voda", "atlas", "sýr", "rak", "kosa",
    )

    @Suppress("UNUSED_PARAMETER")
    private fun newStore(name: String = "vm"): InMemoryGameStorage = InMemoryGameStorage()

    private fun newViewModel(source: DictionarySource, store: GameStorage, seed: Int = 1) = GameViewModel(
        dictionaryRepository = DictionaryRepository(source, dispatcher),
        store = store,
        computeDispatcher = dispatcher,
        random = Random(seed),
        minThinkingMillis = { 0L },
        computerTimeLimitMillis = { 50L },
    )

    private fun GameViewModel.play(word: String) {
        onInputChange(word)
        submitWord()
    }

    @Test
    fun loadsDictionaryAndIsReady() = runTest(dispatcher) {
        val vm = newViewModel(standardWords, newStore())
        assertEquals(GamePhase.LOADING, vm.uiState.value.phase)
        advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals(GamePhase.READY, s.phase)
        assertEquals(23, s.dictionaryWordCount)
        assertTrue(s.moves.isEmpty())
    }

    @Test
    fun playerMoveIsAnsweredByComputer() = runTest(dispatcher) {
        val vm = newViewModel(standardWords, newStore())
        advanceUntilIdle()
        vm.play("kolo")
        advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals(2, s.moves.size)
        assertEquals(Player.HUMAN, s.moves[0].player)
        assertEquals(Player.COMPUTER, s.moves[1].player)
        assertTrue(s.moves[1].word.startsWith("o"))
        assertEquals("", s.input)
        assertEquals(1, s.acceptedPulse)
    }

    @Test
    fun rapidRepeatedSubmissionIsIgnored() = runTest(dispatcher) {
        val vm = newViewModel(standardWords, newStore())
        advanceUntilIdle()
        vm.onInputChange("kolo")
        repeat(5) { vm.submitWord() } // dvojklik / rychlé opakované odeslání
        advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals(1, s.moves.count { it.player == Player.HUMAN })
        assertEquals(2, s.moves.size)
    }

    @Test
    fun invalidWordShowsErrorAndGameContinues() = runTest(dispatcher) {
        val vm = newViewModel(standardWords, newStore())
        advanceUntilIdle()
        vm.play("xyzqw")
        advanceUntilIdle()
        assertEquals("Toto slovo není v českém slovníku.", vm.uiState.value.inputError)
        assertEquals(GamePhase.READY, vm.uiState.value.phase)
        vm.play("")
        advanceUntilIdle()
        assertEquals("Zadej nějaké slovo.", vm.uiState.value.inputError)
        vm.play("kolo")
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.moves.size)
    }

    @Test
    fun winIsRecordedInStatistics() = runTest(dispatcher) {
        val store = newStore()
        val vm = newViewModel(nouns("lampa", "kolo"), store)
        advanceUntilIdle()
        vm.play("lampa") // na A nic nezačíná -> počítač nemá odpověď
        advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals(GamePhase.PLAYER_WON, s.phase)
        assertEquals(EndReason.COMPUTER_HAS_NO_WORD, s.endReason)
        assertTrue(s.showResult)
        val stats = store.statistics()
        assertEquals(1, stats.gamesPlayed)
        assertEquals(1, stats.wins)
    }

    @Test
    fun lossWhenPlayerHasNoWordIsRecorded() = runTest(dispatcher) {
        val store = newStore()
        val vm = newViewModel(nouns("kolo", "okno"), store)
        advanceUntilIdle()
        vm.play("kolo")
        advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals(GamePhase.PLAYER_LOST, s.phase)
        assertEquals(EndReason.PLAYER_HAS_NO_WORD, s.endReason)
        assertEquals(1, store.statistics().losses)
    }

    @Test
    fun surrenderCountsAsLoss() = runTest(dispatcher) {
        val store = newStore()
        val vm = newViewModel(standardWords, store)
        advanceUntilIdle()
        vm.play("kolo")
        advanceUntilIdle()
        vm.requestSurrender()
        assertEquals(GameDialog.CONFIRM_SURRENDER, vm.uiState.value.dialog)
        vm.confirmSurrender()
        advanceUntilIdle()
        assertEquals(GamePhase.PLAYER_LOST, vm.uiState.value.phase)
        assertEquals(EndReason.PLAYER_SURRENDERED, vm.uiState.value.endReason)
        assertEquals(1, store.statistics().losses)
    }

    @Test
    fun newGameDuringComputerTurnCancelsComputerAndRestarts() = runTest(dispatcher) {
        val store = newStore()
        val vm = newViewModel(standardWords, store)
        advanceUntilIdle()
        vm.play("kolo")
        runCurrent() // tah hráče je přijat, počítač začíná přemýšlet
        vm.requestNewGame()
        assertEquals(GameDialog.CONFIRM_NEW_GAME, vm.uiState.value.dialog)
        vm.confirmNewGame()
        advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals(GamePhase.READY, s.phase)
        assertTrue(s.moves.isEmpty())
        assertFalse(s.showResult)
        assertEquals(1, store.statistics().losses) // rozehraná hra se započítala jako prohra
        vm.play("kolo") // po restartu lze slovo použít znovu
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.moves.size)
    }

    @Test
    fun gameIsRestoredAfterAppRestart() = runTest(dispatcher) {
        val store = newStore()
        val first = newViewModel(standardWords, store)
        advanceUntilIdle()
        first.play("kolo")
        advanceUntilIdle()
        val moves = first.uiState.value.moves
        assertEquals(2, moves.size)
        // nový ViewModel nad stejným úložištěm = aplikace po ukončení a znovuspuštění
        val second = newViewModel(standardWords, store, seed = 9)
        advanceUntilIdle()
        assertEquals(moves, second.uiState.value.moves)
        assertEquals(GamePhase.PLAYER_TURN, second.uiState.value.phase)
        assertEquals(first.uiState.value.requiredLetter, second.uiState.value.requiredLetter)
    }

    @Test
    fun newSettingsApplyOnlyToNewGame() = runTest(dispatcher) {
        val store = newStore()
        val vm = newViewModel(standardWords, store)
        advanceUntilIdle()
        vm.play("kolo")
        advanceUntilIdle()
        store.updateSettings { it.copy(rules = it.rules.copy(minWordLength = 5)) }
        advanceUntilIdle()
        assertTrue(vm.uiState.value.pendingRulesChange)
        assertEquals(2, vm.uiState.value.rules.minWordLength)
        vm.requestNewGame()
        vm.confirmNewGame()
        advanceUntilIdle()
        assertEquals(5, vm.uiState.value.rules.minWordLength)
        assertFalse(vm.uiState.value.pendingRulesChange)
        vm.play("kolo")
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.inputError) // "kolo" je teď příliš krátké
    }

    @Test
    fun missingDictionaryShowsError() = runTest(dispatcher) {
        val vm = newViewModel(FakeSource(null, null), newStore())
        advanceUntilIdle()
        assertEquals(GamePhase.ERROR, vm.uiState.value.phase)
        assertTrue(vm.uiState.value.errorMessage!!.contains("chybí"))
    }

    @Test
    fun emptyOrCorruptedDictionaryShowsError() = runTest(dispatcher) {
        val empty = newViewModel(FakeSource(ByteArray(0), null), newStore("a"))
        advanceUntilIdle()
        assertEquals(GamePhase.ERROR, empty.uiState.value.phase)
        assertEquals("Slovník je prázdný.", empty.uiState.value.errorMessage)
        val garbage = newViewModel(FakeSource(byteArrayOf(0x31, 0x32, 0x0A, 0xFF.toByte(), 0x0A), null), newStore("b"))
        advanceUntilIdle()
        assertEquals(GamePhase.ERROR, garbage.uiState.value.phase)
    }

    @Test
    fun hintsAreLimited() = runTest(dispatcher) {
        val vm = newViewModel(standardWords, newStore())
        advanceUntilIdle()
        repeat(5) {
            vm.useHint()
            advanceUntilIdle()
        }
        assertEquals(0, vm.uiState.value.hintsLeft)
        val hint = vm.uiState.value.hint
        assertNotNull(hint)
        vm.applyHint()
        assertEquals(hint, vm.uiState.value.input)
    }
}
