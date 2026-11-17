package com.bsoft.compose.bmusic.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsoft.compose.bmusic.data.preferences.AppSettingsPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Search history for the search screen. */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val preferences: AppSettingsPreferences,
) : ViewModel() {
    val recentSearches: StateFlow<List<String>> = preferences.data
        .map { it.recentSearches }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun remember(query: String) = preferences.addRecentSearch(query)
    fun forget(query: String) = preferences.removeRecentSearch(query)
    fun clearHistory() = preferences.clearRecentSearches()
}
