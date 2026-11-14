package com.example.foldpadhid.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foldpadhid.hid.BluetoothHidController
import com.example.foldpadhid.hid.HidConnectionState
import com.example.foldpadhid.shizuku.ShizukuDisplayState
import kotlin.math.roundToInt

@SuppressLint("MissingPermission")
@Composable
fun TrackpadScreen(
    hidState: HidConnectionState,
    shizukuState: ShizukuDisplayState,
    hidController: BluetoothHidController,
    onRequestPermissions: () -> Unit,
    onToggleDualScreen: (Boolean) -> Unit
) {
    val sensitivity = 1.40f
    val tapToClick = true
    var activeModifiers by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header Status Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "FoldPadHID",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = hidState.connectedDevice?.name ?: "Waiting for Bluetooth HID Host",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRequestPermissions) {
                    Text("Permissions")
                }
                Button(
                    onClick = { onToggleDualScreen(!shizukuState.coverDisplayActive) },
                    enabled = shizukuState.serviceBound
                ) {
                    Text(if (shizukuState.coverDisplayActive) "Cover ON" else "Cover Dual-Screen")
                }
            }
        }

        // Precision Multi-Touch Trackpad Surface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF1E293B), RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                .pointerInput(tapToClick) {
                    detectTapGestures(
                        onTap = {
                            if (tapToClick) {
                                hidController.sendMouseReport(buttons = 0x01, dx = 0, dy = 0)
                                hidController.sendMouseReport(buttons = 0x00, dx = 0, dy = 0)
                            }
                        }
                    )
                }
                .pointerInput(sensitivity) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val dx = (dragAmount.x * sensitivity).roundToInt()
                        val dy = (dragAmount.y * sensitivity).roundToInt()
                        if (dx != 0 || dy != 0) {
                            hidController.sendMouseReport(buttons = 0x00, dx = dx, dy = dy)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Trackpad Surface",
                    color = Color(0xFFCBD5E1),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = hidState.lastReportHex,
                    color = Color(0xFF64748B),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        }

        // Left / Middle / Right Mouse Buttons
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp),
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
