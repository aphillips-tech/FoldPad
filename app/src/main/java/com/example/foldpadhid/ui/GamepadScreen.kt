package com.example.foldpadhid.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foldpadhid.hid.BluetoothHidController
import com.example.foldpadhid.hid.HidConnectionState
import kotlin.math.hypot
import kotlin.math.roundToInt

@SuppressLint("MissingPermission")
@Composable
fun GamepadScreen(
    hidState: HidConnectionState,
    hidController: BluetoothHidController,
    onMakeDiscoverable: () -> Unit,
    onSwitchToTrackpad: () -> Unit
) {
    var buttonsMask by remember { mutableIntStateOf(0) }
    var hatValue by remember { mutableIntStateOf(8) } // 8 = centered
    var lx by remember { mutableIntStateOf(0) }
    var ly by remember { mutableIntStateOf(0) }
    var rx by remember { mutableIntStateOf(0) }
    var ry by remember { mutableIntStateOf(0) }
    var l2 by remember { mutableIntStateOf(0) }
    var r2 by remember { mutableIntStateOf(0) }

    fun pushState(
        nextButtons: Int = buttonsMask,
        nextHat: Int = hatValue,
        nextLx: Int = lx,
        nextLy: Int = ly,
        nextRx: Int = rx,
        nextRy: Int = ry,
        nextL2: Int = l2,
        nextR2: Int = r2
    ) {
        buttonsMask = nextButtons
        hatValue = nextHat
        lx = nextLx
        ly = nextLy
        rx = nextRx
        ry = nextRy
        l2 = nextL2
        r2 = nextR2
        hidController.sendGamepadReport(nextButtons, nextHat, nextLx, nextLy, nextRx, nextRy, nextL2, nextR2)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Top Header + Mode Switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "FoldPadHID · Gamepad Mode",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = hidState.connectionStatus,
                    color = if (hidState.connectedDevice != null) Color(0xFF34D399) else Color(0xFFFBBF24),
                    fontSize = 11.sp,
                    maxLines = 2
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onMakeDiscoverable,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("Pair", fontSize = 11.sp, maxLines = 1)
                }
                Button(
                    onClick = onSwitchToTrackpad,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("Trackpad Mode", fontSize = 11.sp, maxLines = 1)
                }
            }
        }

        // Paired Hosts Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { hidController.refreshBondedDevices() },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("Refresh", fontSize = 11.sp)
            }
            if (hidState.bondedDevices.isEmpty()) {
                Text(
                    text = "No paired hosts — tap Pair then connect from PC",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            } else {
                hidState.bondedDevices.forEach { device ->
                    val isConnected = hidState.connectedDevice?.address == device.address
                    FilterChip(
                        selected = isConnected,
                        onClick = {
                            if (isConnected) hidController.disconnectHost(device)
                            else hidController.connectHost(device)
                        },
                        label = {
                            Text(
                                text = (device.name ?: device.address) + if (isConnected) " (Connected)" else "",
                                fontSize = 11.sp
                            )
                        }
                    )
                }
            }
        }

        // Shoulder Triggers & Bumpers (L2, L1 | Select, Guide, Start | R1, R2)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GamepadHoldButton("L2", isActive = l2 > 0, modifier = Modifier.weight(1f)) { pressed ->
                pushState(nextL2 = if (pressed) 255 else 0)
            }
            GamepadHoldButton("L1", isActive = (buttonsMask and (1 shl 4)) != 0, modifier = Modifier.weight(1f)) { pressed ->
                val mask = if (pressed) buttonsMask or (1 shl 4) else buttonsMask and (1 shl 4).inv()
                pushState(nextButtons = mask)
            }
            GamepadHoldButton("Back", isActive = (buttonsMask and (1 shl 6)) != 0, modifier = Modifier.weight(0.9f)) { pressed ->
                val mask = if (pressed) buttonsMask or (1 shl 6) else buttonsMask and (1 shl 6).inv()
                pushState(nextButtons = mask)
            }
            GamepadHoldButton("Start", isActive = (buttonsMask and (1 shl 7)) != 0, modifier = Modifier.weight(0.9f)) { pressed ->
                val mask = if (pressed) buttonsMask or (1 shl 7) else buttonsMask and (1 shl 7).inv()
                pushState(nextButtons = mask)
            }
            GamepadHoldButton("R1", isActive = (buttonsMask and (1 shl 5)) != 0, modifier = Modifier.weight(1f)) { pressed ->
                val mask = if (pressed) buttonsMask or (1 shl 5) else buttonsMask and (1 shl 5).inv()
                pushState(nextButtons = mask)
            }
            GamepadHoldButton("R2", isActive = r2 > 0, modifier = Modifier.weight(1f)) { pressed ->
                pushState(nextR2 = if (pressed) 255 else 0)
            }
        }

        // Main Dual-Thumb Ergonomic Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Side: D-Pad + Left Analog Stick
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 4-Way D-Pad
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    GamepadHoldButton("▲", isActive = hatValue == 0, modifier = Modifier.size(54.dp, 40.dp)) { p ->
                        pushState(nextHat = if (p) 0 else 8)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        GamepadHoldButton("◀", isActive = hatValue == 6, modifier = Modifier.size(54.dp, 40.dp)) { p ->
                            pushState(nextHat = if (p) 6 else 8)
                        }
                        Spacer(Modifier.size(36.dp, 40.dp))
                        GamepadHoldButton("▶", isActive = hatValue == 2, modifier = Modifier.size(54.dp, 40.dp)) { p ->
                            pushState(nextHat = if (p) 2 else 8)
                        }
                    }
                    GamepadHoldButton("▼", isActive = hatValue == 4, modifier = Modifier.size(54.dp, 40.dp)) { p ->
                        pushState(nextHat = if (p) 4 else 8)
                    }
                }

                AnalogStickPad(
                    label = "L-Stick",
                    onMove = { x, y -> pushState(nextLx = x, nextLy = y) }
                )
            }

            // Center Telemetry + L3/R3/Home
            Column(
                modifier = Modifier.width(120.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GamepadHoldButton("Guide", isActive = (buttonsMask and (1 shl 10)) != 0, modifier = Modifier.fillMaxWidth()) { p ->
                    val mask = if (p) buttonsMask or (1 shl 10) else buttonsMask and (1 shl 10).inv()
                    pushState(nextButtons = mask)
                }
                Text(
                    text = hidState.lastReportHex,
                    color = Color(0xFF64748B),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GamepadHoldButton("L3", isActive = (buttonsMask and (1 shl 8)) != 0, modifier = Modifier.weight(1f)) { p ->
                        val mask = if (p) buttonsMask or (1 shl 8) else buttonsMask and (1 shl 8).inv()
                        pushState(nextButtons = mask)
                    }
                    GamepadHoldButton("R3", isActive = (buttonsMask and (1 shl 9)) != 0, modifier = Modifier.weight(1f)) { p ->
                        val mask = if (p) buttonsMask or (1 shl 9) else buttonsMask and (1 shl 9).inv()
                        pushState(nextButtons = mask)
                    }
                }
            }

            // Right Side: ABXY Diamond + Right Analog Stick
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ABXY Face Buttons (Bit 0=A, Bit 1=B, Bit 2=X, Bit 3=Y)
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    GamepadHoldButton("Y", isActive = (buttonsMask and (1 shl 3)) != 0, modifier = Modifier.size(54.dp, 44.dp), accent = Color(0xFFF59E0B)) { p ->
                        val mask = if (p) buttonsMask or (1 shl 3) else buttonsMask and (1 shl 3).inv()
                        pushState(nextButtons = mask)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GamepadHoldButton("X", isActive = (buttonsMask and (1 shl 2)) != 0, modifier = Modifier.size(54.dp, 44.dp), accent = Color(0xFF3B82F6)) { p ->
                            val mask = if (p) buttonsMask or (1 shl 2) else buttonsMask and (1 shl 2).inv()
                            pushState(nextButtons = mask)
                        }
                        Spacer(Modifier.size(28.dp, 44.dp))
                        GamepadHoldButton("B", isActive = (buttonsMask and (1 shl 1)) != 0, modifier = Modifier.size(54.dp, 44.dp), accent = Color(0xFFEF4444)) { p ->
                            val mask = if (p) buttonsMask or (1 shl 1) else buttonsMask and (1 shl 1).inv()
                            pushState(nextButtons = mask)
                        }
                    }
                    GamepadHoldButton("A", isActive = (buttonsMask and 1) != 0, modifier = Modifier.size(54.dp, 44.dp), accent = Color(0xFF10B981)) { p ->
                        val mask = if (p) buttonsMask or 1 else buttonsMask and 1.inv()
                        pushState(nextButtons = mask)
                    }
                }

                AnalogStickPad(
                    label = "R-Stick",
                    onMove = { x, y -> pushState(nextRx = x, nextRy = y) }
                )
            }
        }
    }
}

