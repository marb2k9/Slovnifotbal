package cz.slovnifotbal.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.slovnifotbal.data.DataStoreManager
import cz.slovnifotbal.domain.ChRule
import cz.slovnifotbal.domain.Difficulty
import cz.slovnifotbal.domain.GameRules
import cz.slovnifotbal.domain.GameSettings
import cz.slovnifotbal.domain.LinkRule
import cz.slovnifotbal.domain.PartOfSpeech
import cz.slovnifotbal.domain.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Nastavení hry – vše se okamžitě ukládá do DataStore. */
class SettingsViewModel(private val store: DataStoreManager) : ViewModel() {

    val settings: StateFlow<GameSettings> = store.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GameSettings())

    private fun updateRules(transform: (GameRules) -> GameRules) {
        viewModelScope.launch { store.updateSettings { it.copy(rules = transform(it.rules)) } }
    }

    fun setDifficulty(value: Difficulty): Unit = updateRules { it.copy(difficulty = value) }

    fun setProperNames(value: Boolean): Unit = updateRules { it.copy(properNames = value) }

    fun setInflectedForms(value: Boolean): Unit = updateRules { it.copy(inflectedForms = value) }

    fun setMinWordLength(value: Int): Unit = updateRules { it.copy(minWordLength = value.coerceIn(GameRules.MIN_LENGTH_RANGE)) }

    fun setChRule(value: ChRule): Unit = updateRules { it.copy(chRule = value) }

    fun setLinkRule(value: LinkRule): Unit = updateRules { it.copy(linkRule = value) }

    /** Přepne slovní druh; alespoň jeden musí zůstat povolený. */
    fun togglePartOfSpeech(pos: PartOfSpeech): Unit = updateRules {
        val updated = if (pos in it.partsOfSpeech) it.partsOfSpeech - pos else it.partsOfSpeech + pos
        if (updated.isEmpty()) it else it.copy(partsOfSpeech = updated)
    }

    fun setSoundEffects(value: Boolean) {
        viewModelScope.launch { store.updateSettings { it.copy(soundEffects = value) } }
    }

    fun setThemeMode(value: ThemeMode) {
        viewModelScope.launch { store.updateSettings { it.copy(themeMode = value) } }
    }

    fun resetToDefaults() {
        viewModelScope.launch { store.resetSettings() }
    }
}
