package com.rewordly.app.core.common

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.rewordly.app.domain.model.InterfaceLanguage

/** Applies the stored interface language as the per-app locale (persisted by the platform/AppCompat). */
object AppLocaleManager {
    fun apply(language: InterfaceLanguage) {
        val desired = LocaleListCompat.forLanguageTags(language.tag)
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != desired.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(desired)
        }
    }
}
