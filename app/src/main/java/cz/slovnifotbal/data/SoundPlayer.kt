package cz.slovnifotbal.data

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import cz.slovnifotbal.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Zvukové efekty hry. */
enum class SoundEffect { WORD_ACCEPTED, ERROR, COMPUTER_WORD, WIN, LOSE }

/** Rozhraní pro přehrávání zvuků (v testech se nahrazuje tichou implementací). */
interface SoundEffects {
    fun play(effect: SoundEffect)
}

object NoSound : SoundEffects {
    override fun play(effect: SoundEffect) = Unit
}

/**
 * Krátké zvukové efekty přes SoundPool (soubory v res/raw, generované přímo pro tuto hru).
 * Načítání i přehrávání běží na vlastním vlákně, aby nikdy nezdrželo hlavní vlákno.
 */
class SoundPlayer(context: Context) : SoundEffects {

    /** Zvuky lze vypnout v nastavení. */
    @Volatile
    var enabled: Boolean = true

    private val appContext = context.applicationContext

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "slovni-fotbal-zvuky").apply { isDaemon = true }
    }

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    /** Id načtených zvuků; zapisuje a čte se jen na vlákně [executor]. */
    private val ids = HashMap<SoundEffect, Int>()

    init {
        executor.execute {
            runCatching {
                ids[SoundEffect.WORD_ACCEPTED] = pool.load(appContext, R.raw.sfx_word_ok, 1)
                ids[SoundEffect.ERROR] = pool.load(appContext, R.raw.sfx_error, 1)
                ids[SoundEffect.COMPUTER_WORD] = pool.load(appContext, R.raw.sfx_computer, 1)
                ids[SoundEffect.WIN] = pool.load(appContext, R.raw.sfx_win, 1)
                ids[SoundEffect.LOSE] = pool.load(appContext, R.raw.sfx_lose, 1)
            }
        }
    }

    override fun play(effect: SoundEffect) {
        if (!enabled) return
        executor.execute {
            val id = ids[effect] ?: return@execute
            // play() vrací 0, pokud zvuk ještě není načtený – pak se zvuk jen přeskočí
            runCatching { pool.play(id, 0.8f, 0.8f, 1, 0, 1f) }
        }
    }
}
