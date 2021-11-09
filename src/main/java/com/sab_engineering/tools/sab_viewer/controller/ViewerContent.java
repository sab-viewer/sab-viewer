package com.sab_engineering.tools.sab_viewer.controller;

import com.sab_engineering.tools.sab_viewer.io.LineContent;

import java.util.Collection;

public class ViewerContent {
    private final Collection<LineContent> lines;
    private final int firstDisplayedLine;
    private final long firstDisplayedColumn;

    public ViewerContent(Collection<LineContent> lines, int firstDisplayedLine, long firstDisplayedColumn) {
        this.lines = lines;
        this.firstDisplayedLine = firstDisplayedLine;
        this.firstDisplayedColumn = firstDisplayedColumn;
    }

    public Collection<LineContent> getLines() {
        return lines;
    }

    public int getFirstDisplayedLine() {
        return firstDisplayedLine;
    }

    public long getFirstDisplayedColumn() {
        return firstDisplayedColumn;
    }
}
