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

data class HidConnectionState(
    val registered: Boolean = false,
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

    private val _state = MutableStateFlow(HidConnectionState())
    val state: StateFlow<HidConnectionState> = _state.asStateFlow()

    private val sdpSettings = BluetoothHidDeviceAppSdpSettings(
        "FoldPadHID",
        "Galaxy Z Fold Dual-Screen Trackpad & Keyboard",
        "FoldPadHID",
        BluetoothHidDevice.SUBCLASS1_COMBO,
        HidDescriptors.COMPOSITE_DESCRIPTOR
    )

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            _state.value = _state.value.copy(
                registered = registered,
                bondedDevices = adapter?.bondedDevices?.toList().orEmpty()
            )
            if (registered && pluggedDevice != null) {
                hidDevice?.connect(pluggedDevice)
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            val active = if (state == BluetoothProfile.STATE_CONNECTED) device else null
            _state.value = _state.value.copy(
                connectedDevice = active,
                bondedDevices = adapter?.bondedDevices?.toList().orEmpty()
            )
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as? BluetoothHidDevice
                hidDevice?.registerApp(sdpSettings, null, null, executor, hidCallback)
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                _state.value = _state.value.copy(registered = false, connectedDevice = null)
            }
        }
    }

    fun start() {
        adapter?.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
    }

    fun stop() {
        hidDevice?.unregisterApp()
        adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        hidDevice = null
    }

    fun connectHost(device: BluetoothDevice) {
        hidDevice?.connect(device)
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
