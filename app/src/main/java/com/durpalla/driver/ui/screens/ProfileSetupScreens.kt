package com.durpalla.driver.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.durpalla.driver.R
import com.durpalla.driver.data.ServiceLocator
import com.durpalla.driver.ui.components.DTopBar
import com.durpalla.driver.ui.components.PrimaryButton
import com.durpalla.driver.ui.components.ScreenColumn
import com.durpalla.driver.ui.components.StickyCta
import com.durpalla.driver.ui.theme.DP
import kotlinx.coroutines.launch

/** Collect display name when OTP register left `drivers.name` empty. */
@Composable
fun DisplayNameScreen(initial: String = "", onSaved: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val trimmed = name.trim()
    Scaffold(
        topBar = { DTopBar(stringResource(R.string.your_name_title)) },
        bottomBar = {
            StickyCta {
                PrimaryButton(
                    if (saving) stringResource(R.string.saving) else stringResource(R.string.continue_label),
                    onClick = {
                        if (saving) return@PrimaryButton
                        if (trimmed.length < 2) return@PrimaryButton
                        saving = true
                        error = null
                        scope.launch {
                            ServiceLocator.profile.updateName(trimmed)
                                .onSuccess { onSaved() }
                                .onFailure { error = it.message }
                            saving = false
                        }
                    },
                    enabled = trimmed.length >= 2 && !saving,
                    loading = saving,
                    accent = true,
                )
            }
        },
    ) { pad ->
        ScreenColumn(pad) {
            Text(stringResource(R.string.your_name_sub), color = DP.Muted, fontSize = 14.sp)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; error = null },
                label = { Text(stringResource(R.string.your_name_label)) },
                singleLine = true,
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )
        }
    }
}
