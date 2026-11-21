package cz.slovnifotbal.engine

import cz.slovnifotbal.domain.Difficulty
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

/**
 * Počítačový soupeř.
 *
 * Hra se dá popsat jako graf nad písmeny: každé slovo je hrana z třídy prvního písmene do třídy
 * posledního písmene a každá hrana se dá použít jen jednou. Slova se stejnou dvojicí písmen jsou
 * pro průběh hry zaměnitelná, takže soupeř neprohledává statisíce slov, ale jen matici počtů
 * zbývajících slov (43 × 43). Díky tomu je i hluboké prohledávání rychlé na každém telefonu.
 *
 *  - LEHKÁ: náhodné platné slovo (bez strategie), s preferencí běžných slov.
 *  - STŘEDNÍ: hodnotí koncová písmena podle počtu slov, která zbývají soupeři, vyhýbá se tahům,
 *    po kterých by sám neměl odpověď, a mezi dobrými tahy volí s prvkem náhody.
 *  - TĚŽKÁ: negamax s alfa-beta ořezáváním, iterativním prohlubováním a časovým limitem.
 */
class ComputerPlayer(private val random: Random = Random.Default) {

    /** Rozhodnutí počítače. */
    data class Decision(
        val wordIndex: Int,
        val targetClass: Int,
        val searchDepth: Int,
        val nodes: Long,
        val forcedWin: Boolean,
        val forcedLoss: Boolean,
    )

    private data class Plan(
        val target: Int,
        val depth: Int = 0,
        val nodes: Long = 0,
        val forcedWin: Boolean = false,
        val forcedLoss: Boolean = false,
    )

    /**
     * Vybere slovo začínající třídou [requiredClass], nebo vrátí null, pokud žádné neexistuje.
     * Funkce je čistě výpočetní – volá se mimo hlavní vlákno.
     */
    fun chooseMove(
        pool: WordPool,
        requiredClass: Int,
        difficulty: Difficulty,
        timeLimitMillis: Long = defaultTimeLimit(difficulty),
        isCancelled: () -> Boolean = { false },
    ): Decision? {
        if (pool.availableFrom(requiredClass) <= 0) return null
        val counts = pool.copyRemaining()
        val rows = pool.copyRowSums()
        val plan = when (difficulty) {
            Difficulty.EASY -> Plan(weightedRandomTarget(counts, requiredClass))
            Difficulty.MEDIUM -> Plan(mediumTarget(counts, rows, requiredClass), depth = 2)
            Difficulty.HARD -> hardTarget(counts, rows, requiredClass, timeLimitMillis, isCancelled)
        }
        val fraction = when (difficulty) {
            Difficulty.EASY -> 0.35f
            Difficulty.MEDIUM -> 0.5f
            Difficulty.HARD -> 0f
        }
        var word = if (plan.target > 0) pool.pickWord(requiredClass, plan.target, fraction, random) else -1
        if (word < 0) word = pool.randomWordFrom(requiredClass, random, 1f)
        if (word < 0) return null
        return Decision(word, pool.endClassOf(word), plan.depth, plan.nodes, plan.forcedWin, plan.forcedLoss)
    }

    /** Náhodná cílová třída vážená počtem slov (= náhodné slovo). */
    private fun weightedRandomTarget(counts: IntArray, r: Int): Int {
        var total = 0
        for (e in 1 until K) total += counts[r * K + e]
        if (total <= 0) return -1
        var x = random.nextInt(total)
        for (e in 1 until K) {
            val c = counts[r * K + e]
            if (x < c) return e
            x -= c
        }
        return -1
    }

