package cz.slovnifotbal.engine

import cz.slovnifotbal.domain.GameRules

/** Výsledek kontroly, zda je slovo ze slovníku povolené aktuálními pravidly. */
enum class Availability {
    ALLOWED,

    /** Slovo je jen vlastní jméno a vlastní jména jsou vypnutá. */
    PROPER_NAMES_DISABLED,

    /** Slovo je jen skloňovaný/časovaný tvar a tvary jsou vypnuté. */
    FORMS_DISABLED,

    /** Slovní druh slova je v nastavení vypnutý. */
    PART_OF_SPEECH_DISABLED,
}

/** Rozhoduje podle metadat slovníku, zda slovo odpovídá pravidlům hry. */
class WordFilter(rules: GameRules) {
    private val posMask = rules.partsOfSpeechMask
    private val forms = rules.inflectedForms
    private val proper = rules.properNames

    fun isAllowed(meta: Int): Boolean {
        if (WordMeta.basePos(meta) and posMask != 0) return true
        if (forms && WordMeta.formPos(meta) and posMask != 0) return true
        if (proper && (WordMeta.isProperBase(meta) || (forms && WordMeta.isProperForm(meta)))) return true
        return false
    }

    fun availability(meta: Int): Availability {
        if (isAllowed(meta)) return Availability.ALLOWED
        val basePos = WordMeta.basePos(meta)
        val formPos = WordMeta.formPos(meta)
        if (basePos == 0 && formPos == 0) {
            // jen vlastní jméno
            return if (!proper) Availability.PROPER_NAMES_DISABLED else Availability.FORMS_DISABLED
        }
        if (!forms && basePos and posMask == 0 && formPos and posMask != 0) return Availability.FORMS_DISABLED
        if (!forms && basePos == 0) return Availability.FORMS_DISABLED
        return Availability.PART_OF_SPEECH_DISABLED
    }
}
