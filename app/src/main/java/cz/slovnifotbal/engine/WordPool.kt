package cz.slovnifotbal.engine

import cz.slovnifotbal.domain.GameRules
import java.util.BitSet
import kotlin.math.ceil
import kotlin.random.Random

/**
 * Index slov povolených v jedné hře.
 *
 * Slova jsou rozdělena do „kapes“ podle dvojice (třída prvního písmene, třída posledního písmene).
 * Uvnitř kapsy jsou seřazena od nejběžnějších (podle frekvenční třídy) a v rámci stejné třídy náhodně
 * promíchána. Díky tomu lze okamžitě zjistit, kolik nepoužitých slov začíná nebo končí
 * na dané písmeno, a rychle vybrat konkrétní slovo.
 */
class WordPool(
    val dictionary: WordDictionary,
    val rules: GameRules,
    random: Random = Random.Default,
) {
    private val k = CzechAlphabet.SYMBOLS
    private val filter = WordFilter(rules)
    private val inPool = BitSet(dictionary.size)
    private val used = BitSet(dictionary.size)
    private val remaining = IntArray(k * k)
    private val rowSum = IntArray(k)
    private val bucketStart = IntArray(k * k + 1)
    private val bucketWords: IntArray

    /** Počet slov povolených pravidly této hry. */
    val totalWords: Int

    init {
        val n = dictionary.size
        val tiers = 16
        val segments = IntArray(k * k * tiers + 1)
        val codes = dictionary.codes
        var total = 0
        for (i in 0 until n) {
            if (!eligible(i, codes)) continue
            inPool.set(i)
            val b = bucketOf(i, codes)
            val t = WordMeta.tier(dictionary.meta(i))
            segments[b * tiers + (tiers - 1 - t)]++
            total++
        }
        totalWords = total
        // prefixové součty -> začátky segmentů
        var acc = 0
        for (s in segments.indices) {
            val c = segments[s]
            segments[s] = acc
            acc += c
        }
        val fill = segments.copyOf()
        val words = IntArray(total)
        var i = inPool.nextSetBit(0)
        while (i >= 0) {
            val b = bucketOf(i, codes)
            val t = WordMeta.tier(dictionary.meta(i))
            val seg = b * tiers + (tiers - 1 - t)
            words[fill[seg]++] = i
            i = inPool.nextSetBit(i + 1)
        }
        // zamíchání v rámci stejné frekvenční třídy (pestřejší výběr slov)
        for (seg in 0 until k * k * tiers) {
            val from = segments[seg]
            val to = segments[seg + 1]
            for (p in to - 1 downTo from + 1) {
                val q = from + random.nextInt(p - from + 1)
                val tmp = words[p]
                words[p] = words[q]
                words[q] = tmp
            }
        }
        for (b in 0 until k * k) {
            bucketStart[b] = segments[b * tiers]
            val size = segments[(b + 1) * tiers] - segments[b * tiers]
            remaining[b] = size
            rowSum[b / k] += size
        }
        bucketStart[k * k] = total
        bucketWords = words
    }

    private fun eligible(i: Int, codes: ByteArray): Boolean {
        val s = dictionary.start(i)
        val e = dictionary.end(i)
        if (CzechAlphabet.letterCount(codes, s, e, rules.chRule) < rules.minWordLength) return false
        return filter.isAllowed(dictionary.meta(i))
    }

    private fun bucketOf(i: Int, codes: ByteArray): Int =
        startClassOf(i, codes) * k + endClassOf(i, codes)

    private fun startClassOf(i: Int, codes: ByteArray): Int = CzechAlphabet.linkClass(
        CzechAlphabet.firstSymbol(codes, dictionary.start(i), dictionary.end(i), rules.chRule), rules.linkRule,
    )

    private fun endClassOf(i: Int, codes: ByteArray): Int = CzechAlphabet.linkClass(
        CzechAlphabet.lastSymbol(codes, dictionary.start(i), dictionary.end(i), rules.chRule), rules.linkRule,
    )

    /** Třída prvního písmene slova (podle pravidel CH a diakritiky). */
    fun startClassOf(index: Int): Int = startClassOf(index, dictionary.codes)

    /** Třída posledního písmene slova (podle pravidel CH a diakritiky). */
    fun endClassOf(index: Int): Int = endClassOf(index, dictionary.codes)

    /** Slovo vyhovuje pravidlům hry (bez ohledu na to, zda už bylo použito). */
    fun isEligible(index: Int): Boolean = index in 0 until dictionary.size && inPool.get(index)

    fun isUsed(index: Int): Boolean = used.get(index)

    fun isAvailable(index: Int): Boolean = isEligible(index) && !used.get(index)

    /** Počet nepoužitých slov začínajících třídou [from]. */
    fun availableFrom(from: Int): Int = rowSum[from]

    /** Počet nepoužitých slov z třídy [from] do třídy [to]. */
    fun available(from: Int, to: Int): Int = remaining[from * k + to]

    fun copyRemaining(): IntArray = remaining.copyOf()

    fun copyRowSums(): IntArray = rowSum.copyOf()

    /** Označí slovo jako použité. */
    fun markUsed(index: Int) {
        if (used.get(index)) return
        used.set(index)
        if (inPool.get(index)) {
            val s = startClassOf(index)
            val b = s * k + endClassOf(index)
            remaining[b]--
            rowSum[s]--
        }
    }

    /**
     * Vybere nepoužité slovo z kapsy (from, to).
     * @param topFraction část kapsy (od nejběžnějších slov), ze které se náhodně vybírá; 0 = nejběžnější slovo.
     * @return index slova nebo -1.
     */
    fun pickWord(from: Int, to: Int, topFraction: Float, random: Random): Int {
        val b = from * k + to
        if (remaining[b] <= 0) return -1
        val s = bucketStart[b]
        val e = bucketStart[b + 1]
        val size = e - s
        if (size <= 0) return -1
        val top = if (topFraction <= 0f) 1 else maxOf(1, minOf(size, ceil(size * topFraction).toInt()))
        var idx = s + random.nextInt(top)
        for (step in 0 until size) {
            val w = bucketWords[idx]
            if (!used.get(w)) return w
            idx++
            if (idx == e) idx = s
        }
        return -1
    }

    /** Náhodné nepoužité slovo začínající třídou [from] (preferuje běžná slova), nebo -1. */
    fun randomWordFrom(from: Int, random: Random, topFraction: Float = 0.3f): Int {
        val total = rowSum[from]
        if (total <= 0) return -1
        var r = random.nextInt(total)
        for (to in 0 until k) {
            val c = remaining[from * k + to]
            if (r < c) return pickWord(from, to, topFraction, random)
            r -= c
        }
        return -1
    }

    companion object {
        const val CLASSES = CzechAlphabet.SYMBOLS
    }
}
