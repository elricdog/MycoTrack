package com.mikotrack.core

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.mikotrack.R

/**
 * The languages of the user interface.
 *
 * The choice is stored by AppCompat: on Android 13 and newer it is the per-app language
 * of the system (also editable from the system settings), and on older versions AppCompat
 * persists it and applies it to every activity.
 */
enum class AppLanguage(val tag: String, @StringRes val labelRes: Int) {
    SYSTEM("", R.string.language_system),
    CATALAN("ca", R.string.language_catalan),
    SPANISH("es", R.string.language_spanish),
    ENGLISH("en", R.string.language_english),
    ;

    companion object {
        /** The language currently selected for the application. */
        fun current(): AppLanguage {
            val locales = AppCompatDelegate.getApplicationLocales()
            if (locales.isEmpty) return SYSTEM
            val language = locales[0]?.language ?: return SYSTEM
            return entries.firstOrNull { it.tag.isNotEmpty() && it.tag == language } ?: SYSTEM
        }

        /** Changes the language of the application; the activities are recreated. */
        fun apply(language: AppLanguage) {
            AppCompatDelegate.setApplicationLocales(
                if (language == SYSTEM) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(language.tag)
                },
            )
        }
    }
}
