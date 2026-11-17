package com.bsoft.compose.bmusic.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsoft.compose.bmusic.data.repositories.ListeningStats
import com.bsoft.compose.bmusic.data.repositories.ListeningStatsRepository
import com.bsoft.compose.bmusic.data.repositories.StatsPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.MONTH,
    val stats: ListeningStats = ListeningStats(),
    val loading: Boolean = true,
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val repository: ListeningStatsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    init {
        load(StatsPeriod.MONTH)
    }

    fun load(period: StatsPeriod) {
        _state.value = _state.value.copy(period = period, loading = true)
        viewModelScope.launch {
            val stats = repository.stats(period)
            // Ignore a slow result for a period the user has already left.
            if (_state.value.period == period) {
                _state.value = StatsUiState(period = period, stats = stats, loading = false)
            }
        }
    }
}
