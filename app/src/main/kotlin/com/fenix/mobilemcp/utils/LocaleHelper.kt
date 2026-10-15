package com.fenix.mobilemcp.utils

import android.content.Context
import android.os.Build
import java.util.Locale

/** Idioma de la app: sistema (defecto), español o inglés. Sin dependencias. */
object LocaleHelper {
    private const val PREFS = "fenix_locale"
    private const val KEY = "lang"

    fun current(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""

    fun set(context: Context, lang: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, lang).apply()
    }

    fun wrap(base: Context): Context {
        val lang = current(base)
        if (lang.isBlank()) return base
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val res = base.resources
        val config = android.content.res.Configuration(res.configuration)
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            base.createConfigurationContext(config)
        } else {
            res.updateConfiguration(config, res.displayMetrics)
            base
        }
    }
}
