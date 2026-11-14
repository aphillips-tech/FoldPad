package com.example.foldpadhid.shizuku;

interface IDisplayControlService {
    long[] getPhysicalDisplayIds() = 1;
    boolean setPhysicalDisplayPowerMode(long physicalDisplayId, int mode) = 2;
    boolean setDisplayBrightness(long physicalDisplayId, float brightness) = 3;
    void destroy() = 16777114;
}
