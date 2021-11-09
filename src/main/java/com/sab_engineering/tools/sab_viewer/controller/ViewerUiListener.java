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
    void onGoOneLineUp(final Consumer<ViewerContent> contentConsumer);
    void onGoOneLineDown(final Consumer<ViewerContent> contentConsumer);
    void onGoOneColumnLeft(final Consumer<ViewerContent> contentConsumer);
    void onGoOneColumnRight(final Consumer<ViewerContent> contentConsumer);
    void onGoOnePageUp(final Consumer<ViewerContent> contentConsumer);
    void onGoOnePageDown(final Consumer<ViewerContent> contentConsumer);
    void onGoOnePageLeft(final Consumer<ViewerContent> contentConsumer);
    void onGoOnePageRight(final Consumer<ViewerContent> contentConsumer);
    void onGoToLineBegin(final Consumer<ViewerContent> contentConsumer);
    void onGoToLineEnd(final Consumer<ViewerContent> contentConsumer);
    void onGoToFirstLine(final Consumer<ViewerContent> contentConsumer);
    void onGoToLastLine(final Consumer<ViewerContent> contentConsumer);
    void onLargeJumpUp(final Consumer<ViewerContent> contentConsumer);
    void onLargeJumpDown(final Consumer<ViewerContent> contentConsumer);
    void onLargeJumpLeft(final Consumer<ViewerContent> contentConsumer);
    void onLargeJumpRight(final Consumer<ViewerContent> contentConsumer);
    void onGoTo(final int line, final int column, final Consumer<ViewerContent> contentConsumer);

    void resize(final int displayedLines, final int displayedColumns, final Consumer<ViewerContent> contentConsumer);

    void interruptBackgroundThreads();
}
