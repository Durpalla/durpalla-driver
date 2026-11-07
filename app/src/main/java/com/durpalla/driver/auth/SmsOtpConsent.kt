package com.durpalla.driver.auth

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status

/**
 * Android SMS User Consent: when an OTP SMS arrives, the system may show a one-tap
 * share sheet; we extract the 6-digit code (same flow as the customer Flutter app).
 *
 * [restartKey] — bump (e.g. on resend) to listen for the next message.
 */
@Composable
fun SmsOtpUserConsent(restartKey: Int = 0, onCode: (String) -> Unit) {
    val context = LocalContext.current
    val activity = context.findComponentActivity() ?: return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val message = result.data?.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE)
        SmsOtpParser.codeFromText(message)?.let(onCode)
    }

    DisposableEffect(restartKey, activity) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (SmsRetriever.SMS_RETRIEVED_ACTION != intent.action) return
                val extras = intent.extras ?: return
                val status = if (Build.VERSION.SDK_INT >= 33) {
                    extras.getParcelable(SmsRetriever.EXTRA_STATUS, Status::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    extras.getParcelable(SmsRetriever.EXTRA_STATUS) as? Status
                } ?: return
                when (status.statusCode) {
                    CommonStatusCodes.SUCCESS -> {
                        val consent = if (Build.VERSION.SDK_INT >= 33) {
                            extras.getParcelable(SmsRetriever.EXTRA_CONSENT_INTENT, Intent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            extras.getParcelable(SmsRetriever.EXTRA_CONSENT_INTENT)
                        } ?: return
                        try {
                            launcher.launch(consent)
                        } catch (_: Exception) {
                            // Sheet already open / activity finishing — ignore.
                        }
                    }
                    CommonStatusCodes.TIMEOUT -> Unit
                }
            }
        }
        val filter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
        if (Build.VERSION.SDK_INT >= 33) {
            activity.registerReceiver(receiver, filter, SmsRetriever.SEND_PERMISSION, null, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            activity.registerReceiver(receiver, filter, SmsRetriever.SEND_PERMISSION, null)
        }
        SmsRetriever.getClient(activity).startSmsUserConsent(null)
        onDispose {
            try {
                activity.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}
