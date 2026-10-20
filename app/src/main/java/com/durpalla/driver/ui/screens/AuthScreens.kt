package com.durpalla.driver.ui.screens

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
                Image(painterResource(R.drawable.durpalla_icon), "Durpalla", Modifier.size(96.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text("durpalla", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text("Driver · Boat · Taxi · Micro", color = DP.Primary200)
        }
        Text("Tap to continue · Durpalla Limited", color = DP.Primary200, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp))
    }
}

@Composable
fun PhoneLoginScreen(state: AuthState, onPhone: (String) -> Unit, onSend: () -> Unit) {
    Scaffold(bottomBar = { StickyCta { PrimaryButton(if (state.loading) "Sending…" else "Send OTP", onSend, enabled = !state.loading) } }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(24.dp))
            DurpallaLogo(Modifier.height(40.dp))
            Text("Welcome, driver", style = MaterialTheme.typography.headlineMedium)
            Text("Log in with your mobile number to start receiving trip requests.", color = DP.Muted)
            OutlinedTextField(
                value = state.phone, onValueChange = onPhone, modifier = Modifier.fillMaxWidth(),
                prefix = { Text("🇧🇩 +88  ") }, placeholder = { Text("01XXXXXXXXX") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), isError = state.error != null,
                supportingText = { state.error?.let { Text(it) } }, shape = RoundedCornerShape(14.dp),
            )
            Text("By continuing you agree to Durpalla's Driver Terms & Privacy Policy.", style = MaterialTheme.typography.bodySmall, color = DP.Muted)
        }
    }
}

@Composable
fun OtpScreen(phone: String, error: String?, onBack: () -> Unit, onVerify: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    var seconds by remember { mutableIntStateOf(30) }
    LaunchedEffect(seconds) { if (seconds > 0) { delay(1000); seconds-- } }
    Scaffold(topBar = { DTopBar("", onBack) }, bottomBar = { StickyCta { PrimaryButton("Verify", { onVerify(code) }, enabled = code.length == 6) } }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Enter verification code", style = MaterialTheme.typography.headlineSmall)
            Text("We sent a 6-digit code to +88 $phone", color = DP.Muted)
            OutlinedTextField(
                value = code, onValueChange = { code = it.filter(Char::isDigit).take(6) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(fontSize = 28.sp, letterSpacing = 12.sp, textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), shape = RoundedCornerShape(14.dp),
                isError = error != null, supportingText = { error?.let { Text(it) } },
            )
            Text(if (seconds > 0) "Resend code in 0:${seconds.toString().padStart(2, '0')}" else "Resend code", color = if (seconds > 0) DP.Muted else DP.Primary500,
                modifier = Modifier.clickable(enabled = seconds == 0) { seconds = 30 })
        }
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
    Scaffold(topBar = { DTopBar("Driver verification", onBack) }, bottomBar = { StickyCta { PrimaryButton("Continue", onNext) } }) { pad ->
        ScreenColumn(pad) {
            KycStepper(0)
            Text("What do you drive?", style = MaterialTheme.typography.titleLarge)
            Text("You'll only get requests for this vehicle type. You can add more vehicles later.", color = DP.Muted)
            VehicleType.entries.forEach { t ->
                DCard(onClick = { onSelect(t) }, border = if (t == selected) DP.Primary500 else DP.Line, color = if (t == selected) DP.Primary100 else Color.White) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VehicleBadge(t)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(t.label, style = MaterialTheme.typography.titleMedium)
                            Text(when (t) { VehicleType.SPEEDBOAT -> "River routes · 6–12 seats"; VehicleType.BOAT -> "Trawler / engine boat · up to 30"; VehicleType.TAXI -> "Sedan · up to 4"; VehicleType.MICROBUS -> "Hiace / Noah · 7–11 seats" }, color = DP.Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        RadioButton(selected = t == selected, onClick = { onSelect(t) })
                    }
                }
            }
        }
    }
}

val kycDocs = listOf("National ID (front & back)", "Driving / Master licence", "Vehicle registration", "Fitness / route permit", "Selfie with NID")

@Composable
fun KycDocumentsScreen(uploaded: Set<String>, onToggle: (String) -> Unit, onBack: () -> Unit, onSubmit: () -> Unit) {
    Scaffold(topBar = { DTopBar("Upload documents", onBack) }, bottomBar = { StickyCta { PrimaryButton("Submit for review", onSubmit, enabled = uploaded.size >= 3) } }) { pad ->
        ScreenColumn(pad) {
            KycStepper(1)
            Text("Clear photos, all corners visible. Reviewed within 24 hours.", color = DP.Muted)
            kycDocs.forEach { d ->
                val done = d in uploaded
                DCard(onClick = { onToggle(d) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (done) Icons.Filled.CheckCircle else Icons.Filled.UploadFile, null, tint = if (done) DP.Success else DP.Primary500)
                        Text(d, Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.titleSmall)
                        StatusChip(if (done) "Uploaded" else "Upload", if (done) Tone.SUCCESS else Tone.PRIMARY)
                    }
                }
            }
        }
    }
}

@Composable
fun KycReviewScreen(onContinue: () -> Unit) {
    Scaffold(bottomBar = { StickyCta { PrimaryButton("Explore the app (demo)", onContinue) } }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            KycStepper(2)
            Spacer(Modifier.height(40.dp))
            Box(Modifier.size(96.dp).clip(RoundedCornerShape(48.dp)).background(DP.WarningSoft), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.HourglassTop, null, tint = DP.Warning, modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("Documents under review", style = MaterialTheme.typography.headlineSmall)
            Text("We'll notify you by SMS once your account is verified, usually within 24 hours.", color = DP.Muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            DCard { KeyValue("NID", "Under review"); KeyValue("Licence", "Under review"); KeyValue("Vehicle", "Under review") }
        }
    }
}

@PhonePreview @Composable private fun SplashPreview() = P { SplashScreen {} }
@PhonePreview @Composable private fun LoginPreview() = P { PhoneLoginScreen(AuthState(phone = "01711234567"), {}, {}) }
@PhonePreview @Composable private fun OtpPreview() = P { OtpScreen("01711234567", null, {}, {}) }
@PhonePreview @Composable private fun KycVehiclePreview() = P { KycVehicleScreen(VehicleType.SPEEDBOAT, {}, {}, {}) }
@PhonePreview @Composable private fun KycDocsPreview() = P { KycDocumentsScreen(setOf(kycDocs[0]), {}, {}, {}) }
@PhonePreview @Composable private fun KycReviewPreview() = P { KycReviewScreen {} }
