package com.durpalla.driver.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.durpalla.driver.R
import com.durpalla.driver.data.ServiceLocator
import com.durpalla.driver.i18n.asString
import com.durpalla.driver.security.promptBiometric
import com.durpalla.driver.ui.AuthState
import com.durpalla.driver.ui.components.DTopBar
import com.durpalla.driver.ui.components.PrimaryButton
import com.durpalla.driver.ui.components.ScreenColumn
import com.durpalla.driver.ui.components.SecondaryButton
import com.durpalla.driver.ui.components.StickyCta
import com.durpalla.driver.ui.theme.DP
import kotlinx.coroutines.launch

/** First OTP login: set password for future password login (customer parity). */
@Composable
fun SetPasswordScreen(state: AuthState, onSave: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    val mismatch = stringResource(R.string.err_password_mismatch)
    val ok = password.length in 8..20 && password == confirm
    Scaffold(
        topBar = { DTopBar(stringResource(R.string.password_set_title)) },
        bottomBar = {
            StickyCta {
                PrimaryButton(
                    if (state.loading) stringResource(R.string.saving) else stringResource(R.string.password_save),
                    onClick = {
                        if (state.loading) return@PrimaryButton
                        if (password != confirm) {
                            localError = mismatch
                            return@PrimaryButton
                        }
                        localError = null
                        onSave(password)
                    },
                    enabled = ok && !state.loading,
                    loading = state.loading,
                    accent = true,
                )
            }
        },
    ) { pad ->
        ScreenColumn(pad) {
            Text(stringResource(R.string.password_set_sub), color = DP.Muted, fontSize = 14.sp)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it.take(20); localError = null },
                label = { Text(stringResource(R.string.password_label)) },
                singleLine = true,
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton({ show = !show }) {
                        Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it.take(20); localError = null },
                label = { Text(stringResource(R.string.password_confirm_label)) },
                singleLine = true,
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = localError != null || state.error != null,
                supportingText = {
                    (localError ?: state.error?.asString())?.let { Text(it) }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )
        }
    }
}

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

/** Register journey: offer fingerprint login; Skip continues without enabling. */
@Composable
fun BiometricSetupScreen(
    canEnable: Boolean,
    onEnable: () -> Unit,
    onSkip: () -> Unit,
) {
    val activity = LocalContext.current as? FragmentActivity
    val available = remember { ServiceLocator.biometricLogin.canAuthenticate() }
    var error by remember { mutableStateOf<String?>(null) }
    val title = stringResource(R.string.biometric_prompt_enable_title)
    val subtitle = stringResource(R.string.biometric_prompt_enable_sub)
    val cancel = stringResource(R.string.cancel)
    val host = activity
    Scaffold(
        topBar = { DTopBar(stringResource(R.string.biometric_setup_title)) },
        bottomBar = {
            StickyCta {
                if (available && canEnable && host != null) {
                    PrimaryButton(
                        stringResource(R.string.biometric_enable),
                        onClick = {
                            host.promptBiometric(
                                title = title,
                                subtitle = subtitle,
                                negativeButton = cancel,
                                onSuccess = onEnable,
                                onError = { error = it },
                            )
                        },
                        accent = true,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                SecondaryButton(stringResource(R.string.biometric_skip), onClick = onSkip)
            }
        },
    ) { pad ->
        ScreenColumn(pad) {
            Icon(
                Icons.Filled.Fingerprint,
                contentDescription = null,
                tint = DP.Primary500,
                modifier = Modifier.size(64.dp).align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.biometric_setup_title), style = MaterialTheme.typography.titleLarge)
            Text(
                if (available) stringResource(R.string.biometric_setup_sub)
                else stringResource(R.string.biometric_unavailable),
                color = DP.Muted,
                fontSize = 14.sp,
            )
            error?.let { Text(it, color = DP.Danger, fontSize = 13.sp) }
        }
    }
}
