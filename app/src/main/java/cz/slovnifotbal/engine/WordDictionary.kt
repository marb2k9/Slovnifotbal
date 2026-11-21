package cz.slovnifotbal.engine

import java.util.zip.CRC32

/** Chyba při načítání slovníku (chybějící, prázdný nebo poškozený soubor). */
class DictionaryException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Stav souboru s metadaty slovníku. */
enum class MetadataStatus {
    /** Metadata odpovídají souboru se slovy. */
    OK,

    /** Soubor s metadaty chybí – všechna slova se berou jako základní tvary všech slovních druhů. */
    MISSING,

    /** Metadata nepatří k aktuálnímu souboru se slovy (např. po ruční úpravě slovníku). */
    MISMATCH,
}

/** Přehled o načteném slovníku. */
data class DictionaryInfo(
    val wordCount: Int,
    val baseWordCount: Int,
    val formOnlyCount: Int,
    val properOnlyCount: Int,
    val skippedLines: Int,
    val duplicateLines: Int,
    val metadataStatus: MetadataStatus,
)

/**
 * Bity metadat jednoho slova (formát souboru cs_words.meta):
 *  - bity 0–4: slovní druhy základního tvaru,
 *  - bity 5–9: slovní druhy, jejichž je slovo skloňovaným/časovaným tvarem,
 *  - bit 10: vlastní jméno (základní tvar), bit 11: vlastní jméno (tvar),
 *  - bity 12–15: frekvenční třída 0–15.
 */
object WordMeta {
    const val POS_MASK = 0x1F
    const val PROPER_BASE = 1 shl 10
    const val PROPER_FORM = 1 shl 11

    fun basePos(meta: Int): Int = meta and POS_MASK
    fun formPos(meta: Int): Int = (meta ushr 5) and POS_MASK
    fun isProperBase(meta: Int): Boolean = meta and PROPER_BASE != 0
    fun isProperForm(meta: Int): Boolean = meta and PROPER_FORM != 0
    fun tier(meta: Int): Int = (meta ushr 12) and 0xF

    fun build(basePos: Int = 0, formPos: Int = 0, properBase: Boolean = false, properForm: Boolean = false, tier: Int = 8): Int {
        var v = (basePos and POS_MASK) or ((formPos and POS_MASK) shl 5)
        if (properBase) v = v or PROPER_BASE
        if (properForm) v = v or PROPER_FORM
        return v or ((tier and 0xF) shl 12)
    }

    /** Výchozí metadata, pokud chybí soubor s metadaty: základní tvar všech slovních druhů. */
    val UNKNOWN: Int = build(basePos = POS_MASK, tier = 8)
}

/**
 * Kompaktní, neměnný český slovník.
 *
 * Slova jsou uložena jako jedno pole kódů písmen ([CzechAlphabet]) seřazené lexikograficky,
 * takže vyhledání slova je binární vyhledávání bez alokací. Paměťová náročnost je přibližně
 * 1 bajt na písmeno + 6 bajtů na slovo.
 */
