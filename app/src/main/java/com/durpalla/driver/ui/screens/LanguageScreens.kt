package com.durpalla.driver.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.durpalla.driver.R
import com.durpalla.driver.i18n.AppLanguage
import com.durpalla.driver.i18n.AppLocale
import com.durpalla.driver.ui.components.ButtonVariant
import com.durpalla.driver.ui.components.DCard
import com.durpalla.driver.ui.components.DTopBar
import com.durpalla.driver.ui.components.DurpallaButton
import com.durpalla.driver.ui.components.ScreenColumn
import com.durpalla.driver.ui.theme.DP
import com.durpalla.driver.ui.theme.SolaimanLipi

/** Each option is always shown in its own script/font, whatever the current app language. */
private val bnStyle = TextStyle(fontFamily = SolaimanLipi, lineHeight = 1.5.em)
private val enStyle = TextStyle(fontFamily = FontFamily.Default, lineHeight = 1.3.em)

@Composable
private fun LanguageOption(lang: AppLanguage, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (name, hint) = when (lang) {
        AppLanguage.BANGLA -> stringResource(R.string.lang_bangla) to stringResource(R.string.lang_bangla_hint)
        AppLanguage.ENGLISH -> stringResource(R.string.lang_english) to stringResource(R.string.lang_english_hint)
    }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (selected) DP.Primary100 else Color.White)
            .border(if (selected) 2.dp else 1.dp, if (selected) DP.Primary500 else DP.Line, RoundedCornerShape(18.dp))
            .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(if (selected) DP.Primary500 else DP.Bg), contentAlignment = Alignment.Center) {
            Text(stringResource(if (lang == AppLanguage.BANGLA) R.string.lang_glyph_bn else R.string.lang_glyph_en), color = if (selected) Color.White else DP.Primary600, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                style = if (lang == AppLanguage.BANGLA) bnStyle else enStyle)
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DP.Ink, style = if (lang == AppLanguage.BANGLA) bnStyle else enStyle)
            Text(hint, fontSize = 13.sp, color = DP.Muted, style = if (lang == AppLanguage.BANGLA) enStyle else bnStyle)
        }
        Icon(if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null, tint = if (selected) DP.Primary500 else DP.Line, modifier = Modifier.size(26.dp))
    }
}

/** First-run prompt, shown right after the splash until a language is saved. Bilingual on purpose. */
@Composable
fun LanguagePromptScreen(initial: AppLanguage? = null, onContinue: (AppLanguage) -> Unit) {
    var choice by remember { mutableStateOf(initial) }
    BrandBackground {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(24.dp))
            Column(
                Modifier.widthIn(max = 440.dp).fillMaxWidth().shadow(24.dp, RoundedCornerShape(24.dp), ambientColor = DP.Primary700)
                    .clip(RoundedCornerShape(24.dp)).background(Color.White).padding(horizontal = 22.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Image(painterResource(R.drawable.durpalla_logo_horizontal), stringResource(R.string.brand_durpalla), Modifier.height(56.dp))
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.lang_prompt_title_bn), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = DP.Ink, textAlign = TextAlign.Center, style = bnStyle)
                Text(stringResource(R.string.lang_prompt_title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = DP.Primary600, textAlign = TextAlign.Center, style = enStyle)
                Spacer(Modifier.height(2.dp))
                LanguageOption(AppLanguage.BANGLA, choice == AppLanguage.BANGLA, { choice = AppLanguage.BANGLA })
                LanguageOption(AppLanguage.ENGLISH, choice == AppLanguage.ENGLISH, { choice = AppLanguage.ENGLISH })
                Spacer(Modifier.height(4.dp))
                val label = when (choice) { AppLanguage.BANGLA -> stringResource(R.string.lang_continue_bn); AppLanguage.ENGLISH -> stringResource(R.string.lang_continue_en); null -> stringResource(R.string.lang_continue_bilingual) }
                DurpallaButton(label, { choice?.let(onContinue) }, variant = ButtonVariant.Accent, enabled = choice != null)
                Text(stringResource(R.string.lang_prompt_sub), fontSize = 12.sp, color = DP.Muted, textAlign = TextAlign.Center, style = bnStyle)
            }
            Text(stringResource(R.string.powered_by), modifier = Modifier.padding(top = 24.dp), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }
    }
}

/** Profile › Language. Applies immediately. */
@Composable
fun LanguageSettingsScreen(current: AppLanguage = AppLocale.language, onSelect: (AppLanguage) -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = { DTopBar(stringResource(R.string.language), onBack) }) { pad ->
        ScreenColumn(pad) {
            Text(stringResource(R.string.language_sub), color = DP.Muted)
            LanguageOption(AppLanguage.BANGLA, current == AppLanguage.BANGLA, { onSelect(AppLanguage.BANGLA) })
            LanguageOption(AppLanguage.ENGLISH, current == AppLanguage.ENGLISH, { onSelect(AppLanguage.ENGLISH) })
        }
    }
}

@PhonePreview @Composable private fun LanguagePromptPreview() = P { LanguagePromptScreen(AppLanguage.BANGLA) {} }
