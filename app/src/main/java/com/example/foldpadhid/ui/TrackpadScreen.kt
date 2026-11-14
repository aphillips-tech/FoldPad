package com.example.foldpadhid.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foldpadhid.hid.BluetoothHidController
import com.example.foldpadhid.hid.HidConnectionState
import com.example.foldpadhid.shizuku.ShizukuDisplayState
import kotlin.math.abs
import kotlin.math.roundToInt

@SuppressLint("MissingPermission")
@Composable
fun TrackpadScreen(
    hidState: HidConnectionState,
    shizukuState: ShizukuDisplayState,
    hidController: BluetoothHidController,
    onRequestPermissions: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onAuthorizeShizuku: () -> Unit,
    onToggleDualScreen: (Boolean) -> Unit
) {
    val sensitivity = 1.40f
    val scrollDir = -1
    val tapToClick = true
    var activeModifiers by remember { mutableIntStateOf(0) }
    var typedBuffer by remember { mutableStateOf("") }

    val shizukuLabel = when {
        !shizukuState.shizukuAvailable -> "Refresh Shizuku"
        !shizukuState.shizukuAuthorized -> "Authorize Shizuku"
        !shizukuState.serviceBound -> "Bind Cover Display"
        shizukuState.coverDisplayActive -> "Cover ON"
        else -> "Cover Dual-Screen"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header Status Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "FoldPadHID",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = hidState.connectionStatus,
                    color = if (hidState.connectedDevice != null) Color(0xFF34D399) else Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onMakeDiscoverable) {
                    Text("Pair / Discover", fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        when {
                            !shizukuState.shizukuAvailable -> onAuthorizeShizuku()
                            !shizukuState.shizukuAuthorized -> onAuthorizeShizuku()
                            !shizukuState.serviceBound -> onAuthorizeShizuku()
                            else -> onToggleDualScreen(!shizukuState.coverDisplayActive)
                        }
                    }
                ) {
                    Text(shizukuLabel, fontSize = 12.sp)
                }
            }
        }

        // Paired Bluetooth Hosts Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    onRequestPermissions()
                    hidController.refreshBondedDevices()
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("Refresh Hosts", fontSize = 11.sp)
            }
            if (hidState.bondedDevices.isEmpty()) {
                Text(
                    text = "No paired hosts yet — tap Pair / Discover then connect from PC/Mac",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            } else {
                hidState.bondedDevices.forEach { device ->
                    val isConnected = hidState.connectedDevice?.address == device.address
                    FilterChip(
                        selected = isConnected,
                        onClick = {
                            if (isConnected) {
                                hidController.disconnectHost(device)
                            } else {
                                hidController.connectHost(device)
                            }
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

        // Live Keyboard Text Relay Input
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = typedBuffer,
                onValueChange = { next ->
                    if (next.length > typedBuffer.length) {
                        val added = next.substring(typedBuffer.length)
                        added.forEach { ch -> hidController.sendAsciiChar(ch, activeModifiers) }
                    } else if (next.length < typedBuffer.length) {
                        val removedCount = typedBuffer.length - next.length
                        repeat(removedCount) { hidController.sendKeyTap(0, 0x2A) }
                    }
                    typedBuffer = if (next.length > 24) next.takeLast(12) else next
                },
                placeholder = { Text("Tap here to type to host...", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(onClick = { hidController.sendKeyTap(0, 0x2A) }) {
                Text("Bksp", fontSize = 12.sp)
            }
        }

        // Precision Multi-Touch Trackpad Surface (1-finger move/tap + 2-finger scroll/right-click)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF1E293B), RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                .pointerInput(sensitivity, tapToClick) {
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        var maxPointers = 1
                        var totalMoved = 0f
                        var prevPositions = mapOf(firstDown.id to firstDown.position)

                        while (true) {
                            val event = awaitPointerEvent()
                            val activeChanges = event.changes.filter { it.pressed }
                            if (activeChanges.size > maxPointers) {
                                maxPointers = activeChanges.size
                            }
                            if (activeChanges.isEmpty()) break

                            if (activeChanges.size == 1) {
                                val p = activeChanges[0]
                                val prev = prevPositions[p.id] ?: p.position
                                val delta = p.position - prev
                                val dx = (delta.x * sensitivity).roundToInt()
                                val dy = (delta.y * sensitivity).roundToInt()
                                totalMoved += abs(delta.x) + abs(delta.y)
                                if (dx != 0 || dy != 0) {
                                    hidController.sendMouseReport(0x00, dx, dy)
                                }
                                p.consume()
                            } else if (activeChanges.size >= 2) {
                                val avgDelta = activeChanges.fold(Offset.Zero) { acc, change ->
                                    val prev = prevPositions[change.id] ?: change.position
                                    acc + (change.position - prev)
                                } / activeChanges.size.toFloat()
                                totalMoved += abs(avgDelta.x) + abs(avgDelta.y)
                                val wheel = (avgDelta.y / 14f * scrollDir).roundToInt()
                                val pan = (avgDelta.x / 18f * scrollDir).roundToInt()
                                if (wheel != 0 || pan != 0) {
                                    hidController.sendMouseReport(0x00, 0, 0, wheel = wheel, pan = pan)
                                }
                                activeChanges.forEach { it.consume() }
                            }

                            prevPositions = activeChanges.associate { it.id to it.position }
                        }

                        if (tapToClick && totalMoved < 14f) {
                            val btn = if (maxPointers >= 2) 0x02 else 0x01
                            hidController.sendMouseReport(btn, 0, 0)
                            hidController.sendMouseReport(0x00, 0, 0)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Trackpad · 1-finger move/tap · 2-finger scroll/right-click",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = hidState.lastReportHex,
                    color = Color(0xFF64748B),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
                if (shizukuState.physicalDisplayIds.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Displays: ${shizukuState.physicalDisplayIds.size} · Cover ${if (shizukuState.coverDisplayActive) "Awake" else "Standby"}",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Left / Right Mouse Buttons
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    hidController.sendMouseReport(0x01, 0, 0)
                    hidController.sendMouseReport(0x00, 0, 0)
                },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Left Click")
            }
            OutlinedButton(
                onClick = {
                    hidController.sendMouseReport(0x02, 0, 0)
                    hidController.sendMouseReport(0x00, 0, 0)
                },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Right Click", color = Color.White)
            }
        }

        // Quick Modifier + Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val modifiers = listOf("Ctrl" to 0x01, "Shift" to 0x02, "Alt" to 0x04, "Cmd" to 0x08)
            modifiers.forEach { (label, bit) ->
                val active = (activeModifiers and bit) != 0
                FilterChip(
                    selected = active,
                    onClick = { activeModifiers = activeModifiers xor bit },
                    label = { Text(label, fontSize = 12.sp) }
                )
            }
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = { hidController.sendKeyTap(activeModifiers, 0x28) }) {
                Text("Enter")
            }
            FilledTonalButton(onClick = { hidController.sendKeyTap(activeModifiers, 0x29) }) {
                Text("Esc")
            }
        }
    }
}
