package com.bsoft.compose.bmusic.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.bsoft.compose.bmusic.R
import com.bsoft.compose.bmusic.utils.LocaleHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.intellij.lang.annotations.Language
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LanguageViewModel @Inject constructor(application: Application) : AndroidViewModel(application) {
    val supportedLanguages = listOf(
        "" to application.getString(R.string.language_system_default),
       "en" to "English",
        "es" to "Español",
        "fr" to "Français",
        "ja" to "日本語",
        "zh" to "中文"
    )

    private val _selectedTag = MutableStateFlow(
        LocaleHelper.getCurrentLanguageTag(application) ?: ""
    )
    val selectedTag: StateFlow<String> = _selectedTag.asStateFlow()

    fun changeLanguage(tag: String?) {
        LocaleHelper.setAppLocale(getApplication(), tag)
        _selectedTag.value = tag ?: ""
    }
}