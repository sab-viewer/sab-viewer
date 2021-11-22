package com.sab_engineering.tools.sab_viewer.controller;

public class ScannerState {
    private final int linesScanned;
    private final long bytesScanned;
    private final boolean finished;
    private final long usedMemory;
    private final long totalMemory;
    private final long maxMemory;
    private final boolean outOfMemory;

    public ScannerState(int linesScanned, long bytesScanned, boolean finished, long usedMemory, long totalMemory, long maxMemory, boolean outOfMemory) {
        this.linesScanned = linesScanned;
        this.bytesScanned = bytesScanned;
        this.finished = finished;
        this.usedMemory = usedMemory;
        this.totalMemory = totalMemory;
        this.maxMemory = maxMemory;
        this.outOfMemory = outOfMemory;
    }

    public int getLinesScanned() {
        return linesScanned;
    }

    public long getBytesScanned() {
        return bytesScanned;
    }

    public boolean isFinished() {
        return finished;
    }

    public long getUsedMemory() {
        return usedMemory;
    }

    public long getTotalMemory() {
        return totalMemory;
    }

    public long getMaxMemory() {
        return maxMemory;
    }

    public boolean isOutOfMemory() {
        return outOfMemory;
    }
}
