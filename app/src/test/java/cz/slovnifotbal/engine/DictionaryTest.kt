package cz.slovnifotbal.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlin.random.Random

/** Testy 1–3: načtení slovníku, kódování UTF-8 a diakritika. */
class DictionaryTest {

    private val real get() = TestDictionaries.real

    @Test
    fun realDictionaryLoadsWithMetadata() {
        val info = real.info
        assertEquals(MetadataStatus.OK, info.metadataStatus)
        assertEquals(0, info.skippedLines)
        assertEquals(0, info.duplicateLines)
        assertTrue("slovník má mít alespoň 50 000 slov, má ${real.size}", real.size >= 50_000)
        assertEquals(EXPECTED_WORD_COUNT, real.size)
        assertTrue(info.baseWordCount > 200_000)
        assertTrue(info.formOnlyCount > 100_000)
        assertTrue(info.properOnlyCount > 50_000)
        assertEquals(real.size, info.baseWordCount + info.formOnlyCount + info.properOnlyCount)
    }

    @Test
    fun dictionaryFileIsStrictUtf8OneLowercaseWordPerLine() {
        val bytes = TestDictionaries.assetFile("cs_words.txt").readBytes()
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = decoder.decode(ByteBuffer.wrap(bytes)).toString() // vyhodí výjimku při neplatném UTF-8
        assertFalse("soubor nesmí začínat BOM", text.startsWith("﻿"))
        assertTrue("soubor končí novým řádkem", text.endsWith("\n"))
        val lines = text.substring(0, text.length - 1).split('\n')
        assertEquals(EXPECTED_WORD_COUNT, lines.size)
        val allowed = Regex("^[a-záčďéěíňóřšťúůýž]+$")
        var previous = ""
        for ((i, line) in lines.withIndex()) {
            if (line.isEmpty()) fail("prázdný řádek ${i + 1}")
            if (!allowed.matches(line)) fail("neplatné znaky na řádku ${i + 1}: '$line'")
            if (i > 0 && previous >= line) fail("řádek ${i + 1} není seřazen nebo je duplicitní: '$previous' >= '$line'")
            previous = line
        }
    }

    @Test
    fun diacriticsAreDistinguished() {
        assertTrue("čaj" in real)
        assertFalse("caj" in real)
        val les = real.indexOf("les")
        val leto = real.indexOf("léto")
        val lek = real.indexOf("lék")
        assertTrue(les >= 0 && leto >= 0 && lek >= 0)
        assertEquals(3, setOf(les, leto, lek).size)
        for (w in listOf("kůň", "řeka", "ďábel", "žába", "šňůra", "úkol", "dítě", "náměstí", "chléb", "píseň")) {
            assertTrue("chybí slovo $w", w in real)
        }
        // "kun" je platný tvar (2. pád mn. č. od "kuna"), proto test používá jiná slova
        assertFalse("zaba" in real)
        assertFalse("dabel" in real)
        assertNotEquals(real.indexOf("byt"), real.indexOf("být"))
    }

    @Test
    fun containsExampleWordsFromRules() {
        for (w in listOf("kolo", "otázka", "auto", "okno", "opice", "chata")) {
            val i = real.indexOf(w)
            assertTrue("chybí $w", i >= 0)
            assertTrue("$w má být základní tvar", WordMeta.basePos(real.meta(i)) and 1 != 0)
        }
    }

    @Test
    fun containsRequiredProperNames() {
        for (w in listOf("otakar", "radek", "petr", "martin", "jana", "lenka")) {
            val i = real.indexOf(w)
            assertTrue("chybí jméno $w", i >= 0)
            assertTrue("$w má být vlastní jméno", WordMeta.isProperBase(real.meta(i)))
        }
    }

    @Test
    fun inflectedFormsAreMarkedAsForms() {
        for (w in listOf("ženy", "dělal", "kolem", "nejlepší")) {
            val i = real.indexOf(w)
            assertTrue("chybí tvar $w", i >= 0)
            assertTrue("$w má být tvar", WordMeta.formPos(real.meta(i)) != 0)
        }
        // nepravidelné tvary nejsou základní tvary
        for (w in listOf("koně", "psa", "stoly", "děti")) {
            val m = real.meta(real.indexOf(w))
            assertEquals("$w nemá být základní tvar", 0, WordMeta.basePos(m))
        }
    }

    @Test
    fun lookupRoundTripsForRandomWords() {
        val rnd = Random(7)
        repeat(5_000) {
            val i = rnd.nextInt(real.size)
            assertEquals(i, real.indexOf(real.wordAt(i)))
        }
        assertEquals(-1, real.indexOf("xyzqw"))
        assertEquals(-1, real.indexOf("kolo1"))
        assertEquals(-1, real.indexOf(""))
    }

    @Test
    fun parserHandlesBomCrlfEmptyAndInvalidLinesUnsortedInputAndDuplicates() {
        val text = "﻿okno\r\nauto\r\n\r\nkolo\nauto\nKOLO\nčaj\nč@j\nmotýl1\nšťáva\n\n"
        val dict = WordDictionary.load(text.toByteArray(Charsets.UTF_8), null)
        assertEquals(listOf("auto", "kolo", "okno", "čaj", "šťáva"), (0 until dict.size).map { dict.wordAt(it) })
        assertEquals(2, dict.info.skippedLines)
        assertEquals(2, dict.info.duplicateLines)
        assertEquals(MetadataStatus.MISSING, dict.info.metadataStatus)
        // bez metadat jsou všechna slova základní tvary všech slovních druhů
        assertTrue(WordFilter(cz.slovnifotbal.domain.GameRules()).isAllowed(dict.meta(dict.indexOf("kolo"))))
    }

    @Test(expected = DictionaryException::class)
    fun emptyDictionaryIsRejected() {
        WordDictionary.load(ByteArray(0), null)
    }

    @Test(expected = DictionaryException::class)
    fun dictionaryWithoutValidWordsIsRejected() {
        WordDictionary.load("123\n???\n\n".toByteArray(), null)
    }

    @Test
    fun mismatchedMetadataIsDetected() {
        val text = "auto\nkolo\n".toByteArray()
        val otherText = "auto\nkolo\nokno\n".toByteArray()
        val meta = WordDictionary.buildMeta(listOf(TestDictionaries.NOUN, TestDictionaries.NOUN, TestDictionaries.NOUN), otherText)
        val dict = WordDictionary.load(text, meta)
        assertEquals(MetadataStatus.MISMATCH, dict.info.metadataStatus)
        assertEquals(WordMeta.UNKNOWN, dict.meta(0))
        val good = WordDictionary.load(otherText, meta)
        assertEquals(MetadataStatus.OK, good.info.metadataStatus)
        assertEquals(TestDictionaries.NOUN, good.meta(2))
    }

    companion object {
        /** Počet slov v zabudovaném slovníku (viz tools/dictionary/stats.json). */
        const val EXPECTED_WORD_COUNT = 805_232
    }
}
