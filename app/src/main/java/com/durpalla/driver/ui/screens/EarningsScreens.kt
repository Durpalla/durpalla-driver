package com.durpalla.driver.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.durpalla.driver.data.SampleData
import com.durpalla.driver.domain.*
import com.durpalla.driver.ui.WithdrawState
import com.durpalla.driver.ui.components.*
import com.durpalla.driver.ui.theme.DP
import kotlinx.coroutines.launch

@Composable
fun BarChart(data: List<Pair<String, Int>>, modifier: Modifier = Modifier) {
    val max = (data.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val slot = size.width / data.size
            val bw = slot * 0.55f
            data.forEachIndexed { i, (_, v) ->
                val h = size.height * v / max
                drawRoundRect(
                    color = if (i == data.lastIndex) DP.Accent else DP.Primary500,
                    topLeft = Offset(i * slot + (slot - bw) / 2, size.height - h), size = Size(bw, h), cornerRadius = CornerRadius(8f, 8f),
                )
            }
        }
        Row { data.forEach { Text(it.first, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = DP.Muted) } }
    }
}

@Composable
fun EarningsScreen(s: EarningsSummary, onWithdraw: () -> Unit, onWallet: () -> Unit, onRecon: () -> Unit, onRatings: () -> Unit, rating: Double) {
    var range by rememberSaveable { mutableIntStateOf(1) }
    Scaffold(topBar = { DTopBar("Earnings") }) { pad ->
        ScreenColumn(pad) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(DP.Primary500, DP.Primary700))).padding(16.dp)) {
                Text("WALLET BALANCE", color = DP.Primary200, fontSize = 11.sp)
                Text(taka(s.walletBalance), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Row { Text("Pending settlement ${taka(s.pendingSettlement)}", color = DP.Primary200, fontSize = 12.sp, modifier = Modifier.weight(1f)); Text("Next payout ${s.nextPayout}", color = DP.Primary200, fontSize = 12.sp) }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Withdraw", onWithdraw, Modifier.weight(1f), accent = true)
                    Button(onClick = onWallet, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f))) { Text("Transactions") }
                }
            }
            SegmentedTabs(listOf("Day", "Week", "Month"), range, { range = it })
            DCard {
                Row { Text("This week", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); Text("${taka(s.weekTotal)} ▲${s.weekChangePct}%", color = DP.Success, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(12.dp)); BarChart(s.daily)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Trips", "${s.weekTrips}", Modifier.weight(1f))
                StatTile("Commission", taka(s.weekCommission), Modifier.weight(1f), DP.Danger)
                Box(Modifier.weight(1f)) { StatTile("Rating", "$rating ★", valueColor = DP.Accent) }
            }
            DCard {
                MenuRow(Icons.Filled.ReceiptLong, "Reconciliation & statements", "Trips vs payouts · commission", onClick = onRecon)
                MenuRow(Icons.Filled.Star, "Ratings & reviews", onClick = onRatings)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WithdrawScreen(balance: Int, accounts: List<WithdrawAccount>, st: WithdrawState, onAccount: (WithdrawAccount) -> Unit, onAmount: (String) -> Unit, onPin: (String) -> Unit, onConfirm: () -> Unit, onBack: () -> Unit) {
    var showPin by remember { mutableStateOf(false) }
    val amt = st.amount.toIntOrNull() ?: 0
    Scaffold(topBar = { DTopBar("Withdraw", onBack) }, bottomBar = { StickyCta { PrimaryButton("Continue", { showPin = true }, enabled = amt in 500..balance, accent = true) } }) { pad ->
        ScreenColumn(pad) {
            DCard(color = DP.Primary100, border = null) { Text("Available balance", color = DP.Primary600, fontSize = 12.sp); Text(taka(balance), style = MaterialTheme.typography.headlineSmall, color = DP.Primary700) }
            Text("Send to", style = MaterialTheme.typography.titleSmall)
            accounts.forEach { a ->
                DCard(onClick = { onAccount(a) }, border = if (a == st.account) DP.Primary500 else DP.Line) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(when (a.method) { PaymentMethod.BKASH -> Color(0xFFE2136E); PaymentMethod.NAGAD -> Color(0xFFF6921E); else -> DP.Primary500 }), contentAlignment = Alignment.Center) {
                            Text(a.method.label.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(a.method.label, style = MaterialTheme.typography.titleSmall); Text(a.account, color = DP.Muted, fontSize = 12.sp) }
                        RadioButton(a == st.account, { onAccount(a) })
                    }
                }
            }
            OutlinedTextField(st.amount, onAmount, label = { Text("Amount") }, prefix = { Text("৳ ") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), supportingText = { Text("Min ৳500 · free for bKash/Nagad, ৳10 for bank") }, shape = RoundedCornerShape(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(1000, 5000, balance).forEach { v -> AssistChip({ onAmount(v.toString()) }, { Text(if (v == balance) "All" else taka(v)) }) } }
        }
    }
    if (showPin && st.done == null) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        ModalBottomSheet(onDismissRequest = { showPin = false }, sheetState = sheet, containerColor = Color.White) {
            Column(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enter wallet PIN", style = MaterialTheme.typography.titleLarge)
                Text("Withdraw ${taka(amt)} to ${st.account?.method?.label} ${st.account?.account}", color = DP.Muted)
                OutlinedTextField(st.pin, onPin, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), textStyle = LocalTextStyle.current.copy(fontSize = 24.sp, letterSpacing = 16.sp, textAlign = TextAlign.Center),
                    isError = st.error != null, supportingText = { st.error?.let { Text(it) } })
                PrimaryButton(if (st.loading) "Processing…" else "Confirm withdraw", onConfirm, enabled = st.pin.length == 4 && !st.loading)
                TextButton(onClick = { scope.launch { sheet.hide(); showPin = false } }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }
    }
}

@Composable
fun WithdrawSuccessScreen(tx: WalletTx, account: WithdrawAccount?, onDone: () -> Unit) {
    Scaffold(bottomBar = { StickyCta { PrimaryButton("Back to earnings", onDone) } }) { pad ->
        Column(Modifier.fillMaxSize().background(DP.Bg).padding(pad).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.height(40.dp))
            Icon(Icons.Filled.CheckCircle, null, tint = DP.Success, modifier = Modifier.size(88.dp))
            Text("Withdrawal requested", style = MaterialTheme.typography.headlineSmall)
            Text(taka(-tx.amount), fontSize = 36.sp, fontWeight = FontWeight.Bold, color = DP.Primary500)
            DCard { KeyValue("To", "${account?.method?.label} ${account?.account}"); KeyValue("Reference", tx.id.takeLast(8).uppercase()); KeyValue("ETA", "Within 30 minutes") }
        }
    }
}

