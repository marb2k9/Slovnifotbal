package cz.slovnifotbal.engine

import cz.slovnifotbal.domain.ChRule
import cz.slovnifotbal.domain.Difficulty
import cz.slovnifotbal.domain.EndReason
import cz.slovnifotbal.domain.GamePhase
import cz.slovnifotbal.domain.GameRules
import cz.slovnifotbal.domain.LinkRule
import cz.slovnifotbal.domain.PartOfSpeech
import cz.slovnifotbal.domain.Player
import cz.slovnifotbal.engine.TestDictionaries.ADV
import cz.slovnifotbal.engine.TestDictionaries.NOUN
import cz.slovnifotbal.engine.TestDictionaries.NOUN_FORM
import cz.slovnifotbal.engine.TestDictionaries.PROPER
import cz.slovnifotbal.engine.TestDictionaries.PROPER_FORM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Testy 5–11, 13–16 a 19: pravidla hry. */
class EngineRulesTest {

    private fun engine(dict: WordDictionary, rules: GameRules = GameRules(), seed: Int = 1) =
        WordFootballEngine(dict, rules, ComputerPlayer(Random(seed)), Random(seed))

    private fun reject(e: WordFootballEngine, word: String): PlayerMoveResult.Rejected {
        val r = e.playPlayerWord(word)
        assertTrue("$word mělo být odmítnuto, ale: $r", r is PlayerMoveResult.Rejected)
        return r as PlayerMoveResult.Rejected
    }

    private fun accept(e: WordFootballEngine, word: String): PlayerMoveResult.Accepted {
        val r = e.playPlayerWord(word)
        assertTrue("$word mělo být přijato, ale: $r", r is PlayerMoveResult.Accepted)
        return r as PlayerMoveResult.Accepted
    }

    @Test
    fun firstMoveAcceptsAnyLetter() {
        val dict = TestDictionaries.nouns("kolo", "okno", "auto", "zebra", "ananas")
        for (first in listOf("kolo", "auto", "zebra")) {
            val e = engine(dict)
            assertEquals(GamePhase.READY, e.phase)
            assertNull(e.requiredLetterText())
            accept(e, first)
            assertEquals(1, e.moveCount)
        }
    }

    @Test
    fun computerAnswersWithLastLetterAndPlayerMustFollow() {
        val dict = TestDictionaries.nouns("kolo", "otázka", "okno", "auto", "opice", "eso", "osel", "lampa", "ananas", "sova")
        val e = engine(dict)
        accept(e, "kolo")
        assertEquals(GamePhase.COMPUTER_THINKING, e.phase)
        val c = e.playComputerMove(50) as ComputerMoveResult.Played
        assertEquals(Player.COMPUTER, c.move.player)
        assertTrue("počítač musí začít na O: ${c.move.word}", c.move.word.startsWith("o"))
        assertEquals(GamePhase.PLAYER_TURN, e.phase)
        val last = c.move.word.last()
        val required = CzechAlphabet.displayName(CzechAlphabet.codeOf(last))
        // slovo na jiné písmeno je odmítnuto se zprávou o požadovaném písmenu
        val wrong = listOf("kolo", "zebra", "lampa", "sova", "auto").first { !it.startsWith(last.toString()) && it in dict }
        val r = reject(e, wrong)
        assertEquals(MoveError.WRONG_START, r.error)
        assertTrue(r.message, r.message.startsWith("Slovo musí začínat na písmeno $required"))
        assertTrue(r.message, r.message.endsWith("."))
        // neplatný tah neukončí hru
        assertEquals(GamePhase.PLAYER_TURN, e.phase)
    }

    @Test
    fun wrongStartMessageMatchesSpecification() {
        val dict = TestDictionaries.nouns("motor", "rak", "kolo", "auto", "radar")
        val e = engine(dict)
        accept(e, "motor")
        e.playComputerMove(50)
        // počítač musel zahrát "rak" nebo "radar" -> další písmeno K nebo R
        val r = reject(e, "auto")
        assertEquals(MoveError.WRONG_START, r.error)
        assertTrue(r.message, r.message == "Slovo musí začínat na písmeno K." || r.message == "Slovo musí začínat na písmeno R.")
    }

    @Test
    fun chRuleTwoLetters() {
        val dict = TestDictionaries.nouns("prach", "hrad", "chata", "cesta", "auto", "dub")
        val rules = GameRules(chRule = ChRule.TWO_LETTERS)
        val e = engine(dict, rules)
        accept(e, "prach")
        val c = e.playComputerMove(50) as ComputerMoveResult.Played
        assertEquals("hrad", c.move.word) // jediné slovo na H
        // chata začíná na C (dvě písmena C + H)
        val e2 = engine(dict, rules)
        accept(e2, "auto")
        assertEquals("C", CzechAlphabet.displayName(CzechAlphabet.firstSymbol("chata", ChRule.TWO_LETTERS)))
        assertEquals("H", CzechAlphabet.displayName(CzechAlphabet.lastSymbol("prach", ChRule.TWO_LETTERS)))
    }

