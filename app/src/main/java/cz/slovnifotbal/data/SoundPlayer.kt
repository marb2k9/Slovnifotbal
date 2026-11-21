package cz.slovnifotbal.data

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import cz.slovnifotbal.R

/** Zvukové efekty hry. */
enum class SoundEffect { WORD_ACCEPTED, ERROR, COMPUTER_WORD, WIN, LOSE }

/** Rozhraní pro přehrávání zvuků (v testech se nahrazuje tichou implementací). */
interface SoundEffects {
    fun play(effect: SoundEffect)
}

object NoSound : SoundEffects {
    override fun play(effect: SoundEffect) = Unit
}

/** Krátké zvukové efekty přes SoundPool (soubory v res/raw, generované přímo pro tuto hru). */
class SoundPlayer(context: Context) : SoundEffects {

    /** Zvuky lze vypnout v nastavení. */
    @Volatile
    var enabled: Boolean = true

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val ids: Map<SoundEffect, Int> = mapOf(
        SoundEffect.WORD_ACCEPTED to pool.load(context, R.raw.sfx_word_ok, 1),
        SoundEffect.ERROR to pool.load(context, R.raw.sfx_error, 1),
        SoundEffect.COMPUTER_WORD to pool.load(context, R.raw.sfx_computer, 1),
        SoundEffect.WIN to pool.load(context, R.raw.sfx_win, 1),
        SoundEffect.LOSE to pool.load(context, R.raw.sfx_lose, 1),
    )

    override fun play(effect: SoundEffect) {
        if (!enabled) return
        val id = ids[effect] ?: return
        // play() vrací 0, pokud zvuk ještě není načtený – pak se zvuk jen přeskočí
        pool.play(id, 0.8f, 0.8f, 1, 0, 1f)
    }
}
