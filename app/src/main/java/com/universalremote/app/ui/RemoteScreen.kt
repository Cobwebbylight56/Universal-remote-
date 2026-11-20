package com.universalremote.app.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.universalremote.app.AppState
import com.universalremote.app.Screen
import com.universalremote.app.control.ControllerEnv
import com.universalremote.app.control.Controllers
import com.universalremote.app.control.TvController
import com.universalremote.app.control.WakeOnLan
import com.universalremote.app.model.Device
import com.universalremote.app.model.Key
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Creates a controller for [device] that lives as long as the calling screen, and keeps it in sync with edits. */
@Composable
fun rememberController(app: AppState, device: Device, onStatus: (String) -> Unit): TvController {
    val context = LocalContext.current
    val statusCb by rememberUpdatedState(onStatus)
    val controller = remember(device.id, device.type) {
        val main = Handler(Looper.getMainLooper())
        Controllers.create(device, context, ControllerEnv(
            status = { msg -> main.post { statusCb(msg) } },
            updateDevice = { d -> main.post { app.device(d.id)?.let { cur -> app.upsert(cur.copy(token = d.token)) } } },
        ))
    }
    LaunchedEffect(device) {
        // Pick up edited codes / host, but keep any token the controller just learned.
        controller.device = device.copy(token = device.token.ifBlank { controller.device.token })
    }
    DisposableEffect(controller) { onDispose { controller.close() } }
    return controller
}

private fun Throwable.friendly(): String = when (this) {
    is java.net.SocketTimeoutException -> "The TV didn't respond in time. Is it switched on?"
    is java.net.UnknownHostException -> "Can't find that address on the network."
    else -> message ?: javaClass.simpleName
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(app: AppState, device: Device) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var showNumbers by remember { mutableStateOf(false) }

    val controller = rememberController(app, device) { status = it; isError = false }

    // Presses are queued so they reach the TV in order, even when tapped quickly.
    val queue = remember(controller) { Channel<Key>(capacity = 32) }
    LaunchedEffect(controller) {
        for (key in queue) {
            try {
                controller.send(key)
                if (isError) { status = ""; isError = false }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                status = e.friendly()
                isError = true
            }
        }
    }
    val press: (Key) -> Unit = { key ->
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        queue.trySend(key)
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(device.name, maxLines = 1) },
            navigationIcon = {
                IconButton(onClick = { app.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            },
            actions = {
                IconButton(onClick = { app.go(Screen.Codes(device.id)) }) { Icon(Icons.Filled.Build, "Button codes") }
                IconButton(onClick = { app.go(Screen.Edit(device.id)) }) { Icon(Icons.Filled.Edit, "Edit TV") }
            },
        )
    }) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Top row: power, source, mute (+ wake if a MAC is set)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    RKey("", { press(Key.POWER) }, icon = Glyphs.Power, size = 60.dp, shape = CircleShape,
                        container = Color(0xFFD93A3A), content = Color.White, description = "Power")
                    if (device.mac.isNotBlank()) {
                        RKey("Wake", {
                            scope.launch {
                                try {
                                    WakeOnLan.send(context, device.mac); status = "Wake-on-LAN sent"; isError = false
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Throwable) {
                                    status = e.friendly(); isError = true
                                }
                            }
                        }, size = 60.dp, shape = CircleShape)
                    }
                    RKey("Source", { press(Key.SOURCE) }, size = 60.dp, shape = CircleShape, fontSize = 12.sp)
                    RKey("Mute", { press(Key.MUTE) }, size = 60.dp, shape = CircleShape, fontSize = 12.sp)
                }

                // D-pad
                DPad(press)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RKey("Back", { press(Key.BACK) })
                    RKey("", { press(Key.HOME) }, icon = Icons.Filled.Home, description = "Home")
                    RKey("Menu", { press(Key.MENU) })
                    RKey("Exit", { press(Key.EXIT) })
                }

                // Volume / channel rockers
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Rocker("VOL", { press(Key.VOL_UP) }, { press(Key.VOL_DOWN) })
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        RKey("Info", { press(Key.INFO) })
                        RKey("Guide", { press(Key.GUIDE) })
                        RKey("123", { showNumbers = !showNumbers })
                    }
                    Rocker("CH", { press(Key.CH_UP) }, { press(Key.CH_DOWN) })
                }

                if (showNumbers) NumberPad(press)

                // Media
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    RKey("", { press(Key.REWIND) }, size = 52.dp, icon = Glyphs.Rewind, description = "Rewind")
                    RKey("", { press(Key.PLAY) }, size = 52.dp, icon = Icons.Filled.PlayArrow, description = "Play")
                    RKey("", { press(Key.PAUSE) }, size = 52.dp, icon = Glyphs.Pause, description = "Pause")
                    RKey("", { press(Key.STOP) }, size = 52.dp, icon = Glyphs.Stop, description = "Stop")
                    RKey("", { press(Key.FAST_FORWARD) }, size = 52.dp, icon = Glyphs.FastForward, description = "Fast forward")
                }

                // Colour keys
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColorKey(Color(0xFFE5484D), "Red") { press(Key.RED) }
                    ColorKey(Color(0xFF30A46C), "Green") { press(Key.GREEN) }
                    ColorKey(Color(0xFFF5D90A), "Yellow") { press(Key.YELLOW) }
                    ColorKey(Color(0xFF0090FF), "Blue") { press(Key.BLUE) }
                }

                // Apps
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppKey("NETFLIX", Color(0xFF141414), Color(0xFFE50914)) { press(Key.NETFLIX) }
                    AppKey("YouTube", Color(0xFFFF0000), Color.White) { press(Key.YOUTUBE) }
                }

                if (status.isNotBlank()) {
                    Surface(
                        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                status,
                                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (isError) {
                                TextButton(onClick = { app.go(Screen.Codes(device.id)) }, contentPadding = PaddingValues(0.dp)) {
                                    Text("Open button codes / explorer")
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DPad(press: (Key) -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(228.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            RKey("", { press(Key.UP) }, icon = Icons.Filled.KeyboardArrowUp, size = 64.dp, shape = CircleShape,
                plain = true, description = "Up", modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp), repeat = true)
            RKey("", { press(Key.DOWN) }, icon = Icons.Filled.KeyboardArrowDown, size = 64.dp, shape = CircleShape,
                plain = true, description = "Down", modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), repeat = true)
            RKey("", { press(Key.LEFT) }, icon = Icons.Filled.KeyboardArrowLeft, size = 64.dp, shape = CircleShape,
                plain = true, description = "Left", modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp), repeat = true)
            RKey("", { press(Key.RIGHT) }, icon = Icons.Filled.KeyboardArrowRight, size = 64.dp, shape = CircleShape,
                plain = true, description = "Right", modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp), repeat = true)
            RKey("OK", { press(Key.OK) }, size = 84.dp, shape = CircleShape, fontSize = 20.sp,
                container = MaterialTheme.colorScheme.primary, content = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun Rocker(label: String, onUp: () -> Unit, onDown: () -> Unit) {
    Surface(shape = RoundedCornerShape(36.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(
            Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RKey("+", onUp, size = 60.dp, shape = CircleShape, plain = true, fontSize = 26.sp, repeat = true, description = "$label up")
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 4.dp))
            RKey("−", onDown, size = 60.dp, shape = CircleShape, plain = true, fontSize = 26.sp, repeat = true, description = "$label down")
        }
    }
}

@Composable
private fun NumberPad(press: (Key) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { n -> RKey("$n", { press(Key.digit(n)) }, size = 64.dp, shape = CircleShape, fontSize = 22.sp) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            RKey("0", { press(Key.NUM_0) }, size = 64.dp, shape = CircleShape, fontSize = 22.sp)
        }
    }
}

@Composable
private fun RowScope.ColorKey(color: Color, name: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.weight(1f).height(36.dp).semantics { contentDescription = name },
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = color),
        contentPadding = PaddingValues(0.dp),
    ) {}
}

