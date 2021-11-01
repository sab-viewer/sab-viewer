package com.sab_engineering.tools.sab_viewer.gui;

import com.sab_engineering.tools.sab_viewer.io.LineContent;

import java.util.Collection;
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

    // Open file
    void onOpenFile(final String filePath, final Consumer<Collection<LineContent>> linesConsumer, final Consumer<MessageInfo> messageConsumer);

    // Navigation
    void onGoOneLineUp(final Consumer<Collection<LineContent>> linesConsumer);
    void onGoOneLineDown(final Consumer<Collection<LineContent>> linesConsumer);
    void onGoOneColumnLeft(final Consumer<Collection<LineContent>> linesConsumer);
    void onGoOneColumnRight(final Consumer<Collection<LineContent>> linesConsumer);
    void onGoOnePageUp(final Consumer<Collection<LineContent>> linesConsumer);
    void onGoOnePageDown(final Consumer<Collection<LineContent>> linesConsumer);
    void onGoOnePageLeft(final Consumer<Collection<LineContent>> linesConsumer);
    void onGoOnePageRight(final Consumer<Collection<LineContent>> linesConsumer);
    void onGoToLineBegin(final Consumer<Collection<LineContent>> linesConsumer);
    void onGoToLineEnd(final Consumer<Collection<LineContent>> linesConsumer);
    void onGoToFirstLine(final Consumer<Collection<LineContent>> linesConsumer);
    void onGoToLastLine(final Consumer<Collection<LineContent>> linesConsumer);
    void onLargeJumpUp(final Consumer<Collection<LineContent>> linesConsumer);
    void onLargeJumpDown(final Consumer<Collection<LineContent>> linesConsumer);
    void onLargeJumpLeft(final Consumer<Collection<LineContent>> linesConsumer);
    void onLargeJumpRight(final Consumer<Collection<LineContent>> linesConsumer);
    void onGoTo(final int line, final int column, final Consumer<Collection<LineContent>> linesConsumer);
}
