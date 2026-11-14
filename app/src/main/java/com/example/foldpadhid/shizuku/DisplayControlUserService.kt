package com.example.foldpadhid.shizuku

import android.annotation.SuppressLint
import android.os.Build
import android.os.IBinder
import kotlin.system.exitProcess

@SuppressLint("PrivateApi", "BlockedPrivateApi")
class DisplayControlUserService : IDisplayControlService.Stub() {

    private fun resolveDisplayControlClass(): Class<*>? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                Class.forName("com.android.server.display.DisplayControl")
            } else {
                null
            }
        }.getOrNull() ?: runCatching {
            Class.forName("android.view.SurfaceControl")
        }.getOrNull()
    }

    override fun getPhysicalDisplayIds(): LongArray {
        val surfaceControl = Class.forName("android.view.SurfaceControl")
        val method = runCatching {
            surfaceControl.getMethod("getPhysicalDisplayIds")
        }.getOrNull() ?: resolveDisplayControlClass()?.getMethod("getPhysicalDisplayIds")

        return (method?.invoke(null) as? LongArray) ?: longArrayOf()
    }

    private fun getPhysicalDisplayToken(physicalId: Long): IBinder? {
        val surfaceControl = Class.forName("android.view.SurfaceControl")
        val scMethod = runCatching {
            surfaceControl.getMethod("getPhysicalDisplayToken", Long::class.javaPrimitiveType)
        }.getOrNull()
        if (scMethod != null) {
            return scMethod.invoke(null, physicalId) as? IBinder
        }
        val dcClass = resolveDisplayControlClass() ?: return null
        val dcMethod = runCatching {
            dcClass.getMethod("getPhysicalDisplayToken", Long::class.javaPrimitiveType)
        }.getOrNull()
        return dcMethod?.invoke(null, physicalId) as? IBinder
    }

    override fun setPhysicalDisplayPowerMode(physicalDisplayId: Long, mode: Int): Boolean {
        return runCatching {
            val token = getPhysicalDisplayToken(physicalDisplayId) ?: return false
            val surfaceControl = Class.forName("android.view.SurfaceControl")
            val setPower = surfaceControl.getMethod(
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
            val surfaceControl = Class.forName("android.view.SurfaceControl")
            val setBright = surfaceControl.getMethod(
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