    @Test
    fun chRuleSingleLetter() {
        val dict = TestDictionaries.nouns("prach", "hrad", "chata", "cesta", "auto", "atlas")
        val rules = GameRules(chRule = ChRule.SINGLE_LETTER)
        val e = engine(dict, rules)
        accept(e, "prach")
        assertEquals("CH", e.requiredLetterText())
        val c = e.playComputerMove(50) as ComputerMoveResult.Played
        assertEquals("chata", c.move.word) // jediné slovo na CH, "hrad" ani "cesta" nevyhovují
        assertEquals(CzechAlphabet.CH, CzechAlphabet.firstSymbol("chata", ChRule.SINGLE_LETTER))
        assertEquals(CzechAlphabet.CH, CzechAlphabet.lastSymbol("prach", ChRule.SINGLE_LETTER))
        // a hráč na "cesta" nemůže odpovědět slovem na CH
        val e2 = engine(TestDictionaries.nouns("motýl", "lech", "chata", "hrad", "atlas"), rules)
        accept(e2, "motýl")
        e2.playComputerMove(50) // lech -> CH
        val r = reject(e2, "hrad")
        assertEquals(MoveError.WRONG_START, r.error)
        assertEquals("Slovo musí začínat na písmeno CH.", r.message)
        accept(e2, "chata")
    }

    @Test
    fun diacriticsLinkRelaxedAndStrict() {
        val dict = TestDictionaries.nouns("náměstí", "inženýr", "íčko", "ryba", "okno", "ovoce")
        val relaxed = engine(dict, GameRules(linkRule = LinkRule.RELAXED))
        accept(relaxed, "okno")
        relaxed.playComputerMove(50) // ovoce -> E
        val e = engine(TestDictionaries.nouns("náměstí", "inženýr", "íčko", "ryba"), GameRules(linkRule = LinkRule.RELAXED))
        accept(e, "náměstí") // končí na Í -> v uvolněném režimu se navazuje na I/Í
        assertEquals("I / Í", e.requiredLetterText())
        val strict = engine(TestDictionaries.nouns("náměstí", "inženýr", "ryba", "íčko"), GameRules(linkRule = LinkRule.STRICT))
        accept(strict, "náměstí")
        assertEquals("Í", strict.requiredLetterText())
        val c = strict.playComputerMove(50) as ComputerMoveResult.Played
        assertEquals("íčko", c.move.word)
    }

    @Test
    fun nonExistingWordIsRejectedAndPlayerCanRetry() {
        val dict = TestDictionaries.nouns("kolo", "okno", "oko")
        val e = engine(dict)
        val r = reject(e, "kolibřík")
        assertEquals(MoveError.NOT_IN_DICTIONARY, r.error)
        assertEquals("Toto slovo není v českém slovníku.", r.message)
        assertEquals(GamePhase.READY, e.phase)
        accept(e, "kolo")
    }

    @Test
    fun repeatedWordIsRejected() {
        val dict = TestDictionaries.nouns("kolo", "okno", "oko")
        val e = engine(dict)
        accept(e, "kolo")
        val c = e.playComputerMove(50) as ComputerMoveResult.Played // okno nebo oko -> O
        val r = reject(e, c.move.word)
        assertEquals(MoveError.ALREADY_USED, r.error)
        assertEquals("Toto slovo už bylo použito.", r.message)
        assertTrue(e.isWordUsed("kolo"))
    }

    @Test
    fun minimumWordLength() {
        val dict = TestDictionaries.nouns("a", "oko", "okno", "kolo")
        val e = engine(dict)
        val r = reject(e, "a")
        assertEquals(MoveError.TOO_SHORT, r.error)
        assertTrue(r.message.startsWith("Slovo je příliš krátké"))
        val e4 = engine(dict, GameRules(minWordLength = 4))
        assertEquals(MoveError.TOO_SHORT, reject(e4, "oko").error)
        accept(e4, "okno")
        // počítač nesmí použít krátké slovo -> nemá odpověď na O kromě "kolo"? (kolo začíná na K) => hráč vyhrál
        assertEquals(GamePhase.PLAYER_WON, e4.phase)
    }

    @Test
    fun properNamesFollowSettings() {
        val dict = TestDictionaries.of("petr" to PROPER, "radek" to PROPER, "kolo" to NOUN, "otakar" to PROPER, "petrovi" to PROPER_FORM)
        val off = engine(dict, GameRules(properNames = false))
        val r = reject(off, "petr")
        assertEquals(MoveError.PROPER_NAMES_DISABLED, r.error)
        val on = engine(dict, GameRules(properNames = true))
        accept(on, "petr")
        // tvar vlastního jména jen se zapnutými tvary
        assertEquals(MoveError.FORMS_DISABLED, reject(engine(dict, GameRules(properNames = true)), "petrovi").error)
        accept(engine(dict, GameRules(properNames = true, inflectedForms = true)), "petrovi")
    }

