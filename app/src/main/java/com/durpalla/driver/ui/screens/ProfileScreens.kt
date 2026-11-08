package com.durpalla.driver.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.durpalla.driver.R
import com.durpalla.driver.data.KycDocumentViewer
import com.durpalla.driver.data.ServiceLocator
import com.durpalla.driver.domain.*
import com.durpalla.driver.i18n.DigitFormatter
import com.durpalla.driver.i18n.asString
import com.durpalla.driver.i18n.l
import com.durpalla.driver.i18n.toLocalDigits
import com.durpalla.driver.security.promptBiometric
import com.durpalla.driver.ui.components.*
import com.durpalla.driver.ui.plural
import com.durpalla.driver.ui.str
import com.durpalla.driver.ui.theme.DP
import com.durpalla.driver.ui.title
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    d: Driver,
    onVehicles: () -> Unit,
    onRatings: () -> Unit,
    onHistory: () -> Unit,
    onPredefined: () -> Unit,
    onCalendar: () -> Unit,
    onWallet: () -> Unit,
    onSupport: () -> Unit,
    onNotifications: () -> Unit,
    onLogout: () -> Unit,
    onLanguage: () -> Unit = {},
    onServiceArea: () -> Unit = {},
    onEditName: () -> Unit = {},
    onChangePhoto: () -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context as? androidx.fragment.app.FragmentActivity
    val biometric = ServiceLocator.biometricLogin
    val canBiometric = remember { biometric.canAuthenticate() }
    var biometricOn by remember { mutableStateOf(biometric.isEnabled()) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var enablePassword by remember { mutableStateOf("") }
    var enableError by remember { mutableStateOf<String?>(null) }
    var enabling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val promptTitle = stringResource(R.string.biometric_prompt_enable_title)
    val promptSub = stringResource(R.string.biometric_prompt_enable_sub)
    val cancelLabel = stringResource(R.string.cancel)
    val unavailable = stringResource(R.string.biometric_unavailable)

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { if (!enabling) showPasswordDialog = false },
            title = { Text(stringResource(R.string.biometric_enable)) },
            text = {
                Column {
                    Text(stringResource(R.string.biometric_confirm_password), color = DP.Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = enablePassword,
                        onValueChange = { enablePassword = it.take(20); enableError = null },
                        label = { Text(stringResource(R.string.password_label)) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        isError = enableError != null,
                        supportingText = enableError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = enablePassword.length >= 8 && !enabling,
                    onClick = {
                        if (activity == null || !canBiometric) {
                            enableError = unavailable
                            return@TextButton
                        }
                        enabling = true
                        activity.promptBiometric(
                            title = promptTitle,
                            subtitle = promptSub,
                            negativeButton = cancelLabel,
                            onSuccess = {
                                scope.launch {
                                    ServiceLocator.auth.loginWithPassword(d.phone, enablePassword)
                                        .onSuccess {
                                            biometric.enable(d.phone, enablePassword)
                                            biometricOn = true
                                            showPasswordDialog = false
                                            enablePassword = ""
                                        }
                                        .onFailure {
                                            enableError = context.getString(R.string.err_password)
                                        }
                                    enabling = false
                                }
                            },
                            onError = {
                                enableError = it
                                enabling = false
                            },
                        )
                    },
                ) { Text(if (enabling) stringResource(R.string.saving) else stringResource(R.string.biometric_enable)) }
            },
            dismissButton = {
                TextButton(enabled = !enabling, onClick = { showPasswordDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Scaffold(topBar = { DTopBar(stringResource(R.string.profile)) }) { pad ->
        ScreenColumn(pad) {
            DCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(d.displayName, 64.dp, imageUrl = d.avatarUrl, onClick = onChangePhoto)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(d.displayName, style = MaterialTheme.typography.titleLarge)
                        Text(d.phone.toLocalDigits(), color = DP.Muted, fontSize = 13.sp)
                        RatingText(d.rating, str(R.string.trips_verified, plural(R.plurals.trips_count, d.totalTrips)))
                        TextButton(onClick = onEditName) { Text(stringResource(R.string.edit_name)) }
                        TextButton(onClick = onChangePhoto) { Text(stringResource(R.string.change_photo)) }
                    }
                }
            }
            DCard {
                MenuRow(Icons.Filled.DirectionsBoat, stringResource(R.string.vehicles_docs), plural(R.plurals.vehicles_count, d.vehicles.size), onClick = onVehicles)
                MenuRow(Icons.Filled.Place, stringResource(R.string.service_area),
                    if (d.serviceStoppageCount > 0) plural(R.plurals.ghats_count, d.serviceStoppageCount) else stringResource(R.string.service_area_empty),
                    onClick = onServiceArea)
                MenuRow(Icons.Filled.Route, stringResource(R.string.predef_title), onClick = onPredefined)
                MenuRow(Icons.Filled.CalendarMonth, stringResource(R.string.availability_calendar), onClick = onCalendar)
                MenuRow(Icons.Filled.History, stringResource(R.string.trip_history), onClick = onHistory)
                MenuRow(Icons.Filled.Star, stringResource(R.string.ratings), str(R.string.rating_star, num(d.rating)), onClick = onRatings)
            }
            DCard {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = DP.Primary500)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(stringResource(R.string.biometric_profile_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (canBiometric) stringResource(R.string.biometric_profile_sub)
                            else stringResource(R.string.biometric_unavailable),
                            color = DP.Muted,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = biometricOn,
                        enabled = canBiometric,
                        onCheckedChange = { on ->
                            if (on) {
                                enablePassword = ""
                                enableError = null
                                showPasswordDialog = true
                            } else {
                                biometric.disable()
                                biometricOn = false
                            }
                        },
                        colors = brandSwitchColors(),
                    )
                }
                MenuRow(Icons.Filled.AccountBalanceWallet, stringResource(R.string.wallet_payout), onClick = onWallet)
                MenuRow(Icons.Filled.Notifications, stringResource(R.string.notifications), onClick = onNotifications)
                MenuRow(Icons.Filled.Translate, stringResource(R.string.language), stringResource(R.string.language_sub), trailing = currentLanguageName(), onClick = onLanguage)
                MenuRow(Icons.Filled.SupportAgent, stringResource(R.string.help_support), onClick = onSupport)
                MenuRow(Icons.AutoMirrored.Filled.Logout, stringResource(R.string.log_out), onClick = onLogout)
            }
            Text(stringResource(R.string.version_footer), color = DP.Muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
fun VehiclesScreen(d: Driver, onBack: () -> Unit, onReupload: () -> Unit = {}, onAdd: () -> Unit = onReupload) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var opening by remember { mutableStateOf(false) }
    if (preview != null) {
        Dialog(onDismissRequest = { preview = null }) {
            DCard {
                Image(preview!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp))
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { preview = null }) { Text(stringResource(R.string.dismiss)) }
            }
        }
    }
    Scaffold(topBar = { DTopBar(stringResource(R.string.vehicles_docs), onBack) }) { pad ->
        ScreenColumn(pad) {
            if (d.needsKycResubmit) {
                DCard(color = DP.DangerSoft) {
                    Text(d.kycNote.ifBlank { stringResource(R.string.kyc_rejected_banner) }, color = DP.Danger, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(stringResource(R.string.reupload_documents), onReupload, accent = true)
                }
            }
            SectionHeader(stringResource(R.string.vehicles), stringResource(R.string.add), onAction = onAdd)
            if (d.vehicles.isEmpty()) {
                EmptyState(stringResource(R.string.vehicles_empty))
                PrimaryButton(stringResource(R.string.add_vehicle), onAdd, accent = true)
            }
            d.vehicles.forEach { v ->
                DCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VehicleBadge(v.type)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(v.name, style = MaterialTheme.typography.titleSmall); Text(str(R.string.dot_join3, v.type.title(), v.reg, plural(R.plurals.seats_count, v.seats)), color = DP.Muted, fontSize = 12.sp) }
                        StatusChip(stringResource(if (v.verified) R.string.verified else R.string.pending), if (v.verified) Tone.SUCCESS else Tone.WARNING)
                    }
                }
            }
            SectionHeader(stringResource(R.string.documents))
            DCard {
                d.documents.forEach { doc ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Description, null, tint = DP.Primary500)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(doc.name.toLocalDigits(), style = MaterialTheme.typography.bodyMedium)
                            if (doc.note.isNotBlank()) Text(doc.note, color = DP.Danger, fontSize = 11.sp)
                        }
                        StatusChip(
                            doc.status.title(),
                            when (doc.status) {
                                DocStatus.VERIFIED -> Tone.SUCCESS
                                DocStatus.UNDER_REVIEW -> Tone.WARNING
                                DocStatus.EXPIRING -> Tone.ACCENT
                                DocStatus.REJECTED, DocStatus.MISSING -> Tone.DANGER
                            },
                        )
                        val viewPath = doc.viewPath
                        if (!viewPath.isNullOrBlank() && doc.status != DocStatus.MISSING) {
                            TextButton(
                                onClick = {
                                    if (opening) return@TextButton
                                    opening = true
                                    scope.launch {
                                        KycDocumentViewer.open(context, ServiceLocator.api, viewPath)
                                            .onSuccess { if (it.bitmap != null) preview = it.bitmap }
                                            .onFailure {
                                                Toast.makeText(context, context.getString(R.string.doc_open_failed), Toast.LENGTH_SHORT).show()
                                            }
                                        opening = false
                                    }
                                },
                                enabled = !opening,
                            ) { Text(stringResource(R.string.view_document)) }
                        }
                    }
                }
            }
            if (!d.kycComplete && !d.needsKycResubmit) {
                PrimaryButton(stringResource(R.string.reupload_documents), onReupload)
            }
        }
    }
}

