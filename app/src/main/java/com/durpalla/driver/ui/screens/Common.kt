package com.durpalla.driver.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.durpalla.driver.ui.theme.DurpallaTheme

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
annotation class PhonePreview

@Composable
fun P(content: @Composable () -> Unit) = DurpallaTheme(content = content)