class WordDictionary private constructor(
    internal val codes: ByteArray,
    internal val offsets: IntArray,
    private val metaValues: CharArray,
    val info: DictionaryInfo,
) {
    val size: Int get() = offsets.size - 1

    fun start(index: Int): Int = offsets[index]

    fun end(index: Int): Int = offsets[index + 1]

    fun length(index: Int): Int = offsets[index + 1] - offsets[index]

    fun meta(index: Int): Int = metaValues[index].code

    fun wordAt(index: Int): String {
        val s = offsets[index]
        val e = offsets[index + 1]
        val chars = CharArray(e - s)
        for (i in s until e) chars[i - s] = CzechAlphabet.charOf(codes[i].toInt())
        return String(chars)
    }

    /** Index slova nebo -1. Slovo musí být normalizované (malá písmena). */
    fun indexOf(word: String): Int {
        val encoded = CzechAlphabet.encode(word) ?: return -1
        return indexOf(encoded)
    }

    fun indexOf(encoded: ByteArray): Int {
        var lo = 0
        var hi = size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = compareWith(encoded, mid)
            when {
                c > 0 -> lo = mid + 1
                c < 0 -> hi = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    operator fun contains(word: String): Boolean = indexOf(word) >= 0

    /** Porovná [encoded] se slovem na indexu (záporné = encoded je menší). */
    private fun compareWith(encoded: ByteArray, index: Int): Int {
        val s = offsets[index]
        val len = offsets[index + 1] - s
        val n = minOf(len, encoded.size)
        for (i in 0 until n) {
            val d = encoded[i] - codes[s + i]
            if (d != 0) return d
        }
        return encoded.size - len
    }

    companion object {
        private val META_MAGIC = byteArrayOf('S'.code.toByte(), 'F'.code.toByte(), 'M'.code.toByte(), '1'.code.toByte())
        private const val META_HEADER_SIZE = 12
        private const val MAX_LINE_LETTERS = 64

        /**
         * Načte slovník ze souboru se slovy (UTF-8, jedno slovo na řádek) a volitelných metadat.
         *
         * Prázdné řádky a řádky s neplatnými znaky se přeskočí. Pokud soubor není seřazený nebo
         * obsahuje duplicity, slovník se seřadí a duplicity sloučí.
         *
         * @throws DictionaryException pokud slovník neobsahuje žádné platné slovo.
         */
        fun load(wordsBytes: ByteArray, metaBytes: ByteArray?, progress: ((Float) -> Unit)? = null): WordDictionary {
            if (wordsBytes.isEmpty()) throw DictionaryException("Slovník je prázdný.")
            var pos = 0
            if (wordsBytes.size >= 3 && wordsBytes[0] == 0xEF.toByte() && wordsBytes[1] == 0xBB.toByte() && wordsBytes[2] == 0xBF.toByte()) {
                pos = 3
            }
            val total = wordsBytes.size
            val codes = ByteArray(total)
            var codeLen = 0
            var offsets = IntArray(maxOf(1024, total / 8))
            var count = 0
            offsets[0] = 0
            var skipped = 0
            var sorted = true
            var prevStart = 0
            var prevEnd = 0
            val line = ByteArray(MAX_LINE_LETTERS)
            var nextProgress = 0

            while (pos < total) {
                var lineLen = 0
                var valid = true
                // dekódování jednoho řádku
                while (pos < total) {
                    val b = wordsBytes[pos].toInt() and 0xFF
                    if (b == '\n'.code) {
                        pos++
                        break
                    }
                    if (b == '\r'.code) {
                        pos++
                        continue
                    }
                    var ch: Int
                    if (b < 0x80) {
                        ch = b
                        pos++
                    } else if (b and 0xE0 == 0xC0 && pos + 1 < total) {
                        val b2 = wordsBytes[pos + 1].toInt() and 0xFF
                        ch = ((b and 0x1F) shl 6) or (b2 and 0x3F)
                        if (b2 and 0xC0 != 0x80) valid = false
                        pos += 2
                    } else {
                        // tří- a vícebajtové znaky nejsou česká písmena
                        valid = false
                        pos++
                        continue
                    }
                    if (!valid) continue
                    var code = CzechAlphabet.codeOf(ch.toChar())
                    if (code == 0) code = CzechAlphabet.codeOf(Character.toLowerCase(ch.toChar()))
                    if (code == 0 || lineLen >= MAX_LINE_LETTERS) {
                        valid = false
                        continue
                    }
                    line[lineLen++] = code.toByte()
                }
                if (!valid || lineLen == 0) {
                    if (lineLen > 0 || !valid) skipped++
                    continue
                }
                System.arraycopy(line, 0, codes, codeLen, lineLen)
                val start = codeLen
                codeLen += lineLen
                if (count > 0 && sorted && compareRanges(codes, prevStart, prevEnd, start, codeLen) >= 0) {
                    sorted = false
                }
                prevStart = start
                prevEnd = codeLen
                count++
                if (count + 1 >= offsets.size) offsets = offsets.copyOf(offsets.size * 2)
                offsets[count] = codeLen
                if (progress != null && pos >= nextProgress) {
                    progress(pos.toFloat() / total)
                    nextProgress = pos + total / 50
                }
            }
            if (count == 0) throw DictionaryException("Slovník neobsahuje žádné platné slovo.")

            val finalOffsets = offsets.copyOf(count + 1)
            val metaResult = readMeta(metaBytes, wordsBytes, count, skipped)
            var meta = metaResult.first
            var metaStatus = metaResult.second

            var resultCodes = codes
            var resultOffsets = finalOffsets
            var duplicates = 0
            if (!sorted) {
                val merged = sortAndMerge(codes, finalOffsets, meta)
                resultCodes = merged.codes
                resultOffsets = merged.offsets
                meta = merged.meta
                duplicates = merged.duplicates
            }
            // zkrácení pole kódů na skutečnou velikost
            val usedLen = resultOffsets[resultOffsets.size - 1]
            if (resultCodes.size != usedLen) resultCodes = resultCodes.copyOf(usedLen)

            var base = 0
            var formOnly = 0
            var properOnly = 0
            for (i in 0 until resultOffsets.size - 1) {
                val m = meta[i].code
                val bp = WordMeta.basePos(m)
                val fp = WordMeta.formPos(m)
                if (bp != 0) base++ else if (fp != 0) formOnly++ else properOnly++
            }
            progress?.invoke(1f)
            return WordDictionary(
                codes = resultCodes,
                offsets = resultOffsets,
                metaValues = meta,
                info = DictionaryInfo(
                    wordCount = resultOffsets.size - 1,
                    baseWordCount = base,
                    formOnlyCount = formOnly,
                    properOnlyCount = properOnly,
                    skippedLines = skipped,
                    duplicateLines = duplicates,
                    metadataStatus = metaStatus,
                ),
            )
        }

        /** Vytvoří slovník ze seznamu slov a jejich metadat (používá se v testech a pro vlastní slovníky). */
        fun fromEntries(entries: Map<String, Int>): WordDictionary {
            // TreeMap řadí podle UTF-16 jednotek – pro česká písmena totéž co pořadí kódových bodů
            val normalized = java.util.TreeMap<String, Int>()
            for ((key, value) in entries) {
                val w = CzechAlphabet.normalize(key)
                if (w.isNotEmpty()) normalized[w] = (normalized[w] ?: 0) or value
            }
            val text = normalized.keys.joinToString(separator = "\n", postfix = "\n").toByteArray(Charsets.UTF_8)
            val metaBytes = buildMeta(normalized.values.toList(), text)
            return load(text, metaBytes)
        }

        /** Sestaví binární soubor metadat ke zadanému textu slovníku. */
        fun buildMeta(values: List<Int>, wordsBytes: ByteArray): ByteArray {
            val out = ByteArray(META_HEADER_SIZE + values.size * 2)
            System.arraycopy(META_MAGIC, 0, out, 0, 4)
            writeIntLe(out, 4, values.size)
            val crc = CRC32().apply { update(wordsBytes) }.value
            writeIntLe(out, 8, crc.toInt())
            values.forEachIndexed { i, v ->
                out[META_HEADER_SIZE + 2 * i] = (v and 0xFF).toByte()
                out[META_HEADER_SIZE + 2 * i + 1] = ((v ushr 8) and 0xFF).toByte()
            }
            return out
        }

        private fun readMeta(metaBytes: ByteArray?, wordsBytes: ByteArray, count: Int, skipped: Int): Pair<CharArray, MetadataStatus> {
            val fallback = { status: MetadataStatus -> CharArray(count) { WordMeta.UNKNOWN.toChar() } to status }
            if (metaBytes == null) return fallback(MetadataStatus.MISSING)
            if (metaBytes.size < META_HEADER_SIZE) return fallback(MetadataStatus.MISMATCH)
            for (i in 0 until 4) if (metaBytes[i] != META_MAGIC[i]) return fallback(MetadataStatus.MISMATCH)
            val n = readIntLe(metaBytes, 4)
            val crc = readIntLe(metaBytes, 8).toLong() and 0xFFFFFFFFL
            val actualCrc = CRC32().apply { update(wordsBytes) }.value
            if (n != count || skipped != 0 || crc != actualCrc || metaBytes.size < META_HEADER_SIZE + 2 * n) {
                return fallback(MetadataStatus.MISMATCH)
            }
            val meta = CharArray(n)
            for (i in 0 until n) {
                val lo = metaBytes[META_HEADER_SIZE + 2 * i].toInt() and 0xFF
                val hi = metaBytes[META_HEADER_SIZE + 2 * i + 1].toInt() and 0xFF
                meta[i] = ((hi shl 8) or lo).toChar()
            }
            return meta to MetadataStatus.OK
        }

        private fun readIntLe(b: ByteArray, at: Int): Int =
            (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) or
                ((b[at + 2].toInt() and 0xFF) shl 16) or ((b[at + 3].toInt() and 0xFF) shl 24)

        private fun writeIntLe(b: ByteArray, at: Int, v: Int) {
            b[at] = (v and 0xFF).toByte()
            b[at + 1] = ((v ushr 8) and 0xFF).toByte()
            b[at + 2] = ((v ushr 16) and 0xFF).toByte()
            b[at + 3] = ((v ushr 24) and 0xFF).toByte()
        }

        private fun compareRanges(codes: ByteArray, aStart: Int, aEnd: Int, bStart: Int, bEnd: Int): Int {
            val la = aEnd - aStart
            val lb = bEnd - bStart
            val n = minOf(la, lb)
            for (i in 0 until n) {
                val d = codes[aStart + i] - codes[bStart + i]
                if (d != 0) return d
            }
            return la - lb
        }

        private class Merged(val codes: ByteArray, val offsets: IntArray, val meta: CharArray, val duplicates: Int)

        /** Seřadí slova a sloučí duplicity (metadata duplicit se spojí). */
        private fun sortAndMerge(codes: ByteArray, offsets: IntArray, meta: CharArray): Merged {
            val n = offsets.size - 1
            val order = IntArray(n) { it }
            val tmp = IntArray(n)
            mergeSort(order, tmp, 0, n) { a, b -> compareRanges(codes, offsets[a], offsets[a + 1], offsets[b], offsets[b + 1]) }
            val outCodes = ByteArray(offsets[n])
            val outOffsets = IntArray(n + 1)
            val outMeta = CharArray(n)
            var w = 0
            var len = 0
            var duplicates = 0
            for (k in 0 until n) {
                val i = order[k]
                val s = offsets[i]
                val e = offsets[i + 1]
                if (w > 0 && sameRange(outCodes, outOffsets[w - 1], outOffsets[w], codes, s, e)) {
                    duplicates++
                    val a = outMeta[w - 1].code
                    val b = meta[i].code
                    val tier = maxOf(WordMeta.tier(a), WordMeta.tier(b))
                    outMeta[w - 1] = (((a or b) and 0x0FFF) or (tier shl 12)).toChar()
                    continue
                }
                System.arraycopy(codes, s, outCodes, len, e - s)
                len += e - s
                outMeta[w] = meta[i]
                w++
                outOffsets[w] = len
            }
            return Merged(outCodes.copyOf(len), outOffsets.copyOf(w + 1), outMeta.copyOf(w), duplicates)
        }

        private fun sameRange(a: ByteArray, aStart: Int, aEnd: Int, b: ByteArray, bStart: Int, bEnd: Int): Boolean {
            if (aEnd - aStart != bEnd - bStart) return false
            for (i in 0 until aEnd - aStart) if (a[aStart + i] != b[bStart + i]) return false
            return true
        }

        private inline fun mergeSort(a: IntArray, tmp: IntArray, from: Int, to: Int, crossinline cmp: (Int, Int) -> Int) {
            // iterativní merge sort (stabilní, bez rekurze)
            var width = 1
            val n = to - from
            var src = a
            var dst = tmp
            while (width < n) {
                var lo = from
                while (lo < to) {
                    val mid = minOf(lo + width, to)
                    val hi = minOf(lo + 2 * width, to)
                    var i = lo
                    var j = mid
                    var k = lo
                    while (i < mid && j < hi) {
                        if (cmp(src[i], src[j]) <= 0) dst[k++] = src[i++] else dst[k++] = src[j++]
                    }
                    while (i < mid) dst[k++] = src[i++]
                    while (j < hi) dst[k++] = src[j++]
                    lo += 2 * width
                }
                val t = src
                src = dst
                dst = t
                width *= 2
            }
            if (src !== a) System.arraycopy(src, from, a, from, n)
        }
    }
}