@Composable
fun NotificationsScreen(list: List<AppNotification>, onBack: () -> Unit) {
    Scaffold(topBar = { DTopBar(stringResource(R.string.notifications), onBack) }) { pad ->
        ScreenColumn(pad) {
            list.forEach { n ->
                DCard(color = if (n.unread) DP.Primary100 else Color.White) {
                    Row { Text(n.title.asString().toLocalDigits(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)); Text(n.time.toLocalDigits(), color = DP.Muted, fontSize = 11.sp) }
                    Text(n.body.asString().toLocalDigits(), color = DP.Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun SupportScreen(onBack: () -> Unit) {
    val faqs = listOf(
        stringResource(R.string.faq_q1) to stringResource(R.string.faq_a1),
        stringResource(R.string.faq_q2) to stringResource(R.string.faq_a2),
        stringResource(R.string.faq_q3) to stringResource(R.string.faq_a3),
        stringResource(R.string.faq_q4) to str(R.string.faq_a4, Commission.RATE_PERCENT),
    )
    Scaffold(topBar = { DTopBar(stringResource(R.string.help_support), onBack) }) { pad ->
        ScreenColumn(pad) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(stringResource(R.string.call_support), {}, Modifier.weight(1f)); SecondaryButton(stringResource(R.string.live_chat), {}, Modifier.weight(1f))
            }
            SectionHeader(stringResource(R.string.faq))
            faqs.forEach { (q, a) ->
                var open by remember { mutableStateOf(false) }
                DCard(onClick = { open = !open }) {
                    Text(q, fontWeight = FontWeight.SemiBold)
                    if (open) { Spacer(Modifier.height(6.dp)); Text(a, color = DP.Muted) }
                }
            }
            SecondaryButton(stringResource(R.string.report_problem), {})
        }
    }
}

@PhonePreview @Composable private fun SupportPreview() = P { SupportScreen {} }

@Composable
private fun currentLanguageName(): String = stringResource(if (com.durpalla.driver.i18n.AppLocale.isBangla) R.string.lang_bangla else R.string.lang_english)