@Composable
fun ReconciliationScreen(r: Reconciliation?, onPeriod: (String) -> Unit, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(1) }
    val periods = listOf("This week", "October 2026", "Custom")
    LaunchedEffect(tab) { onPeriod(periods[tab]) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { DTopBar("Reconciliation", onBack) }, snackbarHost = { SnackbarHost(snack) }) { pad ->
        ScreenColumn(pad) {
            SegmentedTabs(periods, tab, { tab = it })
            if (r == null) { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return@ScreenColumn }
            DCard {
                KeyValue("Completed trips", "${r.completedTrips}")
                KeyValue("Gross fare", taka(r.gross), bold = true)
                KeyValue("Durpalla commission (${Commission.RATE_PERCENT}%)", "-" + taka(r.commission), DP.Danger)
                KeyValue("Net earnings", taka(r.net), DP.Success, true)
                KeyValue("Paid out (${r.withdrawals} withdrawals)", "-" + taka(r.paidOut))
                KeyValue("Cash collected (commission due ${taka(r.commissionDueOnCash)})", "-" + taka(r.cashCollected), DP.Warning)
                HorizontalDivider(color = DP.Line)
                KeyValue("Pending settlement", taka(r.pendingSettlement), DP.Accent, true)
            }
            SectionHeader("Trip-wise")
            DCard { r.lines.forEach { l -> KeyValue("${l.date} · ${l.code} · ${l.method.label}", "${taka(l.gross)} / -${taka(l.commission)}") } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("PDF statement", { scope.launch { snack.showSnackbar("Statement downloaded · ${r.period}") } }, Modifier.weight(1f))
                SecondaryButton("CSV", { scope.launch { snack.showSnackbar("CSV downloaded · ${r.period}") } }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun WalletScreen(balance: Int, txs: List<WalletTx>, onBack: () -> Unit, onWithdraw: () -> Unit) {
    Scaffold(topBar = { DTopBar("Wallet", onBack) }, bottomBar = { StickyCta { PrimaryButton("Withdraw", onWithdraw, accent = true) } }) { pad ->
        ScreenColumn(pad) {
            DCard(color = DP.Primary500, border = null) { Text("Balance", color = DP.Primary200); Text(taka(balance), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold) }
            SectionHeader("Transactions")
            DCard {
                txs.forEach { t ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(when (t.type) { TxType.EARNING -> Icons.Filled.ArrowDownward; TxType.WITHDRAWAL -> Icons.Filled.ArrowUpward; TxType.COMMISSION -> Icons.Filled.Percent; TxType.ADJUSTMENT -> Icons.Filled.Tune }, null,
                            tint = if (t.amount >= 0) DP.Success else DP.Danger)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(t.title, style = MaterialTheme.typography.titleSmall); Text("${t.date}", color = DP.Muted, fontSize = 12.sp) }
                        Text((if (t.amount >= 0) "+" else "") + taka(t.amount), color = if (t.amount >= 0) DP.Success else DP.Danger, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun RatingsScreen(avg: Double, ratings: List<Rating>, onBack: () -> Unit) {
    Scaffold(topBar = { DTopBar("Ratings", onBack) }) { pad ->
        ScreenColumn(pad) {
            DCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("$avg", fontSize = 40.sp, fontWeight = FontWeight.Bold); Stars(avg.toInt()); Text("${ratings.size} reviews", color = DP.Muted, fontSize = 12.sp) }
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        (5 downTo 1).forEach { s ->
                            val c = ratings.count { it.stars == s }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("$s", fontSize = 12.sp, modifier = Modifier.width(14.dp))
                                LinearProgressIndicator(progress = { if (ratings.isEmpty()) 0f else c.toFloat() / ratings.size }, Modifier.weight(1f).padding(vertical = 4.dp), color = DP.Warning, trackColor = DP.Line)
                            }
                        }
                    }
                }
            }
            ratings.forEach { r ->
                DCard {
                    Row(verticalAlignment = Alignment.CenterVertically) { Avatar(r.customer, 36.dp); Column(Modifier.weight(1f).padding(horizontal = 10.dp)) { Text(r.customer, style = MaterialTheme.typography.titleSmall); Text("${r.date} · ${r.tripCode}", color = DP.Muted, fontSize = 11.sp) }; Stars(r.stars, 14.dp) }
                    Spacer(Modifier.height(6.dp)); Text(r.comment)
                }
            }
        }
    }
}

@PhonePreview @Composable private fun EarningsPreview() = P { EarningsScreen(SampleData.summary, {}, {}, {}, {}, 4.8) }
@PhonePreview @Composable private fun WithdrawPreview() = P {
    val a = listOf(WithdrawAccount(PaymentMethod.BKASH, "017•••567"), WithdrawAccount(PaymentMethod.NAGAD, "018•••921"))
    WithdrawScreen(18640, a, WithdrawState(account = a[0], amount = "5000"), {}, {}, {}, {}, {})
}
@PhonePreview @Composable private fun WithdrawSuccessPreview() = P { WithdrawSuccessScreen(SampleData.transactions[2], WithdrawAccount(PaymentMethod.BKASH, "017•••567")) {} }
@PhonePreview @Composable private fun ReconPreview() = P {
    ReconciliationScreen(Reconciliation("October 2026", 42, 96400, 9640, 62000, 4, 6120, listOf(TripLine("23 Oct", "DP-25814", PaymentMethod.CASH, 3200, 320))), {}, {})
}
@PhonePreview @Composable private fun WalletPreview() = P { WalletScreen(18640, SampleData.transactions, {}, {}) }
@PhonePreview @Composable private fun RatingsPreview() = P { RatingsScreen(4.8, SampleData.ratings, {}) }
