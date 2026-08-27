package com.nendo.argosy.core.locale

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import java.util.Locale

/**
 * Applies the launcher's stored language choice to a [Context], independent of the framework
 * per-app language API (API 33+) so that every Android version from minSdk up gets a working
 * override. There is no androidx.appcompat in this app's dependency graph and every activity is
 * a plain `ComponentActivity`, so `AppCompatDelegate`'s locale backport (which only fires from
 * `AppCompatActivity.attachBaseContext2`) would silently do nothing here.
 */
object LocaleHelper {

    const val SYSTEM_LANGUAGE_TAG = "system"

    /**
     * Wraps [base] with [languageTag] applied, or returns [base] unchanged for the "system"
     * sentinel. Every call also repoints `Locale.setDefault` so code that formats dates or
     * numbers off the JVM default (rather than through a `Context`) stays consistent with the
     * chosen language, and so switching back to "system" mid-process does not leave a stale
     * override behind.
     */
    fun wrap(base: Context, languageTag: String): Context {
        val locale = resolveLocale(languageTag)
        if (locale == null) {
            Locale.setDefault(systemLocale())
            return base
        }
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration)
        val localeList = LocaleList(locale)
        LocaleList.setDefault(localeList)
        configuration.setLocales(localeList)
        return base.createConfigurationContext(configuration)
    }

    /**
     * Null for the "system" sentinel, meaning follow the device and override nothing.
     */
    fun resolveLocale(languageTag: String): Locale? =
        if (languageTag == SYSTEM_LANGUAGE_TAG) null else Locale.forLanguageTag(languageTag)

    fun effectiveLocale(context: Context): Locale = context.resources.configuration.locales[0]

    private fun systemLocale(): Locale = Resources.getSystem().configuration.locales[0]
}
