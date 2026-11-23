package com.universalremote.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.universalremote.app.AppState
import com.universalremote.app.Screen
import com.universalremote.app.control.Diagnostics
import com.universalremote.app.control.Discovery
import com.universalremote.app.control.PortResult
import com.universalremote.app.model.Device
import com.universalremote.app.model.TvType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnoseScreen(app: AppState, initialHost: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val phoneIp = remember { Discovery.localIpv4(context) }
    var host by remember { mutableStateOf(initialHost) }
    var checking by remember { mutableStateOf(false) }
    var reachable by remember { mutableStateOf<Boolean?>(null) }
    var results by remember { mutableStateOf<List<PortResult>>(emptyList()) }

    fun check() {
        val h = host.trim()
        if (h.isBlank() || checking) return
        checking = true
        results = emptyList()
        reachable = null
        scope.launch {
            try {
                reachable = Diagnostics.isReachable(h)
                results = Diagnostics.checkPorts(h)
            } finally {
                checking = false
            }
        }
    }

    LaunchedEffect(Unit) { if (initialHost.isNotBlank()) check() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Network check") },
            navigationIcon = {
                IconButton(onClick = { app.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            },
        )
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Your phone: ${phoneIp ?: "not on Wi-Fi"}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (phoneIp == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Enter an address to see what it answers on. The TV's address usually starts with the same " +
                    "first three numbers as your phone's.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    host, { host = it }, label = { Text("IP address") },
                    placeholder = { Text(phoneIp?.substringBeforeLast('.')?.plus(".") ?: "192.168.1.") },
                    singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Button(onClick = { check() }, enabled = !checking) { Text("Check") }
            }

            if (checking) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Checking…")
                }
            }

            reachable?.let { ok ->
                Text(
                    if (ok) "The device answered a ping." else "No ping reply (some TVs ignore pings, so check the ports below).",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (results.isNotEmpty()) {
                val open = results.filter { it.open }
                Text(
                    if (open.isEmpty()) "No known ports are open." else "Open ports:",
                    style = MaterialTheme.typography.titleSmall,
                )
                results.sortedByDescending { it.open }.forEach { r ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            if (r.open) "OPEN" else "closed",
                            color = if (r.open) Color(0xFF30A46C) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (r.open) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.width(64.dp),
                        )
                        Text("${r.port}", modifier = Modifier.width(56.dp))
                        Text(r.meaning, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    }
                }

                val h = host.trim()
                val suggested = when {
                    open.any { it.port == 56789 } -> TvType.VESTEL
                    open.any { it.port == 8060 } -> TvType.ROKU
                    open.any { it.port == 8001 || it.port == 8002 } -> TvType.SAMSUNG
                    open.any { it.port == 3000 || it.port == 3001 } -> TvType.LG
                    else -> null
                }
                if (suggested != null) {
                    Button(onClick = {
                        app.replace(Screen.Edit(null, Device(name = suggested.title.substringBefore(" ("), type = suggested, host = h)))
                    }) { Text("Add as ${suggested.title}") }
                } else {
                    Text(
                        if (open.isEmpty())
                            "Nothing answered. Check the TV is switched on (not standby), connected to the same router, " +
                                "and that this is the right address."
                        else
                            "This device is on the network but doesn't offer a remote-control service this app knows. " +
                                "Please share this list so support can be added.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = {
                        app.replace(Screen.Edit(null, Device(name = "Polaroid TV", type = TvType.VESTEL, host = h)))
                    }) { Text("Add as Vestel TV anyway") }
                }
            }
        }
    }
}
