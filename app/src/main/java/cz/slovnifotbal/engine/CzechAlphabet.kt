package cz.slovnifotbal.engine

import cz.slovnifotbal.domain.ChRule
import cz.slovnifotbal.domain.LinkRule
import java.text.Normalizer
import java.util.Locale

/**
 * Česká abeceda a práce s písmeny.
 *
 * Každé malé české písmeno má kód 1..41. Pořadí kódů odpovídá pořadí Unicode
 * kódových bodů, takže porovnání kódů dává stejné řazení jako soubor slovníku.
 * Symbol [CH] (42) představuje spřežku CH v režimu „samostatné písmeno“.
 */
object CzechAlphabet {
    /** Malá písmena seřazená podle Unicode kódových bodů. */
    const val LETTERS = "abcdefghijklmnopqrstuvwxyzáéíóúýčďěňřšťůž"
    const val LETTER_COUNT = 41
    const val CH = 42

    /** Počet symbolů včetně nepoužitého indexu 0. */
    const val SYMBOLS = 43

    private val CZECH_LOCALE: Locale = Locale.forLanguageTag("cs-CZ")

    private val codeByChar = IntArray(0x180)

    /** Kód písmene C a H pro rozpoznání spřežky. */
    val CODE_C: Int
    val CODE_H: Int

    /** Volné navazování: písmeno s čárkou/kroužkem a ď, ť, ň, ě se navazuje jako písmeno bez nich. */
    private val relaxed = IntArray(SYMBOLS)

    init {
        LETTERS.forEachIndexed { i, c -> codeByChar[c.code] = i + 1 }
        CODE_C = codeByChar['c'.code]
        CODE_H = codeByChar['h'.code]
        for (s in 0 until SYMBOLS) relaxed[s] = s
        val pairs = listOf(
            'á' to 'a', 'é' to 'e', 'ě' to 'e', 'í' to 'i', 'ó' to 'o',
            'ú' to 'u', 'ů' to 'u', 'ý' to 'y', 'ď' to 'd', 'ť' to 't', 'ň' to 'n',
        )
        for ((from, to) in pairs) relaxed[codeOf(from)] = codeOf(to)
    }

    /** Kód malého českého písmene nebo 0, pokud znak není malé české písmeno. */
    fun codeOf(c: Char): Int = if (c.code < codeByChar.size) codeByChar[c.code] else 0

    fun charOf(code: Int): Char = LETTERS[code - 1]

    fun isCzechLetter(c: Char): Boolean = codeOf(c) != 0

    /** Velké písmeno pro zobrazení (CH pro spřežku). */
    fun displayName(symbol: Int): String = when (symbol) {
        CH -> "CH"
        in 1..LETTER_COUNT -> charOf(symbol).toString().uppercase(CZECH_LOCALE)
        else -> "?"
    }

    /** Třída písmene pro navazování podle zvoleného pravidla. */
    fun linkClass(symbol: Int, rule: LinkRule): Int =
        if (rule == LinkRule.STRICT) symbol else relaxed[symbol]

    /** Všechna písmena, která patří do stejné třídy navazování (pro zobrazení „I / Í“). */
    fun symbolsOfClass(linkClass: Int, rule: LinkRule): List<Int> =
        (1 until SYMBOLS).filter { linkClass(it, rule) == linkClass }

    /** Text požadovaného písmene, např. „R“, „I / Í“ nebo „U / Ú / Ů“. */
    fun describeClass(linkClass: Int, rule: LinkRule): String =
        symbolsOfClass(linkClass, rule).joinToString(" / ") { displayName(it) }

    /**
     * Normalizace vstupu: ořízne mezery, sjednotí Unicode (NFC – např. „c“ + háček → „č“)
     * a převede na malá písmena. Diakritiku zachovává.
     */
    fun normalize(raw: String): String =
        Normalizer.normalize(raw.trim(), Normalizer.Form.NFC).lowercase(CZECH_LOCALE)

    /** Převede slovo na pole kódů; vrátí null, pokud obsahuje cokoli jiného než malá česká písmena. */
    fun encode(word: String): ByteArray? {
        if (word.isEmpty()) return null
        val out = ByteArray(word.length)
        for (i in word.indices) {
            val code = codeOf(word[i])
            if (code == 0) return null
            out[i] = code.toByte()
        }
        return out
    }

    /** První písmeno slova (s ohledem na CH). */
    fun firstSymbol(codes: ByteArray, start: Int, end: Int, chRule: ChRule): Int {
        if (chRule == ChRule.SINGLE_LETTER && end - start >= 2 &&
            codes[start].toInt() == CODE_C && codes[start + 1].toInt() == CODE_H
        ) return CH
        return codes[start].toInt()
    }

    /** Poslední písmeno slova (s ohledem na CH). */
    fun lastSymbol(codes: ByteArray, start: Int, end: Int, chRule: ChRule): Int {
        if (chRule == ChRule.SINGLE_LETTER && end - start >= 2 &&
            codes[end - 2].toInt() == CODE_C && codes[end - 1].toInt() == CODE_H
        ) return CH
        return codes[end - 1].toInt()
    }

    /** Počet písmen slova (CH se v režimu samostatného písmene počítá jako jedno písmeno). */
    fun letterCount(codes: ByteArray, start: Int, end: Int, chRule: ChRule): Int {
        val len = end - start
        if (chRule != ChRule.SINGLE_LETTER) return len
        var pairs = 0
        var i = start
        while (i < end - 1) {
            if (codes[i].toInt() == CODE_C && codes[i + 1].toInt() == CODE_H) {
                pairs++
                i += 2
            } else {
                i++
            }
        }
        return len - pairs
    }

    fun firstSymbol(word: String, chRule: ChRule): Int {
        val codes = encode(word) ?: return 0
        return firstSymbol(codes, 0, codes.size, chRule)
    }

    fun lastSymbol(word: String, chRule: ChRule): Int {
        val codes = encode(word) ?: return 0
        return lastSymbol(codes, 0, codes.size, chRule)
    }

    fun letterCount(word: String, chRule: ChRule): Int {
        val codes = encode(word) ?: return word.length
        return letterCount(codes, 0, codes.size, chRule)
    }
}
