package com.rewordly.app.core.common

import android.content.Context
import android.content.res.Configuration
import com.rewordly.app.domain.model.InterfaceLanguage
import java.util.Locale

/**
 * A context that renders in the chosen interface language.
 *
 * Notification and widget content is built outside a Compose composition, so the stored language has
 * to be applied explicitly instead of relying on the ambient locale.
 */
fun Context.withLocale(language: InterfaceLanguage): Context {
    val config = Configuration(resources.configuration).apply {
        setLocale(Locale.forLanguageTag(language.tag))
    }
    return createConfigurationContext(config)
}
