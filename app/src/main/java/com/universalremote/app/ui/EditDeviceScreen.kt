package com.universalremote.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.universalremote.app.AppState
import com.universalremote.app.Screen
import com.universalremote.app.control.IrCodeSets
import com.universalremote.app.control.IrController
import com.universalremote.app.model.Device
import com.universalremote.app.model.TvType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDeviceScreen(app: AppState, existing: Device?) {
    val context = LocalContext.current
    val isNew = existing == null || app.device(existing.id) == null
    val base = existing ?: Device(name = "", type = TvType.VESTEL)

    var name by remember { mutableStateOf(base.name) }
    var type by remember { mutableStateOf(base.type) }
    var host by remember { mutableStateOf(base.host) }
    var port by remember { mutableStateOf(base.port?.toString().orEmpty()) }
    var mac by remember { mutableStateOf(base.mac) }
    var psk by remember { mutableStateOf(base.psk) }
    var irBrand by remember { mutableStateOf(base.irBrand.ifBlank { "vestel" }) }
    var error by remember { mutableStateOf<String?>(null) }
    val hasIr = remember { IrController.hasEmitter(context) }

    fun save() {
        val trimmedHost = host.trim()
        if (type.usesNetwork && trimmedHost.isBlank()) {
            error = "Enter the TV's IP address (find it in the TV's network settings)."
            return
        }
        val portNum = port.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
        if (port.isNotBlank() && portNum == null) {
            error = "Port must be a number, or leave it empty."
            return
        }
        val typeChanged = type != base.type
        val d = base.copy(
            name = name.trim().ifBlank { type.title },
            type = type,
            host = trimmedHost,
            port = portNum,
            mac = mac.trim(),
            psk = psk.trim(),
            irBrand = if (type == TvType.IR) irBrand else base.irBrand,
            // Codes and pairing tokens belong to one backend; drop them if the type changes.
            token = if (typeChanged || trimmedHost != base.host) "" else base.token,
            codes = if (typeChanged) emptyMap() else base.codes,
        )
        app.upsert(d)
        if (isNew) app.replace(Screen.Remote(d.id)) else app.back()
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (isNew) "Add TV" else "Edit TV") },
            navigationIcon = {
                IconButton(onClick = { app.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            },
            actions = {
                if (!isNew && existing != null) {
                    IconButton(onClick = { app.delete(existing.id); app.back() }) { Icon(Icons.Filled.Delete, "Delete") }
                }
            },
        )
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Text("How to control it", style = MaterialTheme.typography.labelLarge)
            Picker(type, TvType.entries, { it.title }, { type = it })
            Text(type.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (type.usesNetwork) {
                OutlinedTextField(
                    host, { host = it }, label = { Text("IP address") }, placeholder = { Text("192.168.1.50") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    port, { port = it }, label = { Text("Port (optional)") }, placeholder = { Text("automatic") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    mac, { mac = it }, label = { Text("MAC address (optional, for Wake-on-LAN)") },
                    placeholder = { Text("AA:BB:CC:DD:EE:FF") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            if (type == TvType.SONY) {
                OutlinedTextField(psk, { psk = it }, label = { Text("Pre-Shared Key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            if (type == TvType.VESTEL) {
                Text(
                    "Tip: on Vestel-made TVs, turn on Settings › System › More › Virtual Remote (wording varies) " +
                        "so phone apps can control the TV. Find the IP under Settings › Network.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (type == TvType.IR) {
                Text("IR brand", style = MaterialTheme.typography.labelLarge)
                Picker(
                    IrCodeSets.byId(irBrand) ?: IrCodeSets.all.first(),
                    IrCodeSets.all, { it.title }, { irBrand = it.id },
                )
                if (!hasIr) {
                    Text(
                        "This phone doesn't have an IR emitter, so IR control won't work here. Use a Wi-Fi option instead.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { save() }) { Text(if (isNew) "Add and open remote" else "Save") }
            }
        }
    }
}
