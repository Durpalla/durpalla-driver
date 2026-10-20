package com.durpalla.driver.ui.screens

import androidx.compose.ui.res.stringResource
import com.durpalla.driver.ui.str
import com.durpalla.driver.ui.title
import com.durpalla.driver.i18n.l
import com.durpalla.driver.i18n.toLocalDigits
import com.durpalla.driver.i18n.DigitFormatter
import com.durpalla.driver.i18n.asString
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.durpalla.driver.R
import com.durpalla.driver.domain.VehicleType
import com.durpalla.driver.ui.AuthState
import com.durpalla.driver.ui.components.*
import com.durpalla.driver.ui.theme.DP
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onContinue: () -> Unit) {
    LaunchedEffect(Unit) { delay(1500); onContinue() }
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(DP.Primary500, DP.Primary700))).clickable(onClick = onContinue),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(120.dp).clip(RoundedCornerShape(32.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.durpalla_icon), stringResource(R.string.brand_durpalla), Modifier.size(96.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.brand_wordmark), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.splash_tagline), color = DP.Primary200)
        }
        Text(stringResource(R.string.splash_tap), color = DP.Primary200, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp))
    }
}


@Composable
fun KycStepper(step: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (it <= step) DP.Primary500 else DP.Line)) }
    }
}

@Composable
fun KycVehicleScreen(selected: VehicleType, onSelect: (VehicleType) -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    Scaffold(topBar = { DTopBar(stringResource(R.string.kyc_title), onBack) }, bottomBar = { StickyCta { PrimaryButton(stringResource(R.string.cont), onNext) } }) { pad ->
        ScreenColumn(pad) {
            KycStepper(0)
            Text(stringResource(R.string.kyc_what), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.kyc_what_sub), color = DP.Muted)
            VehicleType.entries.forEach { t ->
                DCard(onClick = { onSelect(t) }, border = if (t == selected) DP.Primary500 else DP.Line, color = if (t == selected) DP.Primary100 else Color.White) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VehicleBadge(t)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(t.title(), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(when (t) { VehicleType.SPEEDBOAT -> R.string.vt_desc_speedboat; VehicleType.BOAT -> R.string.vt_desc_boat; VehicleType.TAXI -> R.string.vt_desc_taxi; VehicleType.MICROBUS -> R.string.vt_desc_microbus }), color = DP.Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        RadioButton(selected = t == selected, onClick = { onSelect(t) })
                    }
                }
            }
        }
    }
}

/** API document keys (ASCII) with their translated labels. */
val kycDocs = listOf("nid" to R.string.doc_nid, "licence" to R.string.doc_licence, "registration" to R.string.doc_reg, "fitness" to R.string.doc_fitness, "selfie" to R.string.doc_selfie)

@Composable
fun KycDocumentsScreen(uploaded: Set<String>, onToggle: (String) -> Unit, onBack: () -> Unit, onSubmit: () -> Unit) {
    Scaffold(topBar = { DTopBar(stringResource(R.string.kyc_docs_title), onBack) }, bottomBar = { StickyCta { PrimaryButton(stringResource(R.string.kyc_submit), onSubmit, enabled = uploaded.size >= 3) } }) { pad ->
        ScreenColumn(pad) {
            KycStepper(1)
            Text(stringResource(R.string.kyc_docs_sub), color = DP.Muted)
            kycDocs.forEach { (d, label) ->
                val done = d in uploaded
                DCard(onClick = { onToggle(d) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (done) Icons.Filled.CheckCircle else Icons.Filled.UploadFile, null, tint = if (done) DP.Success else DP.Primary500)
                        Text(stringResource(label), Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.titleSmall)
                        StatusChip(stringResource(if (done) R.string.uploaded else R.string.upload), if (done) Tone.SUCCESS else Tone.PRIMARY)
                    }
                }
            }
        }
    }
}

@Composable
fun KycReviewScreen(onContinue: () -> Unit) {
    Scaffold(bottomBar = { StickyCta { PrimaryButton(stringResource(R.string.kyc_explore), onContinue) } }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            KycStepper(2)
            Spacer(Modifier.height(40.dp))
            Box(Modifier.size(96.dp).clip(RoundedCornerShape(48.dp)).background(DP.WarningSoft), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.HourglassTop, null, tint = DP.Warning, modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.kyc_review_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.kyc_review_sub), color = DP.Muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            val ur = stringResource(R.string.under_review)
            DCard { KeyValue(stringResource(R.string.kyc_nid), ur); KeyValue(stringResource(R.string.kyc_licence), ur); KeyValue(stringResource(R.string.vehicle), ur) }
        }
    }
}

@PhonePreview @Composable private fun SplashPreview() = P { SplashScreen {} }
@PhonePreview @Composable private fun LoginPreview() = P { PhoneLoginScreen(AuthState(phone = "01711234567"), {}, {}) }
@PhonePreview @Composable private fun OtpPreview() = P { OtpScreen("01711234567", null, {}, {}) }
@PhonePreview @Composable private fun KycVehiclePreview() = P { KycVehicleScreen(VehicleType.SPEEDBOAT, {}, {}, {}) }
@PhonePreview @Composable private fun KycDocsPreview() = P { KycDocumentsScreen(setOf(kycDocs[0].first), {}, {}, {}) }
@PhonePreview @Composable private fun KycReviewPreview() = P { KycReviewScreen {} }
