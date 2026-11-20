package com.universalremote.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.universalremote.app.AppState
import com.universalremote.app.Screen
import com.universalremote.app.control.Discovery
import com.universalremote.app.control.FoundTv
import com.universalremote.app.model.Device
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(app: AppState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val found = remember { mutableStateListOf<FoundTv>() }
    var scanning by remember { mutableStateOf(false) }
    var scanMessage by remember { mutableStateOf<String?>(null) }

    fun scan() {
        if (scanning) return
        found.clear()
        scanning = true
        scanMessage = null
        scope.launch {
            try {
                Discovery.scan(context) { tv ->
                    val known = app.devices.any { it.host == tv.host && it.type == tv.type }
                    if (!known) found.add(tv)
                }
                if (found.isEmpty()) scanMessage =
                    "No new TVs found. Make sure the TV is switched on and on the same Wi-Fi, or add it manually."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                scanMessage = e.message
            } finally {
                scanning = false
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Universal Remote") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { app.go(Screen.Edit(null)) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Add manually") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("My TVs", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
            if (app.devices.isEmpty()) {
                item {
                    Text(
                        "No TVs yet. Scan your Wi-Fi network, or add one manually.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(app.devices, key = { it.id }) { d ->
                Card(Modifier.fillMaxWidth().clickable { app.go(Screen.Remote(d.id)) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(d.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                d.type.title + if (d.host.isNotBlank()) " · ${d.host}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { app.go(Screen.Edit(d.id)) }) { Icon(Icons.Filled.Edit, "Edit") }
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Find TVs on Wi-Fi", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (scanning) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Button(onClick = { scan() }) {
                            Icon(Icons.Filled.Search, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Scan")
                        }
                    }
                }
            }
            scanMessage?.let { msg ->
                item { Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            }
            items(found, key = { "${it.host}/${it.type}" }) { tv ->
                OutlinedCard(Modifier.fillMaxWidth().clickable {
                    val draft = Device(name = tv.name, type = tv.type, host = tv.host, port = tv.port)
                    app.go(Screen.Edit(null, draft))
                }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(tv.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${tv.type.title} · ${tv.host} · tap to add",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Text(
                    "The scan finds Vestel-based (Polaroid, Toshiba, JVC, Hitachi…), Roku, Samsung, LG and Sony TVs. " +
                        "For other brands, or TVs that aren't smart, use the IR option if your phone has an IR blaster.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 96.dp),
                )
            }
        }
    }
}
