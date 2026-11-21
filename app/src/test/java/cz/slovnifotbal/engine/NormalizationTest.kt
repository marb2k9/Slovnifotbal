package cz.slovnifotbal.engine

import cz.slovnifotbal.domain.ChRule
import cz.slovnifotbal.domain.GameRules
import cz.slovnifotbal.domain.LinkRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Test 4: normalizace a kontrola vstupu. */
class NormalizationTest {

    private val dict = TestDictionaries.nouns("kolo", "čaj", "žluťoučký", "chata", "okno")

    private fun engine(rules: GameRules = GameRules()) = WordFootballEngine(dict, rules)

    private fun errorOf(input: String, e: WordFootballEngine = engine()): MoveError? =
        (e.validate(input) as? ValidationResult.Invalid)?.error

    @Test
    fun normalizeTrimsLowercasesAndComposesDiacritics() {
        assertEquals("kolo", CzechAlphabet.normalize("  KoLo \t"))
        assertEquals("čaj", CzechAlphabet.normalize("ČAJ"))
        // rozložený zápis c + kombinující háček -> č
        assertEquals("čaj", CzechAlphabet.normalize("čaj"))
        assertEquals("žluťoučký", CzechAlphabet.normalize("ŽLUŤOUČKÝ"))
    }

    @Test
    fun validInputVariantsAreAccepted() {
        for (input in listOf("kolo", " Kolo ", "KOLO", "Čaj", "čaj")) {
            val r = engine().validate(input)
            assertTrue("$input má být platné, je $r", r is ValidationResult.Valid)
        }
    }

    @Test
    fun invalidInputIsRejectedWithCzechMessages() {
        assertEquals(MoveError.EMPTY, errorOf(""))
        assertEquals(MoveError.EMPTY, errorOf("    "))
        assertEquals(MoveError.MULTIPLE_WORDS, errorOf("kolo okno"))
        assertEquals(MoveError.DIGITS, errorOf("kolo1"))
        assertEquals(MoveError.INVALID_CHARACTERS, errorOf("kolo!"))
        assertEquals(MoveError.INVALID_CHARACTERS, errorOf("ko-lo"))
        assertEquals(MoveError.FOREIGN_LETTER, errorOf("kölo"))
        assertEquals(MoveError.TOO_LONG, errorOf("a".repeat(GameRules.MAX_WORD_LENGTH + 1)))
        assertEquals(MoveError.NOT_IN_DICTIONARY, errorOf("caj"))
        val empty = engine().validate("") as ValidationResult.Invalid
        assertEquals("Zadej nějaké slovo.", empty.message)
        val unknown = engine().validate("caj") as ValidationResult.Invalid
        assertEquals("Toto slovo není v českém slovníku.", unknown.message)
    }

    @Test
    fun letterCountRespectsChRule() {
        assertEquals(5, CzechAlphabet.letterCount("chata", ChRule.TWO_LETTERS))
        assertEquals(4, CzechAlphabet.letterCount("chata", ChRule.SINGLE_LETTER))
        assertEquals(1, CzechAlphabet.letterCount("ch", ChRule.SINGLE_LETTER))
        assertEquals(5, CzechAlphabet.letterCount("prach", ChRule.TWO_LETTERS))
        assertEquals(4, CzechAlphabet.letterCount("prach", ChRule.SINGLE_LETTER))
        assertEquals(5, CzechAlphabet.letterCount("chochol", ChRule.SINGLE_LETTER))
        assertEquals(7, CzechAlphabet.letterCount("chochol", ChRule.TWO_LETTERS))
    }

    @Test
    fun linkClassesRelaxedAndStrict() {
        val i = CzechAlphabet.codeOf('i')
        val iLong = CzechAlphabet.codeOf('í')
        val c = CzechAlphabet.codeOf('c')
        val cHacek = CzechAlphabet.codeOf('č')
        assertEquals(i, CzechAlphabet.linkClass(iLong, LinkRule.RELAXED))
        assertEquals(iLong, CzechAlphabet.linkClass(iLong, LinkRule.STRICT))
        // č zůstává vždy samostatné písmeno
        assertEquals(cHacek, CzechAlphabet.linkClass(cHacek, LinkRule.RELAXED))
        assertTrue(CzechAlphabet.linkClass(cHacek, LinkRule.RELAXED) != c)
        assertEquals("U / Ú / Ů", CzechAlphabet.describeClass(CzechAlphabet.codeOf('u'), LinkRule.RELAXED))
        assertEquals("CH", CzechAlphabet.displayName(CzechAlphabet.CH))
    }
}
