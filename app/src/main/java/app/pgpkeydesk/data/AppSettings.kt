package app.pgpkeydesk.data

import androidx.core.content.edit
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

enum class ThemeMode(val nightMode: Int) {
    SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
    DARK(AppCompatDelegate.MODE_NIGHT_YES)
}

/** Languages offered in the app (empty tag = follow the system language). */
val SUPPORTED_LANGUAGES = listOf("en", "de")

object AppSettings {
    private const val PREFS = "pgpkeydesk"
    private const val KEY_THEME = "theme"

    fun theme(context: Context): ThemeMode =
        runCatching { ThemeMode.valueOf(context.getSharedPreferences(PREFS, 0).getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)

    /** Applies the stored theme; call before the activity is created. */
    fun applyTheme(context: Context) = AppCompatDelegate.setDefaultNightMode(theme(context).nightMode)

    fun setTheme(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS, 0).edit { putString(KEY_THEME, mode.name) }
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }

    /** "" = system, otherwise a language tag from [SUPPORTED_LANGUAGES]. */
    fun language(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')

    fun setLanguage(tag: String) =
        AppCompatDelegate.setApplicationLocales(if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag))
}
