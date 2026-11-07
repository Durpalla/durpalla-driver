package com.durpalla.driver.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.durpalla.driver.R
import com.durpalla.driver.data.ServiceLocator
import com.durpalla.driver.domain.ServiceStoppage
import com.durpalla.driver.ui.components.DTopBar
import com.durpalla.driver.ui.components.PrimaryButton
import com.durpalla.driver.ui.components.StickyCta
import com.durpalla.driver.ui.theme.DP
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceAreaScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<ServiceStoppage>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }

    LaunchedEffect(Unit) {
        loading = true
        error = null
        ServiceLocator.profile.loadServiceStoppages()
            .onSuccess { list ->
                items = list
                selected = list.filter { it.selected }.map { it.id }.toSet()
            }
            .onFailure { error = it.message }
        loading = false
    }

    Scaffold(
        topBar = { DTopBar(stringResource(R.string.service_area), onBack) },
        bottomBar = {
            if (!loading) {
                StickyCta {
                    PrimaryButton(
                        if (saving) stringResource(R.string.saving) else stringResource(R.string.save_service_area),
                        onClick = {
                            if (saving) return@PrimaryButton
                            scope.launch {
                                saving = true
                                error = null
                                ServiceLocator.profile.saveServiceStoppages(selected.toList().sorted())
                                    .onSuccess {
                                        ServiceLocator.requests.refresh()
                                        onBack()
                                    }
                                    .onFailure { error = it.message }
                                saving = false
                            }
                        },
                        enabled = !saving,
                        loading = saving,
                        accent = true,
                    )
                }
            }
        },
    ) { pad ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
                Text(
                    stringResource(R.string.service_area_hint),
                    color = DP.Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                error?.let {
                    Text(it, color = DP.Danger, style = MaterialTheme.typography.bodySmall)
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(items, key = { it.id }) { s ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Checkbox(
                                checked = selected.contains(s.id),
                                onCheckedChange = { on ->
                                    selected = if (on) selected + s.id else selected - s.id
                                },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(s.name, style = MaterialTheme.typography.bodyLarge)
                                if (s.cityName.isNotBlank()) {
                                    Text(s.cityName, color = DP.Muted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
