package com.example.foldpadhid.shizuku;

interface IDisplayControlService {
    /**
     * Queries physical display IDs known to SurfaceFlinger.
     */
    long[] getPhysicalDisplayIds() = 1;

    /**
     * Sets power mode on a physical display (0 = OFF, 2 = ON, 1 = DOZE).
     * Used on Galaxy Z Fold 5 to keep Cover Display awake while unfolded.
     */
    boolean setPhysicalDisplayPowerMode(long physicalDisplayId, int mode) = 2;

    /**
     * Adjusts display brightness (0.0f .. 1.0f) via SurfaceControl.
     */
    boolean setDisplayBrightness(long physicalDisplayId, float brightness) = 3;

    /**
     * Releases privileged service cleanly (Shizuku destroy transaction code).
     */
    void destroy() = 16777114;
}
