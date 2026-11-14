package com.example.foldpadhid.hid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

enum class HidProfileMode {
    COMBO,
    GAMEPAD
}

data class HidConnectionState(
    val profileMode: HidProfileMode = HidProfileMode.COMBO,
    val registered: Boolean = false,
    val connectionStatus: String = "Idle",
    val connectedDevice: BluetoothDevice? = null,
    val bondedDevices: List<BluetoothDevice> = emptyList(),
    val lastReportHex: String = "--"
)

@SuppressLint("MissingPermission")
class BluetoothHidController(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private val executor = Executors.newSingleThreadExecutor()

    private var hidDevice: BluetoothHidDevice? = null
    private var pendingModeSwitch: HidProfileMode? = null
    private var lastTargetDevice: BluetoothDevice? = null

    private val _state = MutableStateFlow(HidConnectionState())
    val state: StateFlow<HidConnectionState> = _state.asStateFlow()

    private fun buildSdpSettings(mode: HidProfileMode): BluetoothHidDeviceAppSdpSettings {
        return when (mode) {
            HidProfileMode.COMBO -> BluetoothHidDeviceAppSdpSettings(
                "FoldPadHID",
                "Galaxy Z Fold Dual-Screen Trackpad & Keyboard",
                "FoldPadHID",
                BluetoothHidDevice.SUBCLASS1_COMBO,
                HidDescriptors.COMPOSITE_DESCRIPTOR
            )
            HidProfileMode.GAMEPAD -> BluetoothHidDeviceAppSdpSettings(
                "FoldPadHID Pad",
                "Bluetooth HID Dual-Stick Gamepad Controller",
                "FoldPadHID",
                BluetoothHidDevice.SUBCLASS2_GAMEPAD,
                HidDescriptors.GAMEPAD_DESCRIPTOR
            )
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            val nextSwitch = pendingModeSwitch
            if (!registered && nextSwitch != null) {
                pendingModeSwitch = null
                _state.value = _state.value.copy(
                    profileMode = nextSwitch,
                    registered = false,
                    connectionStatus = "Registering ${nextSwitch.name} HID profile..."
                )
                hidDevice?.registerApp(buildSdpSettings(nextSwitch), null, null, executor, this)
                return
            }

            _state.value = _state.value.copy(
                registered = registered,
                connectionStatus = if (registered) {
                    if (_state.value.profileMode == HidProfileMode.GAMEPAD) {
                        "Gamepad Profile Active (Re-pair PC once if OS cached Trackpad)"
                    } else {
                        "Trackpad + Keyboard Profile Active"
                    }
                } else {
                    "Unregistered"
                },
                bondedDevices = safeBondedDevices()
            )
            val target = pluggedDevice ?: lastTargetDevice
            if (registered && target != null) {
                hidDevice?.connect(target)
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            val active = if (state == BluetoothProfile.STATE_CONNECTED) device else null
            if (active != null) lastTargetDevice = active
            val statusText = when (state) {
                BluetoothProfile.STATE_CONNECTED -> "Connected (${_state.value.profileMode.name}) to ${device?.name ?: "Host"}"
                BluetoothProfile.STATE_CONNECTING -> "Connecting to ${device?.name ?: "Host"}..."
                BluetoothProfile.STATE_DISCONNECTING -> "Disconnecting..."
                else -> "Ready (${_state.value.profileMode.name}) — Tap a paired host or Pair"
            }
            _state.value = _state.value.copy(
                connectedDevice = active,
                connectionStatus = statusText,
                bondedDevices = safeBondedDevices()
            )
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as? BluetoothHidDevice
                hidDevice?.registerApp(buildSdpSettings(_state.value.profileMode), null, null, executor, hidCallback)
                refreshBondedDevices()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                _state.value = _state.value.copy(
                    registered = false,
                    connectedDevice = null,
                    connectionStatus = "HID Service Disconnected"
                )
            }
        }
    }

    private fun safeBondedDevices(): List<BluetoothDevice> {
        return runCatching { adapter?.bondedDevices?.toList().orEmpty() }.getOrDefault(emptyList())
    }

    fun refreshBondedDevices() {
        _state.value = _state.value.copy(bondedDevices = safeBondedDevices())
    }

    fun start() {
        refreshBondedDevices()
        adapter?.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
    }

    fun stop() {
        runCatching { hidDevice?.unregisterApp() }
        runCatching { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice) }
        hidDevice = null
    }

    fun connectHost(device: BluetoothDevice) {
        lastTargetDevice = device
        _state.value = _state.value.copy(connectionStatus = "Connecting to ${device.name ?: device.address}...")
        hidDevice?.connect(device)
    }

    fun disconnectHost(device: BluetoothDevice) {
        hidDevice?.disconnect(device)
    }

    fun switchProfileMode(targetMode: HidProfileMode) {
        if (_state.value.profileMode == targetMode) return
        val device = hidDevice
        if (device == null) {
            _state.value = _state.value.copy(profileMode = targetMode)
            return
        }
        lastTargetDevice = _state.value.connectedDevice ?: lastTargetDevice
        if (_state.value.registered) {
            pendingModeSwitch = targetMode
            _state.value = _state.value.copy(
                profileMode = targetMode,
                connectionStatus = "Switching HID descriptor to ${targetMode.name}..."
            )
            device.unregisterApp()
        } else {
            _state.value = _state.value.copy(profileMode = targetMode)
            device.registerApp(buildSdpSettings(targetMode), null, null, executor, hidCallback)
        }
    }

    fun sendGamepadReport(
        buttonsMask: Int,
        hat: Int,
        lx: Int,
        ly: Int,
        rx: Int,
        ry: Int,
        l2: Int,
        r2: Int
    ) {
        val payload = byteArrayOf(
            (buttonsMask and 0xFF).toByte(),
            ((buttonsMask ushr 8) and 0xFF).toByte(),
            (hat and 0x0F).toByte(),
            lx.coerceIn(-127, 127).toByte(),
            ly.coerceIn(-127, 127).toByte(),
            rx.coerceIn(-127, 127).toByte(),
            ry.coerceIn(-127, 127).toByte(),
            l2.coerceIn(0, 255).toByte(),
            r2.coerceIn(0, 255).toByte()
        )
        dispatchReport(HidDescriptors.REPORT_ID_GAMEPAD, payload)
    }

    fun sendMouseReport(buttons: Int, dx: Int, dy: Int, wheel: Int = 0, pan: Int = 0) {
        val payload = byteArrayOf(
            (buttons and 0x1F).toByte(),
            dx.coerceIn(-127, 127).toByte(),
            dy.coerceIn(-127, 127).toByte(),
            wheel.coerceIn(-127, 127).toByte(),
            pan.coerceIn(-127, 127).toByte()
        )
        dispatchReport(HidDescriptors.REPORT_ID_MOUSE, payload)
    }

    fun sendKeyTap(modifierMask: Int, usageCode: Int) {
        val press = byteArrayOf(
            (modifierMask and 0xFF).toByte(),
            0x00,
            (usageCode and 0xFF).toByte(),
            0x00, 0x00, 0x00, 0x00, 0x00
        )
        val release = ByteArray(8)
        dispatchReport(HidDescriptors.REPORT_ID_KEYBOARD, press)
        dispatchReport(HidDescriptors.REPORT_ID_KEYBOARD, release)
    }

    fun sendAsciiChar(ch: Char, extraModifiers: Int = 0) {
        val mapped = asciiToHidUsage(ch) ?: return
        val (shiftBit, usage) = mapped
        sendKeyTap(extraModifiers or shiftBit, usage)
    }

    private fun asciiToHidUsage(ch: Char): Pair<Int, Int>? {
        val shift = 0x02
        return when (ch) {
            in 'a'..'z' -> 0 to (0x04 + (ch - 'a'))
            in 'A'..'Z' -> shift to (0x04 + (ch - 'A'))
            '1' -> 0 to 0x1E
            '!' -> shift to 0x1E
            '2' -> 0 to 0x1F
            '@' -> shift to 0x1F
            '3' -> 0 to 0x20
            '#' -> shift to 0x20
            '4' -> 0 to 0x21
            '$' -> shift to 0x21
            '5' -> 0 to 0x22
            '%' -> shift to 0x22
            '6' -> 0 to 0x23
            '^' -> shift to 0x23
            '7' -> 0 to 0x24
            '&' -> shift to 0x24
            '8' -> 0 to 0x25
            '*' -> shift to 0x25
            '9' -> 0 to 0x26
            '(' -> shift to 0x26
            '0' -> 0 to 0x27
            ')' -> shift to 0x27
            '\n' -> 0 to 0x28
            '\t' -> 0 to 0x2B
            ' ' -> 0 to 0x2C
            '-' -> 0 to 0x2D
            '_' -> shift to 0x2D
            '=' -> 0 to 0x2E
            '+' -> shift to 0x2E
            '[' -> 0 to 0x2F
            '{' -> shift to 0x2F
            ']' -> 0 to 0x30
            '}' -> shift to 0x30
            '\\' -> 0 to 0x31
            '|' -> shift to 0x31
            ';' -> 0 to 0x33
            ':' -> shift to 0x33
            '\'' -> 0 to 0x34
            '"' -> shift to 0x34
            '\u0060' -> 0 to 0x35
            '~' -> shift to 0x35
            ',' -> 0 to 0x36
            '<' -> shift to 0x36
            '.' -> 0 to 0x37
            '>' -> shift to 0x37
            '/' -> 0 to 0x38
            '?' -> shift to 0x38
            else -> null
        }
    }

    fun sendConsumerTap(usage16: Int) {
        val press = byteArrayOf(
            (usage16 and 0xFF).toByte(),
            ((usage16 ushr 8) and 0xFF).toByte()
        )
        val release = byteArrayOf(0x00, 0x00)
        dispatchReport(HidDescriptors.REPORT_ID_CONSUMER, press)
        dispatchReport(HidDescriptors.REPORT_ID_CONSUMER, release)
    }

    private fun dispatchReport(reportId: Int, data: ByteArray) {
        val host = _state.value.connectedDevice
        if (host != null) {
            hidDevice?.sendReport(host, reportId, data)
        }
        val hex = data.joinToString(" ") { "%02X".format(it) }
        _state.value = _state.value.copy(lastReportHex = "ID $reportId: $hex")
    }
}