@Composable
private fun GamepadHoldButton(
    label: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    accent: Color = Color(0xFF2563EB),
    onPressChange: (Boolean) -> Unit
) {
    Box(
        modifier = modifier
            .heightIn(min = 38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) accent else Color(0xFF1E293B))
            .border(1.dp, if (isActive) Color.White else Color(0xFF334155), RoundedCornerShape(10.dp))
            .pointerInput(label) {
                detectTapGestures(
                    onPress = {
                        onPressChange(true)
                        tryAwaitRelease()
                        onPressChange(false)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AnalogStickPad(
    label: String,
    onMove: (Int, Int) -> Unit
) {
    var knobOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .size(132.dp)
            .clip(CircleShape)
            .background(Color(0xFF111827))
            .border(1.dp, Color(0xFF334155), CircleShape)
            .pointerInput(label) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val radius = (size.width / 2f - 24.dp.toPx()).coerceAtLeast(20f)
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.firstOrNull { it.pressed } ?: break
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val raw = pressed.position - center
                        val dist = hypot(raw.x, raw.y)
                        val clamped = if (dist > radius && dist > 0f) raw * (radius / dist) else raw
                        knobOffset = clamped
                        val normX = ((clamped.x / radius) * 127f).roundToInt().coerceIn(-127, 127)
                        val normY = ((clamped.y / radius) * 127f).roundToInt().coerceIn(-127, 127)
                        onMove(normX, normY)
                        pressed.consume()
                    }
                    knobOffset = Offset.Zero
                    onMove(0, 0)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color(0xFF3B82F6),
                radius = 24.dp.toPx(),
                center = center + knobOffset
            )
        }
        Text(
            text = label,
            color = Color(0xFF94A3B8),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
