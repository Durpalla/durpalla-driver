package com.durpalla.driver.i18n

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class LanguageManagerTest {
    private val applied = mutableListOf<AppLanguage>()
    private val applier = LocaleApplier { applied += it }
    @After fun reset() { AppLocale.language = AppLanguage.ENGLISH }

    @Test fun firstRunNeedsPrompt() = runTest {
        val m = LanguageManager(InMemoryLanguageStore(), applier)
        assertTrue(m.needsPrompt())
        assertNull(m.restore())
        assertTrue(applied.isEmpty())
    }

    @Test fun choosePersistsAndApplies() = runTest {
        val store = InMemoryLanguageStore()
        val m = LanguageManager(store, applier)
        m.choose(AppLanguage.BANGLA)
        assertFalse(m.needsPrompt())
        assertEquals(AppLanguage.BANGLA, store.language.first())
        assertEquals(listOf(AppLanguage.BANGLA), applied)
        assertTrue(AppLocale.isBangla)
    }

    @Test fun savedChoiceIsDefaultOnNextLaunch() = runTest {
        val store = InMemoryLanguageStore(AppLanguage.BANGLA)
        val m = LanguageManager(store, applier) { null }
        assertEquals(AppLanguage.BANGLA, m.restore())
        assertEquals(listOf(AppLanguage.BANGLA), applied)
        assertTrue(AppLocale.isBangla)
    }

    @Test fun settingsChangeIsLiveAndSaved() = runTest {
        val store = InMemoryLanguageStore(AppLanguage.BANGLA)
        val m = LanguageManager(store, applier)
        m.restore(); m.choose(AppLanguage.ENGLISH)
        assertEquals(AppLanguage.ENGLISH, store.get())
        assertEquals(AppLanguage.ENGLISH, applied.last())
        assertFalse(AppLocale.isBangla)
    }

    @Test fun android13SystemSettingWinsAndIsSavedBack() = runTest {
        val store = InMemoryLanguageStore(AppLanguage.ENGLISH)
        val m = LanguageManager(store, applier) { AppLanguage.BANGLA }
        assertEquals(AppLanguage.BANGLA, m.restore())
        assertEquals(AppLanguage.BANGLA, store.get())
    }

    @Test fun tagParsing() {
        assertEquals(AppLanguage.BANGLA, AppLanguage.fromTag("bn-BD"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"))
        assertNull(AppLanguage.fromTag("fr"))
        assertNull(AppLanguage.fromTag(null))
    }
}
