package com.sab_engineering.tools.sab_viewer.gui;

import com.sab_engineering.tools.sab_viewer.io.LineContent;
import com.sab_engineering.tools.sab_viewer.io.LineStatistics;
import com.sab_engineering.tools.sab_viewer.io.Reader;
import com.sab_engineering.tools.sab_viewer.io.Scanner;

import javax.swing.JOptionPane;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;

public class ViewerController {
    private Optional<String> maybeFilename;

    private final int lineCount = 50; // TODO: This should be changed on window resize (for now it is final just to disable warning)
    private final int lineLength = 150;

    private int firstDisplayedLineIndex; // index in lineStatistics
    private int lineOffset; // vertical scrolling

    private ViewerUi ui;

    private final Semaphore initialContentLock;
    private List<LineContent> initialContent;

    private final List<LineStatistics> lineStatistics;

    private IOException scannerException = null;

    public ViewerController() {
        firstDisplayedLineIndex = 0;
        lineOffset = 0;
        initialContentLock = new Semaphore(1);
        initialContent = new ArrayList<>(lineCount);
        lineStatistics = new ArrayList<>(100000);
    }

    void setUi(final ViewerUi ui) {
        this.ui = ui;
    }

    public void openFile(final String fileName) {
        this.maybeFilename = Optional.of(fileName);
        Thread scannerThread = new Thread(
                () -> {
                    try {
                        Scanner.scanFile(fileName, this::addInitialContent, lineLength, this::addLineStatistics);
                    } catch (IOException ioException) {
                        clearInitialContent();
                        scannerException = ioException;
                    }
                },
                "Scanner"
        );
        scannerThread.start();

        int consumedLinesCount = 0;
        final List<LineContent> readLines = new ArrayList<>();
        while (scannerThread.isAlive() || (consumedLinesCount < lineCount && initialContent != null)) {
            try {
                initialContentLock.acquire();
                try {
                    while (initialContent != null && consumedLinesCount < initialContent.size()) {
                        final LineContent newLine = initialContent.get(consumedLinesCount);
                        consumedLinesCount += 1;
                        readLines.add(newLine);
                        final List<LineContent> readLinesCopy = new ArrayList<>(readLines);
                        ui.setLines(readLinesCopy);
                    }
                } finally {
                    initialContentLock.release();
                }
                //noinspection BusyWait
                Thread.sleep(10);
            } catch (InterruptedException e) {
                scannerThread.interrupt();
            }
        }

        if (scannerException != null) {
            throw displayAndCreateException(scannerException, "scan");
        }
    }

    public void update() {

        if (maybeFilename.isEmpty()) {
            return;
        }
        final String fileName = maybeFilename.get();

        clearInitialContent();

        List<LineStatistics> linesToRead;
        synchronized (lineStatistics) {
            if (lineStatistics.isEmpty()) {
                return;
            }
            if (firstDisplayedLineIndex >= lineStatistics.size()) {
                firstDisplayedLineIndex = lineStatistics.size() - 1;
            }
            final int oneAfterLastLineIndex = Math.min(lineStatistics.size(), firstDisplayedLineIndex + lineCount);
            linesToRead = new ArrayList<>(lineStatistics.subList(firstDisplayedLineIndex, oneAfterLastLineIndex));
        }

        final List<LineContent> lineContents;
        try {
            lineContents = Reader.readSpecificLines(fileName, linesToRead, lineOffset, lineLength);
        } catch (IOException ioException) {
            throw displayAndCreateException(ioException, "read");
        }
        ui.setLines(lineContents); // For now it is done synchronously, so we don't need copy of the list
    }

    // prevent further updates by scanner
    private void clearInitialContent() {
        if (initialContent != null) {
            try {
                initialContentLock.acquire();
                try {
                    initialContent = null;
                } finally {
                    initialContentLock.release();
                }
            } catch (InterruptedException e) {
                throw new IllegalStateException("Unable to get content lock", e);
            }
        }
    }

    private void addInitialContent(LineContent lineContent) {
        if (initialContent != null) {
            try {
                    initialContentLock.acquire();
                    try {
                        if (initialContent != null && initialContent.size() < lineCount) {
                            initialContent.add(lineContent);
                        }
                    } finally {
                        initialContentLock.release();
                    }
            } catch (InterruptedException e) {
                throw new IllegalStateException("Unable to get content lock", e);
            }
        }
    }

    private void addLineStatistics(LineStatistics statistics) {
        synchronized (lineStatistics) {
            lineStatistics.add(statistics);
        }
    }

    private void moveVertical(int offset) {
        firstDisplayedLineIndex += offset;
        if (firstDisplayedLineIndex < 0) {
            firstDisplayedLineIndex = 0;
        }
        update();
    }

    private void moveHorizontal(int offset) {
        lineOffset += offset;
        if (lineOffset < 0) {
            lineOffset = 0;
        }
        update();
    }

    private UncheckedIOException displayAndCreateException(IOException exception, String verb)  {
        String message = "Unable to " + verb + " file '" + maybeFilename + "': " + exception.getClass().getSimpleName();
        ui.showMessageDialog(message, "Unable to " + verb + " file", JOptionPane.ERROR_MESSAGE);
        return new UncheckedIOException(message, exception);
    }

    public ViewerUiListener getViewerListener() {
        return new UiListenerImpl(); // contains reference to "this"
    }

    /* NOT STATIC */ public class UiListenerImpl implements ViewerUiListener {

        @Override
        public void setUi(final ViewerUi ui) {
            ViewerController.this.setUi(ui);
        }

        @Override
        public void onOpenFile(final String filePath) {
            openFile(filePath);
        }

        @Override
        public void onGoOneLineUp() {
            moveVertical(-1);
        }

        @Override
        public void onGoOneLineDown() {
            moveVertical(1);
        }

        @Override
        public void onGoOneColumnLeft() {
            moveHorizontal(-1);
        }

        @Override
        public void onGoOneColumnRight() {
            moveHorizontal(1);
        }

        @Override
        public void onGoOnePageUp() {
            moveVertical((lineCount - 1) * -1);
        }

        @Override
        public void onGoOnePageDown() {
            moveVertical((lineCount - 1));
        }

        @Override
        public void onGoOnePageLeft() {
            // TODO: Not Implemented Yet
        }

        @Override
        public void onGoOnePageRight() {
            // TODO: Not Implemented Yet
        }

        @Override
        public void onGoLineBegin() {
            // TODO: Not Implemented Yet
        }

        @Override
        public void onGoLineEnd() {
            // TODO: Not Implemented Yet
        }

        @Override
        public void onGoHome() {
            // TODO: Not Implemented Yet
        }

        @Override
        public void onGoToEnd() {
            // TODO: Not Implemented Yet
        }

        @Override
        public void onLargeJumpUp() {
            // TODO: Not Implemented Yet
        }

        @Override
        public void onLargeJumpDown() {
            // TODO: Not Implemented Yet
        }

        @Override
        public void onLargeJumpLeft() {
            // TODO: Not Implemented Yet
        }

        @Override
        public void onLargeJumpRight() {
            // TODO: Not Implemented Yet
        }
    }

}
