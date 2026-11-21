package cz.slovnifotbal.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Jednoduchý kontejner závislostí aplikace (ruční DI). */
class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val dataStoreManager: DataStoreManager = DataStoreManager.getInstance(context)

    val dictionaryRepository: DictionaryRepository =
        DictionaryRepository(AssetDictionarySource(context.applicationContext.assets))

    val soundPlayer: SoundPlayer = SoundPlayer(context.applicationContext)

    init {
        appScope.launch {
            dataStoreManager.settingsFlow.collect { soundPlayer.enabled = it.soundEffects }
        }
    }
}
