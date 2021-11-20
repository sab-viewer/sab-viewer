package com.sab_engineering.tools.sab_viewer.controller;

import java.util.Objects;

public class ViewerSettings {

    private int displayedLines;
    private int displayedColumns;

    private int firstDisplayedLineIndex; // index in lineStatistics
    private long firstDisplayedColumnIndex;

    public ViewerSettings(int displayedLines, int displayedColumns, int firstDisplayedLineIndex, long firstDisplayedColumnIndex) {
        this.displayedLines = displayedLines;
        this.displayedColumns = displayedColumns;
        this.firstDisplayedLineIndex = firstDisplayedLineIndex;
        this.firstDisplayedColumnIndex = firstDisplayedColumnIndex;
    }

    public ViewerSettings(ViewerSettings other) {
        this.displayedLines = other.displayedLines;
        this.displayedColumns = other.displayedColumns;
        this.firstDisplayedLineIndex = other.firstDisplayedLineIndex;
        this.firstDisplayedColumnIndex = other.firstDisplayedColumnIndex;
    }

    public int getDisplayedLines() {
        return displayedLines;
    }

    public void setDisplayedLines(int displayedLines) {
        this.displayedLines = displayedLines;
    }

    public int getDisplayedColumns() {
        return displayedColumns;
    }

    public void setDisplayedColumns(int displayedColumns) {
        this.displayedColumns = displayedColumns;
    }

    public int getFirstDisplayedLineIndex() {
        return firstDisplayedLineIndex;
    }

    public void setFirstDisplayedLineIndex(int firstDisplayedLineIndex) {
        this.firstDisplayedLineIndex = firstDisplayedLineIndex;
    }

    public long getFirstDisplayedColumnIndex() {
        return firstDisplayedColumnIndex;
    }

    public void setFirstDisplayedColumnIndex(long firstDisplayedColumnIndex) {
        this.firstDisplayedColumnIndex = firstDisplayedColumnIndex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ViewerSettings that = (ViewerSettings) o;
        return displayedLines == that.displayedLines && displayedColumns == that.displayedColumns && firstDisplayedLineIndex == that.firstDisplayedLineIndex && firstDisplayedColumnIndex == that.firstDisplayedColumnIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(displayedLines, displayedColumns, firstDisplayedLineIndex, firstDisplayedColumnIndex);
    }
}
