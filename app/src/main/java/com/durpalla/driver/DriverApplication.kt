package com.durpalla.driver

import android.app.Application
import android.util.Log
import com.durpalla.driver.data.ServiceLocator
import com.durpalla.driver.notifications.Channels
import com.durpalla.driver.security.RecaptchaBotProtection
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.durpalla.driver.i18n.AppCompatLocaleApplier
import com.durpalla.driver.i18n.DataStoreLanguageStore
import com.durpalla.driver.i18n.LanguageManager

class DriverApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Saved language (DataStore) is the default on every launch; a change made in Android 13+ system settings wins.
        val lang = LanguageManager(DataStoreLanguageStore(this), AppCompatLocaleApplier, LanguageManager::appCompatSelected)
        ServiceLocator.language = lang
        runBlocking { lang.restore() }
        ServiceLocator.appContext = this
        // Restore the stored token before the first screen decides between login and home.
        kotlinx.coroutines.runBlocking { ServiceLocator.session.restore() }
        Channels.create(this)
        com.durpalla.driver.call.CallNotifications.createChannels(this)
        ServiceLocator.botProtection = RecaptchaBotProtection.create(this, BuildConfig.RECAPTCHA_SITE_KEY)
        // Firebase is only initialised when google-services.json was present at build time.
        if (FirebaseApp.getApps(this).isNotEmpty() || FirebaseApp.initializeApp(this) != null) {
            ServiceLocator.firebaseConfigured = true
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
            FirebaseMessaging.getInstance().token.addOnSuccessListener { t ->
                CoroutineScope(Dispatchers.IO).launch { ServiceLocator.auth.registerDeviceToken(t) }
            }.addOnFailureListener { Log.w("Durpalla", "FCM token unavailable", it) }
        } else Log.i("Durpalla", "Firebase not configured (no google-services.json); push disabled, realtime via ${ServiceLocator.realtime::class.simpleName}")
        startCallSync()
    }

    /** Foreground service + CallStyle notification follow the call state. */
    private fun startCallSync() {
        CoroutineScope(Dispatchers.Main).launch { ServiceLocator.calls.state.collect { com.durpalla.driver.call.CallService.sync(this@DriverApplication, it) } }
    }
}
