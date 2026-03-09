package my.ssdid.wallet.platform.i18n

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LocalizationManager {
    val supportedLocales = listOf("en", "ms", "zh")
    val localeNames = mapOf(
        "en" to "English",
        "ms" to "Bahasa Melayu",
        "zh" to "\u4E2D\u6587"
    )

    fun setLocale(languageTag: String) {
        val locales = LocaleListCompat.forLanguageTags(languageTag)
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun getCurrentLocale(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) "en" else locales[0]?.language ?: "en"
    }
}