    private fun mediumTarget(counts: IntArray, rows: IntArray, r: Int): Int {
        val candidates = IntArray(K)
        val scores = DoubleArray(K)
        var m = 0
        for (e in 1 until K) {
            if (counts[r * K + e] == 0) continue
            counts[r * K + e]--
            rows[r]--
            val opponentOptions = rows[e]
            var s: Double
            if (opponentOptions == 0) {
                s = IMMEDIATE_WIN_SCORE
            } else {
                s = -ln(1.0 + opponentOptions)
                // nevýhodná situace: soupeř může odpovědět tak, že já nebudu mít žádné slovo
                for (x in 1 until K) {
                    if (counts[e * K + x] == 0) continue
                    val mine = rows[x] - if (x == e) 1 else 0
                    if (mine <= 0) {
                        s -= DANGER_PENALTY
                        break
                    }
                }
            }
            counts[r * K + e]++
            rows[r]++
            candidates[m] = e
            scores[m] = s
            m++
        }
        if (m == 0) return -1
        var maxScore = Double.NEGATIVE_INFINITY
        for (i in 0 until m) if (scores[i] > maxScore) maxScore = scores[i]
        var sum = 0.0
        val weights = DoubleArray(m)
        for (i in 0 until m) {
            weights[i] = exp((scores[i] - maxScore) / MEDIUM_TEMPERATURE)
            sum += weights[i]
        }
        var x = random.nextDouble() * sum
        for (i in 0 until m) {
            x -= weights[i]
            if (x <= 0) return candidates[i]
        }
        return candidates[m - 1]
    }

    private fun hardTarget(
        counts: IntArray,
        rows: IntArray,
        r: Int,
        timeLimitMillis: Long,
        isCancelled: () -> Boolean,
    ): Plan {
        val search = Search(counts, rows, System.nanoTime() + timeLimitMillis * 1_000_000L, isCancelled)
        var moves = IntArray(K)
        var m = 0
        for (e in 1 until K) if (counts[r * K + e] > 0) moves[m++] = e
        if (m == 0) return Plan(-1)
        moves = moves.copyOf(m)
        // výchozí řazení: nejméně možností pro soupeře napřed
        sortByKey(moves, m) { e -> rows[e] - if (e == r) 1 else 0 }
        var bestMove = moves[0]
        var depthReached = 0
        var forcedWin = false
        var forcedLoss = false
        val scores = IntArray(m)
        for (depth in 1..MAX_DEPTH) {
            search.exhaustive = true
            var bestScore = -INF
            var bestCount = 0
            val iterBest = IntArray(m)
            var completed = true
            for (i in 0 until m) {
                val e = moves[i]
                counts[r * K + e]--
                rows[r]--
                // okno (best-1, INF): tahy se stejným skóre jako nejlepší se vyhodnotí přesně
                val alpha = if (bestScore == -INF) -INF else bestScore - 1
                val v = -search.negamax(e, depth - 1, 1, -INF, -alpha)
                counts[r * K + e]++
                rows[r]++
                if (search.aborted) {
                    completed = false
                    break
                }
                scores[i] = v
                if (v > bestScore) {
                    bestScore = v
                    bestCount = 0
                    iterBest[bestCount++] = e
                } else if (v == bestScore) {
                    iterBest[bestCount++] = e
                }
            }
            if (!completed) break
            bestMove = iterBest[random.nextInt(bestCount)]
            depthReached = depth
            // další iterace začne nejlepšími tahy
            sortByKey(moves, m) { e -> -scores[moves.indexOf(e)] }
            if (bestScore >= WIN - 1000) {
                forcedWin = true
                break
            }
            if (bestScore <= -(WIN - 1000)) {
                forcedLoss = true
                break
            }
            if (search.exhaustive) break // celý strom hry byl prohledán
        }
        return Plan(bestMove, depthReached, search.nodes, forcedWin, forcedLoss)
    }

