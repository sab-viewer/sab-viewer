package com.sab_engineering.tools.sab_viewer.controller;

import com.sab_engineering.tools.sab_viewer.io.LinePreview;

import java.util.List;

public class ViewerContent {
    private final List<LinePreview> lines;
    private final int firstDisplayedLine;
    private final long firstDisplayedColumn;

    public ViewerContent(List<LinePreview> lines, int firstDisplayedLine, long firstDisplayedColumn) {
        this.lines = lines;
        this.firstDisplayedLine = firstDisplayedLine;
        this.firstDisplayedColumn = firstDisplayedColumn;
    }

    public List<LinePreview> getLines() {
        return lines;
    }

    public int getFirstDisplayedLine() {
        return firstDisplayedLine;
    }

    public long getFirstDisplayedColumn() {
        return firstDisplayedColumn;
    }
}
