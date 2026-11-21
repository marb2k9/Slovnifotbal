package cz.slovnifotbal.engine

import java.io.File

/** Pomocné slovníky pro testy. */
object TestDictionaries {
    val NOUN = WordMeta.build(basePos = 1, tier = 10)
    val ADJ = WordMeta.build(basePos = 2, tier = 10)
    val VERB = WordMeta.build(basePos = 4, tier = 10)
    val ADV = WordMeta.build(basePos = 8, tier = 10)
    val NOUN_FORM = WordMeta.build(formPos = 1, tier = 10)
    val PROPER = WordMeta.build(properBase = true, tier = 10)
    val PROPER_FORM = WordMeta.build(properForm = true, tier = 10)

    fun nouns(vararg words: String): WordDictionary = WordDictionary.fromEntries(words.associateWith { NOUN })

    fun of(vararg entries: Pair<String, Int>): WordDictionary = WordDictionary.fromEntries(entries.toMap())

    /** Najde soubor ve složce assets bez ohledu na to, odkud se testy spouštějí. */
    fun assetFile(name: String): File {
        val candidates = listOf(
            "src/main/assets/dictionary/$name",
            "app/src/main/assets/dictionary/$name",
            "../app/src/main/assets/dictionary/$name",
        )
        return candidates.map(::File).firstOrNull { it.isFile }
            ?: error("Soubor slovníku $name nebyl nalezen (pracovní adresář: ${File(".").absolutePath}).")
    }

    /** Skutečný slovník aplikace (načte se jednou pro všechny testy). */
    val real: WordDictionary by lazy {
        WordDictionary.load(assetFile("cs_words.txt").readBytes(), assetFile("cs_words.meta").readBytes())
    }
}
