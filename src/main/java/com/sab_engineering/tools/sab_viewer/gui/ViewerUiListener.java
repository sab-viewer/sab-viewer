package com.sab_engineering.tools.sab_viewer.gui;

/**
 * Purpose of this interface is to maintain the list of operations available to User via UI of any kind.
 *
 * <p>
 *     Controller should provide implementation of this interface and pass it to "ui" class. UI-class
 *     should connect those callbacks listed below to the gui events.
 * </p>
 */
public interface ViewerUiListener {

    // Register ui by controller
    void setUi(final ViewerUi ui);

    // Open file
    void onOpenFile(final String filePath);

    // Navigation
    void onGoOneLineUp();
    void onGoOneLineDown();
    void onGoOneColumnLeft();
    void onGoOneColumnRight();
    void onGoOnePageUp();
    void onGoOnePageDown();
    void onGoOnePageLeft();
    void onGoOnePageRight();
    void onGoLineBegin();
    void onGoLineEnd();
    void onGoHome();
    void onGoToEnd();
    void onLargeJumpUp();
    void onLargeJumpDown();
    void onLargeJumpLeft();
    void onLargeJumpRight();

}
