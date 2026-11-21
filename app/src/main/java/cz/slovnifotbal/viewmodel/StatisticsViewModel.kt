package cz.slovnifotbal.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.slovnifotbal.data.GameStorage
import cz.slovnifotbal.domain.GameStatistics
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Statistiky odehraných her. */
class StatisticsViewModel(private val store: GameStorage) : ViewModel() {

    val statistics: StateFlow<GameStatistics> = store.statisticsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GameStatistics())

    fun reset() {
        viewModelScope.launch { store.resetStatistics() }
    }
}
