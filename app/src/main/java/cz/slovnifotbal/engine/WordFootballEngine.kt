package cz.slovnifotbal.engine

import cz.slovnifotbal.domain.EndReason
import cz.slovnifotbal.domain.GamePhase
import cz.slovnifotbal.domain.GameRules
import cz.slovnifotbal.domain.GameSnapshot
import cz.slovnifotbal.domain.LinkRule
import cz.slovnifotbal.domain.Move
import cz.slovnifotbal.domain.Player
import kotlin.random.Random

/** Druh chyby při zadání slova. */
enum class MoveError {
    EMPTY,
    MULTIPLE_WORDS,
    DIGITS,
    INVALID_CHARACTERS,
    FOREIGN_LETTER,
    TOO_LONG,
    TOO_SHORT,
    WRONG_START,
    NOT_IN_DICTIONARY,
    PROPER_NAMES_DISABLED,
    FORMS_DISABLED,
    PART_OF_SPEECH_DISABLED,
    ALREADY_USED,
    NOT_PLAYER_TURN,
    GAME_OVER,
}

sealed interface ValidationResult {
    data class Valid(val word: String, val index: Int) : ValidationResult
    data class Invalid(val error: MoveError, val message: String) : ValidationResult
}

sealed interface PlayerMoveResult {
    data class Rejected(val error: MoveError, val message: String) : PlayerMoveResult

    /** Tah byl přijat. [computerHasAnswer] = false znamená, že hráč vyhrál. */
    data class Accepted(val move: Move, val computerHasAnswer: Boolean) : PlayerMoveResult
}

sealed interface ComputerMoveResult {
    /** Počítač zahrál. [playerHasAnswer] = false znamená, že hráč prohrál. */
    data class Played(val move: Move, val decision: ComputerPlayer.Decision, val playerHasAnswer: Boolean) : ComputerMoveResult

    /** Počítač nenašel slovo – hráč vyhrál. */
    data object NoWord : ComputerMoveResult

    /** Výpočet byl přerušen (např. restart hry). Stav hry se nezměnil. */
    data object Cancelled : ComputerMoveResult

    /** Počítač není na tahu. */
    data object NotComputerTurn : ComputerMoveResult
}

/**
 * Herní logika slovního fotbalu: normalizace a kontrola slov, návaznost písmen (včetně pravidla CH
 * a diakritiky), použitá slova, tahy počítače, vyhodnocení výhry a prohry a historie hry.
 *
 * Třída nemá žádnou závislost na Androidu, takže je plně testovatelná jednotkovými testy.
 * Není vláknově bezpečná – volající (ViewModel) zajišťuje, že se volá postupně.
 */
