package com.sab_engineering.tools.sab_viewer.io;

public class LineStatistics {
    private final long startPositionInFile;
    private final long length;

    public LineStatistics(long startPositionInFile, long length) {
        this.startPositionInFile = startPositionInFile;
        this.length = length;
    }

    public long getStartPositionInFile() {
        return startPositionInFile;
    }

    public long getLength() {
        return length;
    }
}
