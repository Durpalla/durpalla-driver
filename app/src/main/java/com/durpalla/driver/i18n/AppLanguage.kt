package com.durpalla.driver.i18n

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Locale

enum class AppLanguage(val tag: String) {
    BANGLA("bn"), ENGLISH("en");
    val locale: Locale get() = Locale.forLanguageTag(tag)
    companion object { fun fromTag(t: String?): AppLanguage? = entries.firstOrNull { it.tag == t?.substringBefore('-')?.lowercase() } }
}

/** Process-wide current display language. Snapshot state, so Compose recomposes when it changes. */
object AppLocale {
    var language by mutableStateOf(AppLanguage.ENGLISH)
    val isBangla: Boolean get() = language == AppLanguage.BANGLA
    val locale: Locale get() = language.locale
}

/** Persists the chosen language. null = never chosen (show the first-run prompt). */
interface LanguageStore {
    val language: Flow<AppLanguage?>
    suspend fun get(): AppLanguage? = language.first()
    suspend fun set(lang: AppLanguage)
}

private val Context.langDataStore: DataStore<Preferences> by preferencesDataStore(name = "durpalla_settings")

class DataStoreLanguageStore(private val ctx: Context) : LanguageStore {
    private val key = stringPreferencesKey("app_language")
    override val language: Flow<AppLanguage?> = ctx.applicationContext.langDataStore.data.map { AppLanguage.fromTag(it[key]) }
    override suspend fun set(lang: AppLanguage) { ctx.applicationContext.langDataStore.edit { it[key] = lang.tag } }
}

class InMemoryLanguageStore(initial: AppLanguage? = null) : LanguageStore {
    private val s = MutableStateFlow(initial)
    override val language: Flow<AppLanguage?> = s
    override suspend fun set(lang: AppLanguage) { s.value = lang }
}

/** Applies a language through the AndroidX per-app language API (Android 13+ system settings stay in sync). */
fun interface LocaleApplier { fun apply(lang: AppLanguage) }

object AppCompatLocaleApplier : LocaleApplier {
    override fun apply(lang: AppLanguage) {
        val wanted = LocaleListCompat.forLanguageTags(lang.tag)
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != wanted.toLanguageTags()) AppCompatDelegate.setApplicationLocales(wanted)
    }
}

/**
 * Language logic, independent of Android so it can be unit-tested:
 *  - [needsPrompt] true until the driver picks a language once.
 *  - [restore] at launch: the saved choice is the default; if the user changed it in Android 13+ system settings
 *    (AppCompat stores it via autoStoreLocales) that wins and is saved back.
 *  - [choose] saves + applies (first-run prompt and Settings > Language).
 */
class LanguageManager(private val store: LanguageStore, private val applier: LocaleApplier, private val systemSelected: () -> AppLanguage? = { null }) {
    suspend fun needsPrompt(): Boolean = store.get() == null

    suspend fun restore(): AppLanguage? {
        val saved = store.get()
        val sys = systemSelected()
        val effective = when {
            saved == null -> null
            sys != null && sys != saved -> sys.also { store.set(it) }
            else -> saved
        }
        effective?.let { AppLocale.language = it; applier.apply(it) }
        return effective
    }

    suspend fun choose(lang: AppLanguage) {
        store.set(lang)
        AppLocale.language = lang
        applier.apply(lang)
    }

    companion object {
        fun appCompatSelected(): AppLanguage? = AppCompatDelegate.getApplicationLocales().takeIf { !it.isEmpty }?.get(0)?.let { AppLanguage.fromTag(it.language) }
    }
}
