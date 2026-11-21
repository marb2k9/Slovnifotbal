package cz.slovnifotbal.domain

/** Jednoznačný stavový model hry. */
enum class GamePhase {
    /** Načítá se slovník. */
    LOADING,

    /** Slovník je načten, hra ještě nezačala (hráč může zadat první slovo). */
    READY,

    /** Na tahu je hráč. */
    PLAYER_TURN,

    /** Počítač hledá odpověď. */
    COMPUTER_THINKING,

    /** Hráč vyhrál. */
    PLAYER_WON,

    /** Hráč prohrál. */
    PLAYER_LOST,

    /** Chyba (např. chybějící nebo poškozený slovník). */
    ERROR;

    val isFinished: Boolean get() = this == PLAYER_WON || this == PLAYER_LOST

    val acceptsPlayerInput: Boolean get() = this == READY || this == PLAYER_TURN
}

enum class Player { HUMAN, COMPUTER }

/** Jeden odehraný tah. */
data class Move(val player: Player, val word: String)

/** Důvod konce hry. */
enum class EndReason(val key: String) {
    /** Počítač nenašel žádné platné slovo. */
    COMPUTER_HAS_NO_WORD("computer_no_word"),

    /** Pro hráče už neexistuje žádné platné slovo. */
    PLAYER_HAS_NO_WORD("player_no_word"),

    /** Hráč se vzdal. */
    PLAYER_SURRENDERED("surrender"),

    /** Hráč ukončil rozehranou hru a začal novou. */
    PLAYER_ABANDONED("abandon");

    companion object {
        fun fromKey(key: String?): EndReason? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Uložitelný stav rozehrané hry. Používá se k obnovení hry po ukončení aplikace.
 * Formát je jednoduchý řádkový text, aby nebyl potřeba žádný serializační framework.
 */
data class GameSnapshot(
    val rules: GameRules,
    val moves: List<Move>,
    val phase: GamePhase,
    val endReason: EndReason? = null,
    val startedAtMillis: Long = 0L,
) {
    fun encode(): String = buildString {
        appendLine(HEADER)
        appendLine("difficulty=${rules.difficulty.key}")
        appendLine("proper=${if (rules.properNames) 1 else 0}")
        appendLine("minlen=${rules.minWordLength}")
        appendLine("ch=${rules.chRule.key}")
        appendLine("link=${rules.linkRule.key}")
        appendLine("pos=${rules.partsOfSpeechMask}")
        appendLine("forms=${if (rules.inflectedForms) 1 else 0}")
        appendLine("phase=${phase.name}")
        appendLine("end=${endReason?.key ?: ""}")
        appendLine("started=$startedAtMillis")
        for (m in moves) {
            append(if (m.player == Player.HUMAN) "H:" else "C:")
            appendLine(m.word)
        }
    }

    companion object {
        private const val HEADER = "SLOVNI_FOTBAL_GAME_V1"

        /** Vrátí null, pokud text není platný uložený stav. */
        fun decode(text: String?): GameSnapshot? {
            if (text.isNullOrBlank()) return null
            val lines = text.lines().filter { it.isNotEmpty() }
            if (lines.firstOrNull() != HEADER) return null
            val values = HashMap<String, String>()
            val moves = ArrayList<Move>()
            for (line in lines.drop(1)) {
                when {
                    line.startsWith("H:") -> moves += Move(Player.HUMAN, line.substring(2))
                    line.startsWith("C:") -> moves += Move(Player.COMPUTER, line.substring(2))
                    '=' in line -> {
                        val i = line.indexOf('=')
                        values[line.substring(0, i)] = line.substring(i + 1)
                    }
                    else -> return null
                }
            }
            if (moves.any { it.word.isEmpty() || it.word.length > GameRules.MAX_WORD_LENGTH }) return null
            val phase = values["phase"]?.let { p -> GamePhase.entries.firstOrNull { it.name == p } } ?: return null
            val rules = GameRules(
                difficulty = Difficulty.fromKey(values["difficulty"]),
                properNames = values["proper"] == "1",
                minWordLength = values["minlen"]?.toIntOrNull() ?: GameRules.DEFAULT_MIN_LENGTH,
                chRule = ChRule.fromKey(values["ch"]),
                linkRule = LinkRule.fromKey(values["link"]),
                partsOfSpeech = PartOfSpeech.fromMask(values["pos"]?.toIntOrNull() ?: 31),
                inflectedForms = values["forms"] == "1",
            ).sanitized()
            return GameSnapshot(
                rules = rules,
                moves = moves,
                phase = phase,
                endReason = EndReason.fromKey(values["end"]),
                startedAtMillis = values["started"]?.toLongOrNull() ?: 0L,
            )
        }
    }
}
