package cz.slovnifotbal.data

import android.content.res.AssetManager
import cz.slovnifotbal.engine.DictionaryException
import cz.slovnifotbal.engine.WordDictionary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException

/** Zdroj dat slovníku (v aplikaci soubory v assets, v testech libovolná data). */
interface DictionarySource {
    /** Obsah souboru se slovy (UTF-8, jedno slovo na řádek). */
    @Throws(IOException::class)
    fun readWords(): ByteArray

    /** Obsah souboru s metadaty nebo null, pokud chybí. */
    fun readMeta(): ByteArray?
}

/** Slovník zabudovaný v APK (app/src/main/assets/dictionary). */
class AssetDictionarySource(private val assets: AssetManager) : DictionarySource {
    override fun readWords(): ByteArray = assets.open(WORDS_PATH).use { it.readBytes() }

    override fun readMeta(): ByteArray? = try {
        assets.open(META_PATH).use { it.readBytes() }
    } catch (_: IOException) {
        null
    }

    companion object {
        const val WORDS_PATH = "dictionary/cs_words.txt"
        const val META_PATH = "dictionary/cs_words.meta"
    }
}

/**
 * Načte slovník jednou za běh aplikace (mimo hlavní vlákno) a dál ho drží v paměti.
 * Při každém tahu se už slovník znovu nenačítá.
 */
class DictionaryRepository(
    private val source: DictionarySource,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()

    @Volatile
    private var cached: WordDictionary? = null

    val isLoaded: Boolean get() = cached != null

    /**
     * @throws DictionaryException pokud slovník chybí, je prázdný nebo poškozený.
     */
    suspend fun load(onProgress: (Float) -> Unit = {}): WordDictionary {
        cached?.let { return it }
        return mutex.withLock {
            cached ?: withContext(dispatcher) {
                val words = try {
                    source.readWords()
                } catch (e: FileNotFoundException) {
                    throw DictionaryException("Soubor se slovníkem v aplikaci chybí. Nainstaluj aplikaci znovu.", e)
                } catch (e: IOException) {
                    throw DictionaryException("Slovník se nepodařilo načíst: ${e.message}", e)
                }
                val meta = source.readMeta()
                WordDictionary.load(words, meta, onProgress)
            }.also { cached = it }
        }
    }
}
