package com.durpalla.driver.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.durpalla.driver.ui.theme.DurpallaTheme

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
annotation class PhonePreview

@Composable
fun P(content: @Composable () -> Unit) = DurpallaTheme(content = content)

/** Shown when a route's item (trip, request, withdrawal) is gone, e.g. a stale deep link. Never a blank dead-end. */
@Composable
fun MissingContent(onBack: () -> Unit, onHome: () -> Unit) {
    androidx.compose.material3.Scaffold(topBar = { com.durpalla.driver.ui.components.DTopBar(androidx.compose.ui.res.stringResource(com.durpalla.driver.R.string.not_available_title), onBack) },
        bottomBar = { com.durpalla.driver.ui.components.StickyCta { com.durpalla.driver.ui.components.PrimaryButton(androidx.compose.ui.res.stringResource(com.durpalla.driver.R.string.back_home), onHome) } }) { pad ->
        com.durpalla.driver.ui.components.ScreenColumn(pad) { com.durpalla.driver.ui.components.EmptyState(androidx.compose.ui.res.stringResource(com.durpalla.driver.R.string.not_available_body)) }
    }
}
