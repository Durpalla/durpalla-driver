package com.durpalla.driver.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.durpalla.driver.R
import com.durpalla.driver.domain.VehicleType
import com.durpalla.driver.i18n.DigitFormatter
import com.durpalla.driver.i18n.toLocalDigits
import com.durpalla.driver.ui.components.*
import com.durpalla.driver.ui.theme.DP
import com.durpalla.driver.ui.title

/** Solo / owner-operator: register one boat that this driver will operate. */
@Composable
fun AddVehicleScreen(
    defaultType: VehicleType = VehicleType.BOAT,
    onBack: () -> Unit,
    onSave: (title: String, reg: String, capacity: Int, type: VehicleType) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var reg by remember { mutableStateOf("") }
    var capacity by remember { mutableStateOf("10") }
    var type by remember { mutableStateOf(defaultType) }
    var saving by remember { mutableStateOf(false) }
    val cap = DigitFormatter.parseInt(capacity) ?: 0
    val canSave = title.trim().length >= 2 && cap > 0 && !saving

    Scaffold(
        topBar = { DTopBar(stringResource(R.string.add_vehicle), onBack) },
        bottomBar = {
            StickyCta {
                PrimaryButton(
                    if (saving) stringResource(R.string.saving) else stringResource(R.string.save_vehicle),
                    {
                        saving = true
                        onSave(title.trim(), reg.trim(), cap, type)
                    },
                    enabled = canSave,
                    accent = true,
                    loading = saving,
                )
            }
        },
    ) { pad ->
        ScreenColumn(pad) {
            Text(stringResource(R.string.add_vehicle_hint), color = DP.Muted, fontSize = 13.sp)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(80) },
                label = { Text(stringResource(R.string.vehicle_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )
            OutlinedTextField(
                value = reg,
                onValueChange = { reg = it.take(32) },
                label = { Text(stringResource(R.string.vehicle_reg_no)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )
            OutlinedTextField(
                value = capacity.toLocalDigits(),
                onValueChange = { capacity = DigitFormatter.digitsOnly(it, 3) },
                label = { Text(stringResource(R.string.vehicle_capacity)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )
            SectionHeader(stringResource(R.string.kyc_what))
            VehicleType.entries.forEach { vt ->
                val selected = type == vt
                DCard(onClick = { type = vt }, color = if (selected) DP.Primary100 else DP.Card) {
                    Text(vt.title(), style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}
