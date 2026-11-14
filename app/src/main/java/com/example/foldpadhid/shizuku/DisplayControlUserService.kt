package com.example.foldpadhid.shizuku

import android.annotation.SuppressLint
import android.os.Build
import android.os.IBinder
import dalvik.system.PathClassLoader
import kotlin.system.exitProcess

/**
 * Runs inside Shizuku's privileged shell process (UID 2000).
 * Keeps the Galaxy Z Fold 5 outer cover display powered on while unfolded
 * so the inner 7.6" panel acts as Trackpad/Keyboard and the outer 6.2" panel
 * acts as a dedicated Macro Deck.
 */
@SuppressLint("PrivateApi", "BlockedPrivateApi")
class DisplayControlUserService : IDisplayControlService.Stub() {

    private val displayControlClass: Class<*>? by lazy {
        if (Build.VERSION.SDK_INT >= 34) {
            runCatching {
                PathClassLoader("/system/framework/services.jar", ClassLoader.getSystemClassLoader())
                    .loadClass("com.android.server.display.DisplayControl")
            }.getOrNull()
        } else {
            null
        }
    }

    private val surfaceControlClass: Class<*> by lazy {
        Class.forName("android.view.SurfaceControl")
    }

    override fun getPhysicalDisplayIds(): LongArray {
        val scIds = runCatching {
            surfaceControlClass.getMethod("getPhysicalDisplayIds").invoke(null) as? LongArray
        }.getOrNull()
        if (scIds != null && scIds.isNotEmpty()) return scIds

        val dcIds = runCatching {
            displayControlClass?.getMethod("getPhysicalDisplayIds")?.invoke(null) as? LongArray
        }.getOrNull()
        return dcIds ?: longArrayOf()
    }

    private fun getPhysicalDisplayToken(physicalId: Long): IBinder? {
        val scToken = runCatching {
            surfaceControlClass
                .getMethod("getPhysicalDisplayToken", Long::class.javaPrimitiveType)
                .invoke(null, physicalId) as? IBinder
        }.getOrNull()
        if (scToken != null) return scToken

        return runCatching {
            displayControlClass
                ?.getMethod("getPhysicalDisplayToken", Long::class.javaPrimitiveType)
                ?.invoke(null, physicalId) as? IBinder
        }.getOrNull()
    }

    override fun setPhysicalDisplayPowerMode(physicalDisplayId: Long, mode: Int): Boolean {
        return runCatching {
            val token = getPhysicalDisplayToken(physicalDisplayId) ?: return false
            val setPower = surfaceControlClass.getMethod(
                "setDisplayPowerMode",
                IBinder::class.java,
                Int::class.javaPrimitiveType
            )
            setPower.invoke(null, token, mode)
            true
        }.getOrDefault(false)
    }

    override fun setDisplayBrightness(physicalDisplayId: Long, brightness: Float): Boolean {
        return runCatching {
            val token = getPhysicalDisplayToken(physicalDisplayId) ?: return false
            val setBright = surfaceControlClass.getMethod(
                "setDisplayBrightness",
                IBinder::class.java,
                Float::class.javaPrimitiveType
            )
            setBright.invoke(null, token, brightness.coerceIn(0.05f, 1.0f))
            true
        }.getOrDefault(false)
    }

    override fun destroy() {
        exitProcess(0)
    }
}
