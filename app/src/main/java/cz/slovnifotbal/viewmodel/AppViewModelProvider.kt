package cz.slovnifotbal.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cz.slovnifotbal.SlovniFotbalApplication
import cz.slovnifotbal.data.AppContainer

/** Továrna ViewModelů s ruční injekcí závislostí z [AppContainer]. */
object AppViewModelProvider {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val c = container()
            GameViewModel(c.dictionaryRepository, c.dataStoreManager, c.soundPlayer)
        }
        initializer { SettingsViewModel(container().dataStoreManager) }
        initializer { StatisticsViewModel(container().dataStoreManager) }
    }

    private fun CreationExtras.container(): AppContainer =
        (this[APPLICATION_KEY] as SlovniFotbalApplication).container
}