    @Test
    fun inflectedFormsAndPartsOfSpeechFollowSettings() {
        val dict = TestDictionaries.of("žena" to NOUN, "ženy" to NOUN_FORM, "rychle" to ADV, "auto" to NOUN)
        assertEquals(MoveError.FORMS_DISABLED, reject(engine(dict), "ženy").error)
        accept(engine(dict, GameRules(inflectedForms = true)), "ženy")
        val nounsOnly = GameRules(partsOfSpeech = setOf(PartOfSpeech.NOUN))
        assertEquals(MoveError.PART_OF_SPEECH_DISABLED, reject(engine(dict, nounsOnly), "rychle").error)
        accept(engine(dict, nounsOnly), "žena")
    }

    @Test
    fun computerWithoutWordMeansPlayerWins() {
        val dict = TestDictionaries.nouns("lampa", "kolo")
        val e = engine(dict)
        val r = accept(e, "lampa")
        assertFalse(r.computerHasAnswer)
        assertEquals(GamePhase.PLAYER_WON, e.phase)
        assertEquals(EndReason.COMPUTER_HAS_NO_WORD, e.endReason)
        assertEquals(MoveError.GAME_OVER, reject(e, "kolo").error)
        assertTrue(e.playComputerMove() is ComputerMoveResult.NotComputerTurn)
    }

    @Test
    fun playerWithoutWordLoses() {
        val dict = TestDictionaries.nouns("kolo", "okno")
        val e = engine(dict)
        accept(e, "kolo")
        val c = e.playComputerMove(50) as ComputerMoveResult.Played
        assertEquals("okno", c.move.word)
        assertFalse(c.playerHasAnswer)
        assertEquals(GamePhase.PLAYER_LOST, e.phase)
        assertEquals(EndReason.PLAYER_HAS_NO_WORD, e.endReason)
        assertFalse(e.playerHasValidWord())
    }

    @Test
    fun winLoseSurrenderAndRestart() {
        val dict = TestDictionaries.nouns("kolo", "okno", "oko", "ovoce", "eso")
        val e = engine(dict)
        accept(e, "kolo")
        e.playComputerMove(50)
        assertTrue(e.surrender())
        assertEquals(GamePhase.PLAYER_LOST, e.phase)
        assertEquals(EndReason.PLAYER_SURRENDERED, e.endReason)
        assertFalse(e.surrender())
        e.restart()
        assertEquals(GamePhase.READY, e.phase)
        assertEquals(0, e.moveCount)
        assertNull(e.endReason)
        assertFalse(e.isWordUsed("kolo"))
        accept(e, "kolo") // po restartu lze slovo použít znovu
        e.restart(GameRules(difficulty = Difficulty.HARD, minWordLength = 4))
        assertEquals(Difficulty.HARD, e.rules.difficulty)
        assertEquals(MoveError.TOO_SHORT, reject(e, "oko").error)
    }

    @Test
    fun rapidRepeatedSubmissionIsIgnoredWhileComputerThinks() {
        val dict = TestDictionaries.nouns("kolo", "okno", "oko", "ovoce")
        val e = engine(dict)
        accept(e, "kolo")
        val second = reject(e, "kolo")
        assertEquals(MoveError.NOT_PLAYER_TURN, second.error)
        assertEquals("Počítač přemýšlí.", second.message)
        val third = reject(e, "okno")
        assertEquals(MoveError.NOT_PLAYER_TURN, third.error)
        assertEquals(1, e.moveCount)
    }

    @Test
    fun cancelledComputerMoveDoesNotChangeState() {
        val dict = TestDictionaries.nouns("kolo", "okno", "oko", "ovoce")
        val e = engine(dict)
        accept(e, "kolo")
        val r = e.playComputerMove(50) { true }
        assertEquals(ComputerMoveResult.Cancelled, r)
        assertEquals(GamePhase.COMPUTER_THINKING, e.phase)
        assertEquals(1, e.moveCount)
        assertTrue(e.playComputerMove(50) is ComputerMoveResult.Played)
    }

    @Test
    fun hintGivesValidUnusedWord() {
        val dict = TestDictionaries.nouns("kolo", "okno", "oko", "ovoce", "eso", "osel", "lampa")
        val e = engine(dict)
        accept(e, "kolo")
        e.playComputerMove(50)
        val hint = e.hint()
        if (e.playerHasValidWord()) {
            assertTrue(hint != null)
            assertTrue(e.validate(hint!!) is ValidationResult.Valid)
        }
    }
}
