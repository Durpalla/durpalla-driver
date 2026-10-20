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

class DriverApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Channels.create(this)
        ServiceLocator.botProtection = RecaptchaBotProtection.create(this, BuildConfig.RECAPTCHA_SITE_KEY)
        // Firebase is only initialised when google-services.json was present at build time.
        if (FirebaseApp.getApps(this).isNotEmpty() || FirebaseApp.initializeApp(this) != null) {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
            FirebaseMessaging.getInstance().token.addOnSuccessListener { t ->
                CoroutineScope(Dispatchers.IO).launch { ServiceLocator.auth.registerDeviceToken(t) }
            }.addOnFailureListener { Log.w("Durpalla", "FCM token unavailable", it) }
        } else Log.i("Durpalla", "Firebase not configured (no google-services.json); push disabled, realtime via ${ServiceLocator.realtime::class.simpleName}")
    }
}
