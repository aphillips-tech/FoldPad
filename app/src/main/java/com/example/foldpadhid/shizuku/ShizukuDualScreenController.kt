package com.example.foldpadhid.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.IBinder
import android.view.Display
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

data class ShizukuDisplayState(
    val shizukuAvailable: Boolean = false,
    val shizukuAuthorized: Boolean = false,
    val serviceBound: Boolean = false,
    val physicalDisplayIds: List<Long> = emptyList(),
    val coverDisplayActive: Boolean = false,
    val lastError: String? = null
)

class ShizukuDualScreenController(private val context: Context) {

    private val _state = MutableStateFlow(ShizukuDisplayState())
    val state: StateFlow<ShizukuDisplayState> = _state.asStateFlow()

    private var service: IDisplayControlService? = null

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, DisplayControlUserService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("display_ctrl")
        .debuggable(false)
        .version(1)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IDisplayControlService.Stub.asInterface(binder)
            val ids = runCatching { service?.physicalDisplayIds?.toList().orEmpty() }.getOrDefault(emptyList())
            _state.value = _state.value.copy(
                serviceBound = service != null,
                physicalDisplayIds = ids,
                lastError = null
            )
            if (true) {
                setCoverScreenAwake(true, 0.65f)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            _state.value = _state.value.copy(serviceBound = false, coverDisplayActive = false)
        }
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        val granted = grantResult == PackageManager.PERMISSION_GRANTED
        _state.value = _state.value.copy(shizukuAuthorized = granted)
        if (granted) bindDisplayService()
    }

    fun register() {
        Shizuku.addRequestPermissionResultListener(permissionListener)
        refreshAvailability()
    }

    fun unregister() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        runCatching { Shizuku.unbindUserService(userServiceArgs, connection, true) }
    }

    fun refreshAvailability() {
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val granted = binderAlive &&
            runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        _state.value = _state.value.copy(
            shizukuAvailable = binderAlive,
            shizukuAuthorized = granted
        )
        if (granted && service == null) {
            bindDisplayService()
        }
    }

    fun requestPermission() {
        if (Shizuku.pingBinder()) {
            Shizuku.requestPermission(1001)
        }
    }

    fun bindDisplayService() {
        runCatching {
            Shizuku.bindUserService(userServiceArgs, connection)
        }.onFailure { err ->
            _state.value = _state.value.copy(lastError = err.message)
        }
    }

    fun setCoverScreenAwake(awake: Boolean, brightness: Float = 0.65f) {
        val svc = service ?: return
        val ids = _state.value.physicalDisplayIds
        // On Samsung Galaxy Z Fold 5: index 0 is primary inner display, index 1 is outer cover panel
        val coverId = ids.getOrNull(1) ?: return
        val mode = if (awake) 2 /* POWER_MODE_NORMAL */ else 0 /* POWER_MODE_OFF */
        val ok = runCatching {
            val powered = svc.setPhysicalDisplayPowerMode(coverId, mode)
            if (powered && awake) {
                svc.setDisplayBrightness(coverId, brightness)
            }
            powered
        }.getOrDefault(false)
        _state.value = _state.value.copy(coverDisplayActive = ok && awake)
    }

    fun findSecondaryDisplay(): Display? {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val builtIn = runCatching {
            dm.getDisplays("com.samsung.android.hardware.display.category.BUILTIN").toList()
        }.getOrDefault(emptyList())
        return builtIn.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
            ?: dm.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
    }
}
