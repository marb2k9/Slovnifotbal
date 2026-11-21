package cz.slovnifotbal.engine

import cz.slovnifotbal.domain.ChRule
import cz.slovnifotbal.domain.Difficulty
import cz.slovnifotbal.domain.GamePhase
import cz.slovnifotbal.domain.GameRules
import cz.slovnifotbal.domain.LinkRule
import cz.slovnifotbal.domain.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Test 12 a další: inteligence a korektnost počítačového soupeře. */
class ComputerPlayerTest {

    private fun cls(c: Char) = CzechAlphabet.codeOf(c)

    @Test
    fun hardTakesImmediateWin() {
        // z O vede "ob" (na B existuje odpověď) a "oz" (na Z neexistuje nic) -> těžká obtížnost musí zvolit "oz"
        val dict = TestDictionaries.nouns("ob", "oz", "ba", "bo")
        repeat(20) { seed ->
            val pool = WordPool(dict, GameRules(), Random(seed))
            val d = ComputerPlayer(Random(seed)).chooseMove(pool, cls('o'), Difficulty.HARD, 200)!!
            assertEquals("oz", dict.wordAt(d.wordIndex))
            assertTrue(d.forcedWin)
        }
    }

    @Test
    fun hardAvoidsMoveThatLetsOpponentWin() {
        // "ob": soupeř odpoví "bx" a počítač na X nemá nic -> prohra.
        // "oc": soupeř musí "cd", počítač "da" a soupeř na A nemá nic -> vynucená výhra.
        val dict = TestDictionaries.nouns("ob", "oc", "bx", "cd", "da")
        repeat(20) { seed ->
            val pool = WordPool(dict, GameRules(), Random(seed))
            val d = ComputerPlayer(Random(seed)).chooseMove(pool, cls('o'), Difficulty.HARD, 200)!!
            assertEquals("oc", dict.wordAt(d.wordIndex))
            assertTrue(d.forcedWin)
        }
    }

    @Test
    fun mediumUsuallyPrefersRestrictingLetters() {
        // z K: "ky" vede na Y (soupeř má 1 slovo), "ka" vede na A (soupeř má 30 slov)
        val words = mutableListOf("ky", "ka", "yo", "oba", "obo", "oca", "oco", "oda", "uba", "ubo")
        for (i in 0 until 30) words += "a" + "bcdfghjklmnprstvz"[i % 17] + "ou"[i / 17]
        val dict = TestDictionaries.nouns(*words.distinct().toTypedArray())
        var restricting = 0
        repeat(200) { seed ->
            val pool = WordPool(dict, GameRules(), Random(seed))
            val d = ComputerPlayer(Random(seed)).chooseMove(pool, cls('k'), Difficulty.MEDIUM)!!
            if (dict.wordAt(d.wordIndex) == "ky") restricting++
        }
        assertTrue("střední obtížnost má většinou omezovat soupeře ($restricting/200)", restricting in 100..199)
    }

    @Test
    fun returnsNullWhenNoWordExists() {
        val dict = TestDictionaries.nouns("kolo", "okno")
        val pool = WordPool(dict, GameRules())
        for (d in Difficulty.entries) assertNull(ComputerPlayer().chooseMove(pool, cls('z'), d))
    }

    @Test
    fun hardRespectsTimeLimitOnRealDictionary() {
        val dict = TestDictionaries.real
        val pool = WordPool(dict, GameRules(), Random(3))
        val start = System.nanoTime()
        val d = ComputerPlayer(Random(3)).chooseMove(pool, cls('k'), Difficulty.HARD, 300)!!
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("výpočet trval $ms ms", ms < 1_000)
        assertTrue("hloubka prohledávání ${d.searchDepth}", d.searchDepth >= 2)
        assertTrue(dict.wordAt(d.wordIndex).startsWith("k"))
    }

    @Test
    fun hardStopsWhenCancelled() {
        val dict = TestDictionaries.real
        val pool = WordPool(dict, GameRules(), Random(3))
        val start = System.nanoTime()
        val d = ComputerPlayer(Random(3)).chooseMove(pool, cls('k'), Difficulty.HARD, 10_000) { true }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("zrušený výpočet trval $ms ms", ms < 500)
        assertTrue(d != null) // vrátí alespoň platný tah z první úrovně
    }

    /**
     * Simulace mnoha her na skutečném slovníku: hráč hraje podle nápovědy, počítač všech obtížností
     * musí vždy zahrát existující, povolené, nepoužité slovo se správným počátečním písmenem.
     */
    @Test
    fun computerAlwaysPlaysLegalWordsInSimulatedGames() {
        val dict = TestDictionaries.real
        val ruleSets = listOf(
            GameRules(),
            GameRules(chRule = ChRule.SINGLE_LETTER, linkRule = LinkRule.STRICT, minWordLength = 3),
            GameRules(inflectedForms = true, properNames = true),
        )
        var games = 0
        var computerMoves = 0
        for (rules in ruleSets) for (difficulty in Difficulty.entries) repeat(3) { seed ->
            val r = rules.copy(difficulty = difficulty)
            val engine = WordFootballEngine(dict, r, ComputerPlayer(Random(seed)), Random(seed))
            val filter = WordFilter(r)
            val used = HashSet<String>()
            var turns = 0
            while (!engine.phase.isFinished && turns < 30) {
                val hint = engine.hint() ?: break
                val res = engine.playPlayerWord(hint)
                assertTrue("nápověda $hint musí být platná: $res", res is PlayerMoveResult.Accepted)
                used += hint
                if (engine.phase == GamePhase.COMPUTER_THINKING) {
                    val required = engine.requiredClass
                    val c = engine.playComputerMove(60)
                    if (c is ComputerMoveResult.Played) {
                        val w = c.move.word
                        val idx = dict.indexOf(w)
                        assertTrue("slovo počítače $w musí být ve slovníku", idx >= 0)
                        assertTrue("slovo počítače $w musí být povolené", filter.isAllowed(dict.meta(idx)))
                        assertTrue("slovo počítače $w už bylo použito", used.add(w))
                        assertTrue("slovo $w je kratší než minimum",
                            CzechAlphabet.letterCount(w, r.chRule) >= r.minWordLength)
                        val first = CzechAlphabet.linkClass(CzechAlphabet.firstSymbol(w, r.chRule), r.linkRule)
                        assertEquals("slovo $w nezačíná správným písmenem", required, first)
                        computerMoves++
                    }
                }
                turns++
            }
            val moves = engine.moves
            moves.forEachIndexed { i, m -> assertEquals(if (i % 2 == 0) Player.HUMAN else Player.COMPUTER, m.player) }
            games++
        }
        assertEquals(27, games)
        assertTrue(computerMoves > 100)
    }
}
