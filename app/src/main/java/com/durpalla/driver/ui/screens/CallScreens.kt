package com.durpalla.driver.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.durpalla.driver.R
import com.durpalla.driver.call.CallLogEntry
import com.durpalla.driver.call.CallRole
import com.durpalla.driver.call.CallState
import com.durpalla.driver.call.CallStatus
import com.durpalla.driver.call.EndReason
import com.durpalla.driver.i18n.toLocalDigits
import com.durpalla.driver.ui.components.*
import com.durpalla.driver.ui.str
import com.durpalla.driver.ui.theme.DP
import kotlinx.coroutines.delay
import java.util.Locale

/** mm:ss in the app's digits. */
fun callDuration(sec: Int): String = String.format(Locale.ROOT, "%02d:%02d", sec / 60, sec % 60).toLocalDigits()

/** Runs [action] once RECORD_AUDIO is granted (asks if needed); [onDenied] otherwise. */
@Composable
fun rememberWithMic(onDenied: () -> Unit): (() -> Unit) -> Unit {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) pending?.invoke() else onDenied(); pending = null }
    return { action ->
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) action()
        else { pending = action; launcher.launch(Manifest.permission.RECORD_AUDIO) }
    }
}

/** Full-screen call UI, drawn over the nav host while a call exists. */
@Composable
fun CallScreen(
    s: CallState, onAccept: () -> Unit, onDecline: () -> Unit, onHangUp: () -> Unit,
    onMute: (Boolean) -> Unit, onSpeaker: (Boolean) -> Unit, onDismiss: () -> Unit,
) {
    if (s is CallState.Idle) return
    if (s is CallState.Ended) LaunchedEffect(s) { delay(2_500); onDismiss() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(s) { while (s is CallState.Active) { now = System.currentTimeMillis(); delay(1_000) } }
    val name = when (s) { is CallState.Outgoing -> s.peerName; is CallState.Incoming -> s.call.callerName; is CallState.Connecting -> s.peerName; is CallState.Active -> s.peerName; is CallState.Ended -> s.peerName; else -> "" }
        .ifBlank { stringResource(R.string.call_customer) }
    val status = when (s) {
        is CallState.Outgoing -> stringResource(R.string.call_calling)
        is CallState.Incoming -> stringResource(R.string.call_incoming)
        is CallState.Connecting -> stringResource(R.string.call_connecting)
        is CallState.Active -> callDuration(((now - s.startedAt) / 1000).toInt().coerceAtLeast(0))
        is CallState.Ended -> s.error?.let { stringResource(it.res) } ?: when (s.reason) {
            EndReason.DECLINED -> stringResource(R.string.call_declined); EndReason.MISSED -> stringResource(R.string.call_no_answer)
            EndReason.BUSY -> stringResource(R.string.call_err_busy); EndReason.FAILED -> stringResource(R.string.call_err_generic)
            else -> if (s.durationSec > 0) str(R.string.call_ended_after, callDuration(s.durationSec)) else stringResource(R.string.call_ended)
        }
        else -> ""
    }
    Surface(Modifier.fillMaxSize(), color = DP.Primary700) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(64.dp))
            Avatar(name, 96.dp)
            Spacer(Modifier.height(16.dp))
            Text(name, style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text(status, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = .8f), modifier = Modifier.padding(top = 4.dp))
            if (s is CallState.Active && s.weak) Text(stringResource(R.string.call_weak), color = DP.Accent, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.weight(1f))
            when (s) {
                is CallState.Incoming -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundAction(Icons.Filled.CallEnd, stringResource(R.string.call_decline), DP.Danger, onDecline)
                    RoundAction(Icons.Filled.Call, stringResource(R.string.call_accept), DP.Success, onAccept)
                }
                is CallState.Active -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        RoundAction(if (s.muted) Icons.Filled.MicOff else Icons.Filled.Mic, stringResource(if (s.muted) R.string.call_unmute else R.string.call_mute), Color.White.copy(alpha = .2f), { onMute(!s.muted) })
                        RoundAction(Icons.Filled.VolumeUp, stringResource(if (s.speaker) R.string.call_speaker_off else R.string.call_speaker), if (s.speaker) DP.Accent else Color.White.copy(alpha = .2f), { onSpeaker(!s.speaker) })
                    }
                    Spacer(Modifier.height(24.dp))
                    RoundAction(Icons.Filled.CallEnd, stringResource(R.string.call_end), DP.Danger, onHangUp)
                }
                is CallState.Outgoing, is CallState.Connecting -> RoundAction(Icons.Filled.CallEnd, stringResource(R.string.call_end), DP.Danger, onHangUp)
                is CallState.Ended -> SecondaryButton(stringResource(R.string.call_close), onDismiss)
                else -> Unit
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, bg: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(onClick, Modifier.size(72.dp).semantics { contentDescription = label }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = bg, contentColor = Color.White)) { Icon(icon, null, Modifier.size(32.dp)) }
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Trip detail: calls on this trip (trip.callLog). */
@Composable
fun CallLogCard(entries: List<CallLogEntry>) {
    if (entries.isEmpty()) return
    DCard {
        Text(stringResource(R.string.call_log), style = MaterialTheme.typography.titleSmall)
        entries.forEach { e ->
            val dir = stringResource(if (e.callerRole == CallRole.DRIVER) R.string.call_outgoing else R.string.call_incoming_short)
            val what = when (e.status) {
                CallStatus.MISSED -> stringResource(R.string.call_missed); CallStatus.DECLINED -> stringResource(R.string.call_declined)
                else -> if (e.durationSec > 0) callDuration(e.durationSec) else stringResource(R.string.call_no_answer)
            }
            KeyValue(dir, what, if (e.status == CallStatus.MISSED) DP.Danger else DP.Ink)
        }
    }
}
