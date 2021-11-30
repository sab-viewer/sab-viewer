package com.sab_engineering.tools.sab_viewer.controller;

import java.util.function.Consumer;

/**
 * Purpose of this interface is to maintain the list of operations available to User via UI of any kind.
 *
 * <p>
 *     Controller should provide implementation of this interface and pass it to "ui" class. UI-class
 *     should connect those callbacks listed below to the gui events.
 * </p>
 */
public interface ViewerUiListener {

    // Navigation
    void onGoOneLineUp();
    void onGoOneLineDown();
    void onGoOneColumnLeft();
    void onGoOneColumnRight();
    void onGoOnePageUp();
    void onGoOnePageDown();
    void onGoOnePageLeft();
    void onGoOnePageRight();
    void onGoToLineBegin();
    void onGoToLineEnd();
    void onGoToFirstLine();
    void onGoToLastLine();
    void onLargeJumpUp();
    void onLargeJumpDown();
    void onLargeJumpLeft();
    void onLargeJumpRight();
    void onGoTo(final int line, final long column);

    void resize(final int displayedLines, final int displayedColumns);

    void moveToLocationOfSearchTerm(String literalSearchTerm);

    void interruptBackgroundThreads();
}
