package com.example.foldpadhid

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.foldpadhid.hid.BluetoothHidController
import com.example.foldpadhid.shizuku.ShizukuDualScreenController
import com.example.foldpadhid.ui.CoverMacroPresentation
import com.example.foldpadhid.ui.TrackpadScreen

class MainActivity : ComponentActivity() {

    private lateinit var hidController: BluetoothHidController
    private lateinit var shizukuController: ShizukuDualScreenController
    private var coverPresentation: CoverMacroPresentation? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            hidController.start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hidController = BluetoothHidController(this)
        shizukuController = ShizukuDualScreenController(this)
        shizukuController.register()

        requestBluetoothPermissions()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val hidState by hidController.state.collectAsState()
                val shizukuState by shizukuController.state.collectAsState()

                TrackpadScreen(
                    hidState = hidState,
                    shizukuState = shizukuState,
                    hidController = hidController,
                    onRequestPermissions = { requestBluetoothPermissions() },
                    onToggleDualScreen = { enable ->
                        shizukuController.setCoverScreenAwake(enable)
                        if (enable) showCoverPresentation() else dismissCoverPresentation()
                    }
                )
            }
        }
    }

    private fun requestBluetoothPermissions() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
        }
        permissionLauncher.launch(perms)
    }

    private fun showCoverPresentation() {
        if (coverPresentation != null) return
        val secondary = shizukuController.findSecondaryDisplay() ?: return
        coverPresentation = CoverMacroPresentation(
            outerContext = this,
            display = secondary,
            hidController = hidController,
            lifecycleOwner = this,
            savedStateOwner = this
        ).also {
            it.show()
        }
    }

    private fun dismissCoverPresentation() {
        coverPresentation?.dismiss()
        coverPresentation = null
    }

    override fun onDestroy() {
        dismissCoverPresentation()
        shizukuController.unregister()
        hidController.stop()
        super.onDestroy()
    }
}
