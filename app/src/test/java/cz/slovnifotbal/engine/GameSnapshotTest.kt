package cz.slovnifotbal.engine

import cz.slovnifotbal.domain.ChRule
import cz.slovnifotbal.domain.Difficulty
import cz.slovnifotbal.domain.EndReason
import cz.slovnifotbal.domain.GamePhase
import cz.slovnifotbal.domain.GameRules
import cz.slovnifotbal.domain.GameSnapshot
import cz.slovnifotbal.domain.LinkRule
import cz.slovnifotbal.domain.Move
import cz.slovnifotbal.domain.PartOfSpeech
import cz.slovnifotbal.domain.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

/** Test 20: správné obnovení stavu hry. */
class GameSnapshotTest {

    private val rules = GameRules(
        difficulty = Difficulty.HARD,
        properNames = true,
        minWordLength = 3,
        chRule = ChRule.SINGLE_LETTER,
        linkRule = LinkRule.STRICT,
        partsOfSpeech = setOf(PartOfSpeech.NOUN, PartOfSpeech.VERB),
        inflectedForms = true,
    )

    @Test
    fun encodeDecodeRoundTrip() {
        val s = GameSnapshot(
            rules = rules,
            moves = listOf(Move(Player.HUMAN, "kolo"), Move(Player.COMPUTER, "otázka")),
            phase = GamePhase.PLAYER_TURN,
            startedAtMillis = 1234L,
        )
        assertEquals(s, GameSnapshot.decode(s.encode()))
        val finished = s.copy(phase = GamePhase.PLAYER_LOST, endReason = EndReason.PLAYER_SURRENDERED)
        assertEquals(finished, GameSnapshot.decode(finished.encode()))
    }

    @Test
    fun corruptedSnapshotIsIgnored() {
        assertNull(GameSnapshot.decode(null))
        assertNull(GameSnapshot.decode(""))
        assertNull(GameSnapshot.decode("nesmysl"))
        assertNull(GameSnapshot.decode("SLOVNI_FOTBAL_GAME_V1\nphase=NEEXISTUJE\n"))
        assertNull(GameSnapshot.decode("SLOVNI_FOTBAL_GAME_V1\nphase=PLAYER_TURN\nřádek bez formátu\n"))
    }

    @Test
    fun engineRestoresGameInProgress() {
        val dict = TestDictionaries.real
        val e = WordFootballEngine(dict, GameRules(difficulty = Difficulty.MEDIUM), ComputerPlayer(Random(5)), Random(5))
        e.startedAtMillis = 42L
        e.playPlayerWord("kolo")
        e.playComputerMove(50)
        val hint = e.hint()!!
        e.playPlayerWord(hint)
        // uloženo během přemýšlení počítače
        val saved = GameSnapshot.decode(e.snapshot().encode())!!
        assertEquals(GamePhase.COMPUTER_THINKING, saved.phase)
        val restored = WordFootballEngine.restore(dict, saved, ComputerPlayer(Random(6)), Random(6))
        assertNotNull(restored)
        restored!!
        assertEquals(e.moves, restored.moves)
        assertEquals(GamePhase.COMPUTER_THINKING, restored.phase)
        assertEquals(e.requiredClass, restored.requiredClass)
        assertEquals(42L, restored.startedAtMillis)
        // obnovená hra pokračuje: počítač táhne a použitá slova zůstávají použitá
        val result = restored.playComputerMove(50)
        if (result is ComputerMoveResult.Played) assertEquals(e.moveCount + 1, restored.moveCount)
        assertEquals(true, restored.isWordUsed("kolo"))
        assertEquals(true, restored.isWordUsed(hint))
    }

    @Test
    fun snapshotNotMatchingDictionaryIsRejected() {
        val dict = TestDictionaries.nouns("kolo", "okno", "oko")
        val bad = GameSnapshot(GameRules(), listOf(Move(Player.HUMAN, "kolo"), Move(Player.COMPUTER, "xyz")), GamePhase.PLAYER_TURN)
        assertNull(WordFootballEngine.restore(dict, bad))
        val wrongOrder = GameSnapshot(GameRules(), listOf(Move(Player.COMPUTER, "kolo")), GamePhase.PLAYER_TURN)
        assertNull(WordFootballEngine.restore(dict, wrongOrder))
        val badChain = GameSnapshot(GameRules(), listOf(Move(Player.HUMAN, "okno"), Move(Player.COMPUTER, "kolo")), GamePhase.PLAYER_TURN)
        assertNull(WordFootballEngine.restore(dict, badChain))
        val duplicate = GameSnapshot(GameRules(), listOf(Move(Player.HUMAN, "oko"), Move(Player.COMPUTER, "oko")), GamePhase.PLAYER_TURN)
        assertNull(WordFootballEngine.restore(dict, duplicate))
    }
}
