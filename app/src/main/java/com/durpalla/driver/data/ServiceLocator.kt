package com.durpalla.driver.data

import com.durpalla.driver.BuildConfig
import com.durpalla.driver.data.api.ApiClient
import com.durpalla.driver.data.session.DataStoreTokenStore
import com.durpalla.driver.data.session.SessionStore
import com.durpalla.driver.domain.SlotLockPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual DI. Everything talks to the real Durpalla apigw driver API ([BuildConfig.API_BASE_URL]). */
object ServiceLocator {
    /** Set in DriverApplication before anything else is touched. */
    lateinit var appContext: android.content.Context
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val slotPolicy = SlotLockPolicy(bufferMinutes = 30)
    val session: SessionStore by lazy { SessionStore(DataStoreTokenStore(appContext), appScope) }
    val api: ApiClient by lazy { ApiClient(BuildConfig.API_BASE_URL, { session.token }, session::expire) }

    val auth: ApiAuthRepository by lazy { ApiAuthRepository(api, session, ::readImage) }

    /** KYC photo: read + downscale to ≤ 1600 px JPEG so uploads stay small. */
    private fun readImage(uri: String): Pair<ByteArray, String>? = runCatching {
        val r = appContext.contentResolver
        val u = android.net.Uri.parse(uri)
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        r.openInputStream(u)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        val bmp = r.openInputStream(u)?.use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
        out.toByteArray() to "image/jpeg"
    }.getOrNull()
    val trips: TripRepository by lazy { ApiTripRepository(api) }
    val requests: RequestRepository by lazy { ApiRequestRepository(api, trips, slotPolicy) }
    val earnings: EarningsRepository by lazy { ApiEarningsRepository(api, trips) }
    val profile: ProfileRepository by lazy { ApiProfileRepository(api, trips) }

    /** Pusher private channels authorised at `{API}/realtime/auth`; without PUSHER_KEY realtime is off (pull-to-refresh only). */
    val realtime: com.durpalla.driver.realtime.RealtimeClient by lazy {
        if (BuildConfig.PUSHER_KEY.isNotBlank())
            com.durpalla.driver.realtime.PusherRealtimeClient(BuildConfig.PUSHER_KEY, BuildConfig.PUSHER_CLUSTER, BuildConfig.PUSHER_AUTH_URL)
        else com.durpalla.driver.realtime.DisabledRealtimeClient()
    }

    var firebaseConfigured = false
    val callApi: com.durpalla.driver.call.CallApi by lazy { com.durpalla.driver.call.HttpCallApi(BuildConfig.API_BASE_URL, { session.token }) }
    /** Agora/WebRTC router (server picks per call). Without Firebase, calling is unavailable (calls fail with a message). */
    val calls: com.durpalla.driver.call.CallProvider by lazy {
        if (firebaseConfigured) {
            val signaling = com.durpalla.driver.call.FirestoreSignaling(customToken = { auth.firebaseToken() })
            com.durpalla.driver.call.CallProviderRouter(appScope, callApi, mapOf(
                com.durpalla.driver.call.CallProviderKind.AGORA to com.durpalla.driver.call.AgoraCallProvider(appContext, appScope, callApi, signaling),
                com.durpalla.driver.call.CallProviderKind.WEBRTC to com.durpalla.driver.call.WebRtcCallProvider(appContext, appScope, callApi, signaling),
            ))
        } else com.durpalla.driver.call.UnavailableCallProvider(callApi, appScope)
    }
    /** Set when the user tapped Accept on the incoming-call notification. */
    val acceptFromNotification = kotlinx.coroutines.flow.MutableStateFlow(false)
    var botProtection: com.durpalla.driver.security.BotProtection = com.durpalla.driver.security.NoopBotProtection
    /** Replaced with the DataStore + AppCompat implementation in DriverApplication. */
    var language: com.durpalla.driver.i18n.LanguageManager =
        com.durpalla.driver.i18n.LanguageManager(com.durpalla.driver.i18n.InMemoryLanguageStore(), { })
}