class WordFootballEngine(
    private val dictionary: WordDictionary,
    rules: GameRules = GameRules(),
    private val computer: ComputerPlayer = ComputerPlayer(),
    private val random: Random = Random.Default,
) {
    var rules: GameRules = rules.sanitized()
        private set

    private var pool: WordPool = WordPool(dictionary, this.rules, random)
    private var filter = WordFilter(this.rules)
    private val history = ArrayList<Move>()

    /** Stav hry. */
    var phase: GamePhase = GamePhase.READY
        private set

    var endReason: EndReason? = null
        private set

    /** Třída písmene, na které musí začínat další slovo (0 = libovolné písmeno, tj. první tah). */
    var requiredClass: Int = 0
        private set

    /** Čas zahájení hry (nastavuje volající, ukládá se se stavem hry). */
    var startedAtMillis: Long = 0L

    val moves: List<Move> get() = history.toList()

    val moveCount: Int get() = history.size

    /** Počet slov povolených pravidly této hry. */
    val playableWordCount: Int get() = pool.totalWords

    /** Zahájí novou hru (volitelně s novými pravidly). */
    fun restart(newRules: GameRules = rules) {
        rules = newRules.sanitized()
        pool = WordPool(dictionary, rules, random)
        filter = WordFilter(rules)
        history.clear()
        phase = GamePhase.READY
        endReason = null
        requiredClass = 0
    }

    /** Požadované písmeno pro zobrazení, např. „R“, „I / Í“ nebo „CH“; null u prvního tahu. */
    fun requiredLetterText(): String? =
        if (requiredClass == 0) null else CzechAlphabet.describeClass(requiredClass, rules.linkRule)

    /** Hlavní písmeno požadované třídy (pro velké zobrazení), např. „R“. */
    fun requiredLetterPrimary(): String? =
        if (requiredClass == 0) null else CzechAlphabet.displayName(requiredClass)

    /** Počet nepoužitých slov, kterými může hráč teď pokračovat. */
    fun availableWordsForPlayer(): Int =
        if (requiredClass == 0) pool.totalWords - history.size else pool.availableFrom(requiredClass)

    fun playerHasValidWord(): Boolean = availableWordsForPlayer() > 0

    fun isWordUsed(word: String): Boolean {
        val idx = dictionary.indexOf(CzechAlphabet.normalize(word))
        return idx >= 0 && pool.isUsed(idx)
    }

    /** Zkontroluje slovo hráče bez provedení tahu. */
    fun validate(raw: String): ValidationResult {
        if (phase.isFinished) return invalid(MoveError.GAME_OVER, "Hra skončila. Začni novou hru.")
        if (phase == GamePhase.COMPUTER_THINKING) return invalid(MoveError.NOT_PLAYER_TURN, "Počítač přemýšlí.")
        if (!phase.acceptsPlayerInput) return invalid(MoveError.NOT_PLAYER_TURN, "Teď nejsi na tahu.")
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return invalid(MoveError.EMPTY, "Zadej nějaké slovo.")
        val word = CzechAlphabet.normalize(trimmed)
        if (word.length > GameRules.MAX_WORD_LENGTH) {
            return invalid(MoveError.TOO_LONG, "Slovo je příliš dlouhé (nejvýše ${GameRules.MAX_WORD_LENGTH} znaků).")
        }
        if (word.any { it.isWhitespace() }) return invalid(MoveError.MULTIPLE_WORDS, "Zadej jen jedno slovo – bez mezer.")
        if (word.any { it.isDigit() }) return invalid(MoveError.DIGITS, "Slovo nesmí obsahovat číslice.")
        word.firstOrNull { !it.isLetter() }?.let {
            return invalid(MoveError.INVALID_CHARACTERS, "Slovo smí obsahovat jen písmena – bez interpunkce a speciálních znaků.")
        }
        word.firstOrNull { !CzechAlphabet.isCzechLetter(it) }?.let {
            return invalid(MoveError.FOREIGN_LETTER, "Písmeno „$it“ do české abecedy nepatří.")
        }
        val codes = CzechAlphabet.encode(word)
            ?: return invalid(MoveError.INVALID_CHARACTERS, "Slovo smí obsahovat jen česká písmena.")
        val letters = CzechAlphabet.letterCount(codes, 0, codes.size, rules.chRule)
        if (letters < rules.minWordLength) {
            return invalid(MoveError.TOO_SHORT, "Slovo je příliš krátké (alespoň ${lettersText(rules.minWordLength)}).")
        }
        if (requiredClass != 0) {
            val first = CzechAlphabet.linkClass(CzechAlphabet.firstSymbol(codes, 0, codes.size, rules.chRule), rules.linkRule)
            if (first != requiredClass) {
                return invalid(MoveError.WRONG_START, "Slovo musí začínat na písmeno ${requiredLetterSentence()}.")
            }
        }
        val index = dictionary.indexOf(codes)
        if (index < 0) return invalid(MoveError.NOT_IN_DICTIONARY, "Toto slovo není v českém slovníku.")
        when (filter.availability(dictionary.meta(index))) {
            Availability.ALLOWED -> Unit
            Availability.PROPER_NAMES_DISABLED -> return invalid(
                MoveError.PROPER_NAMES_DISABLED,
                "Vlastní jména jsou vypnutá. Zapnout je můžeš v Nastavení.",
            )
            Availability.FORMS_DISABLED -> return invalid(
                MoveError.FORMS_DISABLED,
                "Skloňované a časované tvary jsou vypnuté – zkus základní tvar slova.",
            )
            Availability.PART_OF_SPEECH_DISABLED -> return invalid(
                MoveError.PART_OF_SPEECH_DISABLED,
                "Tento slovní druh je v Nastavení vypnutý.",
            )
        }
        if (pool.isUsed(index)) return invalid(MoveError.ALREADY_USED, "Toto slovo už bylo použito.")
        if (!pool.isEligible(index)) return invalid(MoveError.NOT_IN_DICTIONARY, "Toto slovo není v českém slovníku.")
        return ValidationResult.Valid(word, index)
    }

    /** Zkontroluje a provede tah hráče. Neplatný tah nic nemění – hráč může zkusit jiné slovo. */
    fun playPlayerWord(raw: String): PlayerMoveResult {
        return when (val v = validate(raw)) {
            is ValidationResult.Invalid -> PlayerMoveResult.Rejected(v.error, v.message)
            is ValidationResult.Valid -> {
                val move = Move(Player.HUMAN, v.word)
                apply(move, v.index)
                if (pool.availableFrom(requiredClass) == 0) {
                    phase = GamePhase.PLAYER_WON
                    endReason = EndReason.COMPUTER_HAS_NO_WORD
                    PlayerMoveResult.Accepted(move, computerHasAnswer = false)
                } else {
                    phase = GamePhase.COMPUTER_THINKING
                    PlayerMoveResult.Accepted(move, computerHasAnswer = true)
                }
            }
        }
    }

    /**
     * Najde a provede tah počítače. Výpočet běží v aktuálním vlákně – volající ho spouští mimo UI vlákno.
     * Při zrušení ([isCancelled]) se stav hry nezmění.
     */
    fun playComputerMove(
        timeLimitMillis: Long = ComputerPlayer.defaultTimeLimit(rules.difficulty),
        isCancelled: () -> Boolean = { false },
    ): ComputerMoveResult {
        if (phase != GamePhase.COMPUTER_THINKING) return ComputerMoveResult.NotComputerTurn
        val decision = computer.chooseMove(pool, requiredClass, rules.difficulty, timeLimitMillis, isCancelled)
        if (isCancelled()) return ComputerMoveResult.Cancelled
        if (decision == null || !isLegalComputerWord(decision.wordIndex)) {
            phase = GamePhase.PLAYER_WON
            endReason = EndReason.COMPUTER_HAS_NO_WORD
            return ComputerMoveResult.NoWord
        }
        val move = Move(Player.COMPUTER, dictionary.wordAt(decision.wordIndex))
        apply(move, decision.wordIndex)
        return if (pool.availableFrom(requiredClass) == 0) {
            phase = GamePhase.PLAYER_LOST
            endReason = EndReason.PLAYER_HAS_NO_WORD
            ComputerMoveResult.Played(move, decision, playerHasAnswer = false)
        } else {
            phase = GamePhase.PLAYER_TURN
            ComputerMoveResult.Played(move, decision, playerHasAnswer = true)
        }
    }

    private fun isLegalComputerWord(index: Int): Boolean =
        index >= 0 && pool.isAvailable(index) && pool.startClassOf(index) == requiredClass

    /** Hráč se vzdává. */
    fun surrender(): Boolean {
        if (phase.isFinished) return false
        phase = GamePhase.PLAYER_LOST
        endReason = EndReason.PLAYER_SURRENDERED
        return true
    }

    /** Hráč ukončil rozehranou hru (počítá se jako prohra). */
    fun abandon(): Boolean {
        if (phase.isFinished) return false
        phase = GamePhase.PLAYER_LOST
        endReason = EndReason.PLAYER_ABANDONED
        return true
    }

    /** Nápověda: náhodné platné nepoužité slovo pro hráče (preferuje běžná slova). */
    fun hint(): String? {
        if (!phase.acceptsPlayerInput) return null
        val idx = if (requiredClass == 0) {
            var found = -1
            repeat(50) {
                val from = 1 + random.nextInt(CzechAlphabet.SYMBOLS - 1)
                if (found < 0 && pool.availableFrom(from) > 0) found = pool.randomWordFrom(from, random, 0.05f)
            }
            found
        } else {
            pool.randomWordFrom(requiredClass, random, 0.15f)
        }
        return if (idx >= 0) dictionary.wordAt(idx) else null
    }

    /** Uložitelný stav hry. */
    fun snapshot(): GameSnapshot = GameSnapshot(
        rules = rules,
        moves = history.toList(),
        phase = phase,
        endReason = endReason,
        startedAtMillis = startedAtMillis,
    )

    private fun apply(move: Move, index: Int) {
        history += move
        pool.markUsed(index)
        requiredClass = pool.endClassOf(index)
    }

    private fun requiredLetterSentence(): String {
        val names = CzechAlphabet.symbolsOfClass(requiredClass, rules.linkRule).map { CzechAlphabet.displayName(it) }
        return when (names.size) {
            0 -> "?"
            1 -> names[0]
            else -> names.dropLast(1).joinToString(", ") + " nebo " + names.last()
        }
    }

    private fun invalid(error: MoveError, message: String) = ValidationResult.Invalid(error, message)

    companion object {
        /** „2 písmena“, „5 písmen“. */
        fun lettersText(n: Int): String = when {
            n == 1 -> "1 písmeno"
            n in 2..4 -> "$n písmena"
            else -> "$n písmen"
        }

        /**
         * Obnoví hru z uloženého stavu. Vrací null, pokud uložený stav neodpovídá slovníku
         * nebo pravidlům (pak se začne nová hra).
         */
        fun restore(
            dictionary: WordDictionary,
            snapshot: GameSnapshot,
            computer: ComputerPlayer = ComputerPlayer(),
            random: Random = Random.Default,
        ): WordFootballEngine? {
            val engine = WordFootballEngine(dictionary, snapshot.rules, computer, random)
            engine.startedAtMillis = snapshot.startedAtMillis
            snapshot.moves.forEachIndexed { i, move ->
                val expected = if (i % 2 == 0) Player.HUMAN else Player.COMPUTER
                if (move.player != expected) return null
                val idx = dictionary.indexOf(move.word)
                if (idx < 0 || !engine.pool.isAvailable(idx)) return null
                if (engine.requiredClass != 0 && engine.pool.startClassOf(idx) != engine.requiredClass) return null
                engine.apply(move, idx)
            }
            val last = snapshot.moves.lastOrNull()
            when {
                snapshot.phase.isFinished -> {
                    engine.phase = snapshot.phase
                    engine.endReason = snapshot.endReason
                }
                last == null -> engine.phase = GamePhase.READY
                last.player == Player.HUMAN -> if (engine.pool.availableFrom(engine.requiredClass) == 0) {
                    engine.phase = GamePhase.PLAYER_WON
                    engine.endReason = EndReason.COMPUTER_HAS_NO_WORD
                } else {
                    engine.phase = GamePhase.COMPUTER_THINKING
                }
                else -> if (engine.pool.availableFrom(engine.requiredClass) == 0) {
                    engine.phase = GamePhase.PLAYER_LOST
                    engine.endReason = EndReason.PLAYER_HAS_NO_WORD
                } else {
                    engine.phase = GamePhase.PLAYER_TURN
                }
            }
            return engine
        }

        /** Popis třídy písmene pro zobrazení podle pravidla navazování. */
        fun describe(linkClass: Int, rule: LinkRule): String = CzechAlphabet.describeClass(linkClass, rule)
    }
}
