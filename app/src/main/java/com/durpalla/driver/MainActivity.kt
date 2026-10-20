package com.durpalla.driver

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.durpalla.driver.data.ServiceLocator
import com.durpalla.driver.i18n.AppLanguage
import com.durpalla.driver.i18n.AppLocale
import com.durpalla.driver.notifications.Channels
import com.durpalla.driver.notifications.DeepLink
import com.durpalla.driver.notifications.NotificationFactory
import com.durpalla.driver.notifications.Notifier
import com.durpalla.driver.ui.nav.DriverApp
import com.durpalla.driver.ui.theme.DurpallaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** AppCompatActivity so AppCompatDelegate.setApplicationLocales() also works on Android 12 and below. */
class MainActivity : AppCompatActivity() {
    private val deepLink = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The resources now carry the applied per-app locale (also when changed from Android 13+ system settings).
        AppLanguage.fromTag(resources.configuration.locales[0].language)?.let { AppLocale.language = it }
        enableEdgeToEdge()
        handle(intent)
        // Realtime events arriving while the app is in the background become system notifications.
        lifecycleScope.launch {
            ServiceLocator.realtime.events.collect { e ->
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) NotificationFactory.spec(e)?.let { Notifier.show(this@MainActivity, it) }
            }
        }
        setContent {
            DurpallaTheme {
                DriverApp(deepLink = deepLink, onDeepLinkHandled = { deepLink.value = null }, onLanguageApplied = { Channels.create(applicationContext) })
            }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handle(intent) }

    private fun handle(i: Intent?) { i?.getStringExtra(DeepLink.EXTRA)?.let { deepLink.value = it } }
}
