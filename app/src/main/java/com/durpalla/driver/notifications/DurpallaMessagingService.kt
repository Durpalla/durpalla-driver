package com.durpalla.driver.notifications

import com.durpalla.driver.data.ServiceLocator
import com.durpalla.driver.realtime.EventParser
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * FCM entry point. Backend sends DATA messages: {event, payload(JSON), route?, title?, body?}.
 * Events are fed into the realtime stream (so open screens update) and shown as a notification.
 */
class DurpallaMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { ServiceLocator.auth.registerDeviceToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val event = EventParser.parseFcm(data)
        event?.let { ServiceLocator.realtime.inject(it) }
        val spec = event?.let(NotificationFactory::spec)
            ?: message.notification?.let { NotificationSpec(data["channel"] ?: Channels.GENERAL, it.title.orEmpty(), it.body.orEmpty(), data["route"] ?: "home") }
            ?: data["title"]?.let { NotificationSpec(data["channel"] ?: Channels.GENERAL, it, data["body"].orEmpty(), data["route"] ?: "home") }
        spec?.let { Notifier.show(this, it.copy(route = data["route"] ?: it.route)) }
    }
}
