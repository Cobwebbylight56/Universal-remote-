package com.universalremote.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.universalremote.app.AppState
import com.universalremote.app.control.Controllers
import com.universalremote.app.model.Device
import com.universalremote.app.model.Key
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodesScreen(app: AppState, device: Device) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    val controller = rememberController(app, device) { status = it }

    // Edited values, keyed by Key.name. Starts from the saved overrides.
    val edits = remember(device.id) { mutableStateMapOf<String, String>().apply { putAll(device.codes) } }

    var explorerCode by remember {
        mutableStateOf(controller.codeFor(Key.POWER) ?: "")
    }
    var assignTo by remember { mutableStateOf(Key.POWER) }

    fun send(code: String) {
        if (code.isBlank()) return
        scope.launch {
            try {
                status = "Sending $code…"
                controller.sendCode(code)
                status = "Sent $code"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                status = "Failed: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun step(delta: Int) {
        val n = explorerCode.trim().toIntOrNull() ?: return
        explorerCode = (n + delta).toString()
        send(explorerCode)
    }

    fun save() {
        val cleaned = edits.filterValues { it.isNotBlank() }
            .filter { (k, v) -> runCatching { controller.defaultCode(Key.valueOf(k)) }.getOrNull() != v.trim() }
            .mapValues { it.value.trim() }
        app.upsert(device.copy(codes = cleaned))
        app.back()
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Button codes") },
            navigationIcon = {
                IconButton(onClick = { app.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            },
            actions = { TextButton(onClick = { save() }) { Text("Save") } },
        )
    }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Code explorer", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Send any code and watch the TV. When a code does something useful, assign it to a button. " +
                                "With numeric codes, − and + send the previous or next one.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedTextField(
                            explorerCode, { explorerCode = it }, label = { Text("Code") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(onClick = { step(-1) }) { Text("−") }
                            Button(onClick = { send(explorerCode) }, modifier = Modifier.weight(1f)) { Text("Send") }
                            FilledTonalButton(onClick = { step(1) }) { Text("+") }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Assign to", modifier = Modifier.width(80.dp))
                            Picker(assignTo, Key.entries, { it.label }, { assignTo = it }, Modifier.weight(1f))
                            TextButton(onClick = {
                                edits[assignTo.name] = explorerCode.trim()
                                status = "${assignTo.label} = ${explorerCode.trim()} (tap Save to keep)"
                            }) { Text("Assign") }
                        }
                        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                Text(
                    Controllers.codeHint(device.type),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(Key.entries, key = { it.name }) { key ->
                val default = controller.defaultCode(key).orEmpty()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(key.label, modifier = Modifier.width(84.dp))
                    OutlinedTextField(
                        value = edits[key.name].orEmpty(),
                        onValueChange = { edits[key.name] = it },
                        placeholder = { Text(default.ifBlank { "not set" }) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { send(edits[key.name]?.takeIf { it.isNotBlank() } ?: default) }) { Text("Test") }
                }
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    "Empty fields use the built-in code shown in grey.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 32.dp),
                )
            }
        }
    }
}