@Composable
private fun RowScope.AppKey(label: String, bg: Color, fg: Color, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.weight(1f).height(48.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = bg, contentColor = fg),
        shape = RoundedCornerShape(12.dp),
    ) { Text(label, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
}

/** One remote button. With [repeat], holding it keeps sending (for volume and arrows). */
@Composable
fun RKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    size: Dp = 56.dp,
    shape: Shape = RoundedCornerShape(16.dp),
    container: Color? = null,
    content: Color? = null,
    plain: Boolean = false,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    repeat: Boolean = false,
    description: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val click by rememberUpdatedState(onClick)
    if (repeat) {
        LaunchedEffect(pressed) {
            if (pressed) {
                delay(450)
                while (true) { click(); delay(160) }
            }
        }
    }
    val colors = when {
        plain -> ButtonDefaults.filledTonalButtonColors(
            containerColor = Color.Transparent,
            contentColor = content ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
        container != null -> ButtonDefaults.filledTonalButtonColors(
            containerColor = container,
            contentColor = content ?: MaterialTheme.colorScheme.onSecondaryContainer,
        )
        else -> ButtonDefaults.filledTonalButtonColors()
    }
    val wide = label.length > 3 && icon == null
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(size).widthIn(min = size),
        shape = shape,
        colors = colors,
        interactionSource = interaction,
        contentPadding = PaddingValues(horizontal = if (wide) 14.dp else 0.dp),
    ) {
        if (icon != null) Icon(icon, description ?: label, Modifier.size(size * 0.5f))
        else Text(label, fontSize = fontSize, maxLines = 1)
    }
}
