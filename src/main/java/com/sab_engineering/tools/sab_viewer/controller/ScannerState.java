package com.sab_engineering.tools.sab_viewer.controller;

public class ScannerState {
    private final int linesScanned;
    private final long bytesScanned;
    private final boolean finished;

    public ScannerState(int linesScanned, long bytesScanned, boolean finished) {
        this.linesScanned = linesScanned;
        this.bytesScanned = bytesScanned;
        this.finished = finished;
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
}