    /** Prohledávání stromu hry nad maticí počtů. */
    private class Search(
        val counts: IntArray,
        val rows: IntArray,
        val deadlineNanos: Long,
        val isCancelled: () -> Boolean,
    ) {
        var nodes = 0L
        var aborted = false

        /** Zůstane true, pokud žádná větev nebyla uříznuta hloubkou (strom je prohledán celý). */
        var exhaustive = true

        private val moveBuffers = Array(MAX_DEPTH + 2) { IntArray(K) }
        private val keyBuffers = Array(MAX_DEPTH + 2) { IntArray(K) }

        fun negamax(r: Int, depth: Int, ply: Int, alpha0: Int, beta: Int): Int {
            nodes++
            if (nodes and 1023L == 0L && (System.nanoTime() > deadlineNanos || isCancelled())) aborted = true
            if (aborted) return 0
            if (rows[r] == 0) return -(WIN - ply)
            if (depth == 0) {
                exhaustive = false
                return evaluate(r)
            }
            val moves = moveBuffers[ply]
            val keys = keyBuffers[ply]
            var m = 0
            for (e in 1 until K) {
                if (counts[r * K + e] > 0) {
                    moves[m] = e
                    keys[m] = rows[e] - if (e == r) 1 else 0
                    m++
                }
            }
            // vložením seřadit: nejvíc omezující tahy napřed (lepší ořezávání)
            for (i in 1 until m) {
                val mv = moves[i]
                val ky = keys[i]
                var j = i - 1
                while (j >= 0 && keys[j] > ky) {
                    moves[j + 1] = moves[j]
                    keys[j + 1] = keys[j]
                    j--
                }
                moves[j + 1] = mv
                keys[j + 1] = ky
            }
            var alpha = alpha0
            var best = -INF
            for (i in 0 until m) {
                val e = moves[i]
                counts[r * K + e]--
                rows[r]--
                val v = -negamax(e, depth - 1, ply + 1, -beta, -alpha)
                counts[r * K + e]++
                rows[r]++
                if (aborted) return 0
                if (v > best) best = v
                if (v > alpha) alpha = v
                if (alpha >= beta) break
            }
            return best
        }

        /** Hodnocení pozice z pohledu hráče na tahu: čím víc možností, tím lépe. */
        private fun evaluate(r: Int): Int {
            val mine = rows[r]
            // nejhorší dostupný tah soupeře pro mě: kolik možností mu nejvíc mohu vzít
            var minOpp = Int.MAX_VALUE
            for (e in 1 until K) {
                if (counts[r * K + e] > 0) {
                    val opp = rows[e] - if (e == r) 1 else 0
                    if (opp < minOpp) minOpp = opp
                }
            }
            if (minOpp == 0) return WIN / 2 // mám okamžitě vyhrávající tah
            return mobility(mine) - mobility(minOpp) / 2
        }
    }

    companion object {
        private const val K = CzechAlphabet.SYMBOLS
        private const val WIN = 1_000_000
        private const val INF = Int.MAX_VALUE / 2
        private const val MAX_DEPTH = 40
        private const val MEDIUM_TEMPERATURE = 1.3
        private const val DANGER_PENALTY = 4.0
        /** Okamžitá výhra je silně preferovaná (vyšší než jakékoli jiné skóre), ale ne vždy jistá. */
        private const val IMMEDIATE_WIN_SCORE = 0.0

        /** Výchozí časový limit výpočtu pro obtížnost (ms). */
        fun defaultTimeLimit(difficulty: Difficulty): Long = when (difficulty) {
            Difficulty.EASY -> 50L
            Difficulty.MEDIUM -> 100L
            Difficulty.HARD -> 1200L
        }

        private fun mobility(n: Int): Int = (ln(1.0 + n) * 1000).toInt()

        private inline fun sortByKey(arr: IntArray, n: Int, key: (Int) -> Int) {
            val keys = IntArray(n) { key(arr[it]) }
            for (i in 1 until n) {
                val v = arr[i]
                val kv = keys[i]
                var j = i - 1
                while (j >= 0 && keys[j] > kv) {
                    arr[j + 1] = arr[j]
                    keys[j + 1] = keys[j]
                    j--
                }
                arr[j + 1] = v
                keys[j + 1] = kv
            }
        }
    }
}
