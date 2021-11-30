package com.sab_engineering.tools.sab_viewer.controller;

public class ScannerState {
    private final int linesScanned;
    private final long bytesScanned;
    private final boolean finished;
    private final boolean stoppedBecauseOom;
    private final long usedMemory;
    private final long totalMemory;
    private final long maxMemory;

    public ScannerState(int linesScanned, long bytesScanned, boolean finished, boolean stoppedBecauseOom, long usedMemory, long totalMemory, long maxMemory) {
        this.linesScanned = linesScanned;
        this.bytesScanned = bytesScanned;
        this.finished = finished;
        this.stoppedBecauseOom = stoppedBecauseOom;
        this.usedMemory = usedMemory;
        this.totalMemory = totalMemory;
        this.maxMemory = maxMemory;
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

    public boolean isStoppedBecauseOom() {
        return stoppedBecauseOom;
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
}
