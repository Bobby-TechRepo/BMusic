package com.bsoft.compose.bmusic.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Per-app language. AppCompatDelegate uses the system LocaleManager on Android 13+ and its own
 * backport (stored by AppLocalesMetadataHolderService, declared in the manifest) on older
 * versions, so one code path works everywhere. Requires an AppCompatActivity host.
 */
object LocaleHelper {
    /** [languageTag] null or empty means "follow the system language". */
    fun setAppLocale(@Suppress("UNUSED_PARAMETER") context: Context, languageTag: String?) {
        val locales = if (languageTag.isNullOrEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /** The active in-app language tag, or null when following the system. */
    fun getCurrentLanguageTag(@Suppress("UNUSED_PARAMETER") context: Context): String? =
        AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag()
}
