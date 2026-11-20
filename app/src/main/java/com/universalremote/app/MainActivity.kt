package com.universalremote.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.universalremote.app.model.Device
import com.universalremote.app.model.DeviceStore
import com.universalremote.app.ui.CodesScreen
import com.universalremote.app.ui.DeviceListScreen
import com.universalremote.app.ui.EditDeviceScreen
import com.universalremote.app.ui.RemoteScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = AppState(DeviceStore(applicationContext))
        setContent {
            RemoteTheme { App(app) }
        }
    }
}

sealed interface Screen {
    data object Devices : Screen
    data class Remote(val id: String) : Screen
    data class Edit(val id: String?, val draft: Device? = null) : Screen
    data class Codes(val id: String) : Screen
}

class AppState(private val store: DeviceStore) {
    val devices = mutableStateListOf<Device>().apply { addAll(store.load()) }
    private val stack = mutableStateListOf<Screen>(Screen.Devices)
    val screen get() = stack.last()
    val canGoBack get() = stack.size > 1

    init {
        // Jump straight to the last remote you used.
        store.lastDeviceId?.let { id -> if (devices.any { it.id == id }) stack.add(Screen.Remote(id)) }
    }

    fun go(s: Screen) {
        stack.add(s)
        if (s is Screen.Remote) store.lastDeviceId = s.id
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
        if (screen == Screen.Devices) store.lastDeviceId = null
    }

    fun replace(s: Screen) {
        stack.removeAt(stack.lastIndex)
        go(s)
    }

    fun device(id: String) = devices.firstOrNull { it.id == id }

    fun upsert(d: Device) {
        val i = devices.indexOfFirst { it.id == d.id }
        if (i >= 0) devices[i] = d else devices.add(d)
        store.save(devices)
    }

    fun delete(id: String) {
        devices.removeAll { it.id == id }
        store.save(devices)
        stack.removeAll { (it as? Screen.Remote)?.id == id || (it as? Screen.Codes)?.id == id }
        if (stack.isEmpty()) stack.add(Screen.Devices)
    }
}

@Composable
fun App(app: AppState) {
    BackHandler(enabled = app.canGoBack) { app.back() }
    when (val s = app.screen) {
        Screen.Devices -> DeviceListScreen(app)
        is Screen.Remote -> {
            val d = app.device(s.id)
            if (d != null) RemoteScreen(app, d) else LaunchedEffect(s) { app.back() }
        }
        is Screen.Edit -> EditDeviceScreen(app, s.id?.let { app.device(it) } ?: s.draft)
        is Screen.Codes -> {
            val d = app.device(s.id)
            if (d != null) CodesScreen(app, d) else LaunchedEffect(s) { app.back() }
        }
    }
}

@Composable
fun RemoteTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = remember(dark) {
        when {
            Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dark -> darkColorScheme()
            else -> lightColorScheme()
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
