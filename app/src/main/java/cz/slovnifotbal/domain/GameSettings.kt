package cz.slovnifotbal.domain

/** Obtížnost počítačového soupeře. */
enum class Difficulty(val key: String, val label: String) {
    EASY("easy", "Lehká"),
    MEDIUM("medium", "Střední"),
    HARD("hard", "Těžká");

    companion object {
        fun fromKey(key: String?): Difficulty = entries.firstOrNull { it.key == key } ?: MEDIUM
    }
}

/** Jak se zachází se spřežkou CH. */
enum class ChRule(val key: String, val label: String) {
    /** CH je jedno samostatné písmeno (chata začíná na CH, prach končí na CH). */
    SINGLE_LETTER("single", "Samostatné písmeno CH"),

    /** CH jsou dvě písmena C a H (chata začíná na C, prach končí na H). */
    TWO_LETTERS("two", "Dvě písmena C + H");

    companion object {
        fun fromKey(key: String?): ChRule = entries.firstOrNull { it.key == key } ?: TWO_LETTERS
    }
}

/**
 * Jak se navazuje na koncové písmeno s diakritikou.
 *
 * Diakritika se při kontrole slov ve slovníku rozlišuje vždy (čaj ≠ caj, les ≠ lés).
 * Toto pravidlo určuje jen to, na jaké písmeno se navazuje.
 */
enum class LinkRule(val key: String, val label: String) {
    /** á = a, é/ě = e, í = i, ó = o, ú/ů = u, ý = y, ď = d, ť = t, ň = n. Č, Ř, Š, Ž zůstávají samostatná. */
    RELAXED("relaxed", "Volně (á = a, ě = e, ů = u…)"),

    /** Navazuje se přesně na stejné písmeno včetně čárky, háčku i kroužku. */
    STRICT("strict", "Přesně včetně diakritiky");

    companion object {
        fun fromKey(key: String?): LinkRule = entries.firstOrNull { it.key == key } ?: RELAXED
    }
}

/** Slovní druhy, které lze v nastavení povolit nebo zakázat. Bit odpovídá formátu slovníku. */
enum class PartOfSpeech(val key: String, val label: String, val bit: Int) {
    NOUN("noun", "Podstatná jména", 1),
    ADJECTIVE("adj", "Přídavná jména", 2),
    VERB("verb", "Slovesa", 4),
    ADVERB("adv", "Příslovce", 8),
    OTHER("other", "Ostatní (zájmena, číslovky, předložky…)", 16);

    companion object {
        val ALL: Set<PartOfSpeech> = entries.toSet()

        fun maskOf(set: Set<PartOfSpeech>): Int = set.fold(0) { acc, p -> acc or p.bit }

        fun fromMask(mask: Int): Set<PartOfSpeech> = entries.filter { mask and it.bit != 0 }.toSet()
    }
}

/** Vzhled aplikace. */
enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "Podle systému"),
    LIGHT("light", "Světlý"),
    DARK("dark", "Tmavý");

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/**
 * Pravidla jedné hry. Při zahájení nové hry se zkopírují z aktuálního nastavení
 * a během hry se už nemění.
 */
data class GameRules(
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val properNames: Boolean = false,
    val minWordLength: Int = DEFAULT_MIN_LENGTH,
    val chRule: ChRule = ChRule.TWO_LETTERS,
    val linkRule: LinkRule = LinkRule.RELAXED,
    val partsOfSpeech: Set<PartOfSpeech> = PartOfSpeech.ALL,
    val inflectedForms: Boolean = false,
) {
    val partsOfSpeechMask: Int get() = PartOfSpeech.maskOf(partsOfSpeech)

    /** Vrátí kopii s hodnotami v povoleném rozsahu. */
    fun sanitized(): GameRules = copy(
        minWordLength = minWordLength.coerceIn(MIN_LENGTH_RANGE),
        partsOfSpeech = partsOfSpeech.ifEmpty { PartOfSpeech.ALL },
    )

    companion object {
        const val DEFAULT_MIN_LENGTH = 2
        val MIN_LENGTH_RANGE = 2..8
        const val MAX_WORD_LENGTH = 40
    }
}

/** Kompletní nastavení aplikace (pravidla hry + vzhled a zvuky). */
data class GameSettings(
    val rules: GameRules = GameRules(),
    val soundEffects: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)
