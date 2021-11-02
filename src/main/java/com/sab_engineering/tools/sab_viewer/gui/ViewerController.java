package com.sab_engineering.tools.sab_viewer.gui;

import com.sab_engineering.tools.sab_viewer.io.LineContent;
import com.sab_engineering.tools.sab_viewer.io.LineStatistics;
import com.sab_engineering.tools.sab_viewer.io.Reader;
import com.sab_engineering.tools.sab_viewer.io.Scanner;

import javax.swing.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

public class ViewerController {
    private Optional<String> maybeFilename;

    private final int currentlyDisplayedLines = 50; // TODO: This should be changed on window resize (for now it is final just to disable warning)
    private final int currentlyDisplayedColumns = 150;

    private final int largeLinesJump = currentlyDisplayedLines * 10; // TODO: Should be modifiable by user in settings
    private final int largeColumnsJump = currentlyDisplayedColumns * 10;

    private int firstDisplayedLineIndex; // index in lineStatistics
    private long firstDisplayedColumnIndex;

    private final Semaphore initialLinesLock;
    private List<LineContent> initialLines_toBeAccessedLocked;

    private final List<LineStatistics> lineStatistics_toBeAccessedSynchronized;

    private Reader reader;

    private Consumer<MessageInfo> messageConsumer;
    private IOException scannerException = null;

    public ViewerController() {
        firstDisplayedLineIndex = 0;
        firstDisplayedColumnIndex = 0;
        initialLinesLock = new Semaphore(1);
        initialLines_toBeAccessedLocked = new ArrayList<>(currentlyDisplayedLines);
        lineStatistics_toBeAccessedSynchronized = new ArrayList<>(100000);
    }

    public void openFile(final String fileName, final Consumer<Collection<LineContent>> linesConsumer, final Consumer<MessageInfo> messageConsumer) {

        // TODO: We need to handle the case, that it is subsequent "open", so we need to cancel current operations and clean the buffer before start scanning
        // OR just interrupt and then dump the entire Controller and create a new one

        this.maybeFilename = Optional.of(fileName);
        this.messageConsumer = messageConsumer;
        Thread scannerThread = new Thread(
                () -> {
                    try {
                        Scanner.scanFile(fileName, this::addInitialContent, currentlyDisplayedColumns, this::addLineStatistics);
                    } catch (IOException ioException) {
                        clearInitialContent();
                        scannerException = ioException;
                    }
                },
                "Scanner"
        );
        scannerThread.start();

        final List<LineContent> initialLinesRead = new ArrayList<>();
        while (scannerThread.isAlive() || (initialLinesRead.size() < currentlyDisplayedLines && initialLines_toBeAccessedLocked != null)) {
            try {
                initialLinesLock.acquire();
                try {
                    while (initialLines_toBeAccessedLocked != null && initialLinesRead.size() < initialLines_toBeAccessedLocked.size()) {
                        final LineContent newLine = initialLines_toBeAccessedLocked.get(initialLinesRead.size());
                        initialLinesRead.add(newLine);
                        linesConsumer.accept(new ArrayList<>(initialLinesRead));
                    }
                } finally {
                    initialLinesLock.release();
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

    public void update(final Consumer<Collection<LineContent>> linesConsumer) {

        if (maybeFilename.isEmpty()) {
            return;
        }
        final String fileName = maybeFilename.get();

        clearInitialContent();

        List<LineStatistics> linesToRead;
        synchronized (lineStatistics_toBeAccessedSynchronized) {
            if (lineStatistics_toBeAccessedSynchronized.isEmpty()) {
                return;
            }
            if (firstDisplayedLineIndex >= lineStatistics_toBeAccessedSynchronized.size()) {
                firstDisplayedLineIndex = lineStatistics_toBeAccessedSynchronized.size() - 1;
            }
            final int oneAfterLastLineIndex = Math.min(lineStatistics_toBeAccessedSynchronized.size(), firstDisplayedLineIndex + currentlyDisplayedLines);
            linesToRead = new ArrayList<>(lineStatistics_toBeAccessedSynchronized.subList(firstDisplayedLineIndex, oneAfterLastLineIndex));
        }

        final List<LineContent> lineContents;
        try {
            if (reader == null) {
                reader = new Reader(fileName);
            }
            lineContents = reader.readSpecificLines(linesToRead, firstDisplayedColumnIndex, currentlyDisplayedColumns);
        } catch (IOException ioException) {
            throw displayAndCreateException(ioException, "read");
        }
        linesConsumer.accept(lineContents);
    }

    // prevent further updates by scanner
    private void clearInitialContent() {
        if (initialLines_toBeAccessedLocked != null) {
            try {
                initialLinesLock.acquire();
                try {
                    initialLines_toBeAccessedLocked = null;
                } finally {
                    initialLinesLock.release();
                }
            } catch (InterruptedException e) {
                throw new IllegalStateException("Unable to get content lock", e);
            }
        }
    }

    private void addInitialContent(LineContent lineContent) {
        if (initialLines_toBeAccessedLocked != null) {
            try {
                    initialLinesLock.acquire();
                    try {
                        if (initialLines_toBeAccessedLocked != null && initialLines_toBeAccessedLocked.size() < currentlyDisplayedLines) {
                            initialLines_toBeAccessedLocked.add(lineContent);
                        }
                    } finally {
                        initialLinesLock.release();
                    }
            } catch (InterruptedException e) {
                throw new IllegalStateException("Unable to get content lock", e);
            }
        }
    }

    private void addLineStatistics(final LineStatistics statistics) {
        synchronized (lineStatistics_toBeAccessedSynchronized) {
            lineStatistics_toBeAccessedSynchronized.add(statistics);
        }
    }

    private void moveVertical(final int lineOffset, final Consumer<Collection<LineContent>> linesConsumer) {
        moveToPosition(firstDisplayedLineIndex + lineOffset, firstDisplayedColumnIndex, linesConsumer);
    }

    private void moveHorizontal(final long columnOffset, final Consumer<Collection<LineContent>> linesConsumer) {
        moveToPosition(firstDisplayedLineIndex, firstDisplayedColumnIndex + columnOffset, linesConsumer);
    }

    private void moveToPosition(final int firstDisplayedLineIndex, final long firstDisplayedColumnIndex, final Consumer<Collection<LineContent>> linesConsumer) {
        this.firstDisplayedLineIndex = Math.max(firstDisplayedLineIndex, 0);
        this.firstDisplayedColumnIndex = Math.max(firstDisplayedColumnIndex, 0);
        update(linesConsumer);
    }

    private UncheckedIOException displayAndCreateException(IOException exception, String verb)  {
        String message = "Unable to " + verb + " file '" + maybeFilename + "': " + exception.getClass().getSimpleName();
        messageConsumer.accept(new MessageInfo("Unable to " + verb + " file", message, JOptionPane.ERROR_MESSAGE));
        return new UncheckedIOException(message, exception);
    }

    public ViewerUiListener getViewerListener() {
        return new UiListenerImpl(); // contains reference to "this"
    }

    /* NOT STATIC */ public class UiListenerImpl implements ViewerUiListener {

        @Override
        public void onOpenFile(final String filePath, final Consumer<Collection<LineContent>> linesConsumer, final Consumer<MessageInfo> messageConsumer) {
            openFile(filePath, linesConsumer, messageConsumer);
        }

        @Override
        public void onGoOneLineUp(final Consumer<Collection<LineContent>> linesConsumer) {
            moveVertical(-1, linesConsumer);
        }

        @Override
        public void onGoOneLineDown(final Consumer<Collection<LineContent>> linesConsumer) {
            moveVertical(1, linesConsumer);
        }

        @Override
        public void onGoOneColumnLeft(final Consumer<Collection<LineContent>> linesConsumer) {
            moveHorizontal(-1, linesConsumer);
        }

        @Override
        public void onGoOneColumnRight(final Consumer<Collection<LineContent>> linesConsumer) {
            moveHorizontal(1, linesConsumer);
        }

        @Override
        public void onGoOnePageUp(final Consumer<Collection<LineContent>> linesConsumer) {
            moveVertical((currentlyDisplayedLines - 1) * -1, linesConsumer);
        }

        @Override
        public void onGoOnePageDown(final Consumer<Collection<LineContent>> linesConsumer) {
            moveVertical(currentlyDisplayedLines - 1, linesConsumer);
        }

        @Override
        public void onGoOnePageLeft(final Consumer<Collection<LineContent>> linesConsumer) {
            moveHorizontal((-1)*(currentlyDisplayedColumns - 1), linesConsumer);
        }

        @Override
        public void onGoOnePageRight(final Consumer<Collection<LineContent>> linesConsumer) {
            moveHorizontal(currentlyDisplayedColumns - 1, linesConsumer); // TODO: Do we need to stop it, when we reach end of line? which line?
        }

        @Override
        public void onGoToLineBegin(final Consumer<Collection<LineContent>> linesConsumer) {
            moveToPosition(firstDisplayedLineIndex, 0, linesConsumer);
        }

        @Override
        public void onGoToLineEnd(final Consumer<Collection<LineContent>> linesConsumer) {
            LineStatistics statisticOfCurrentLine = null;
            synchronized (lineStatistics_toBeAccessedSynchronized) {
                if (lineStatistics_toBeAccessedSynchronized.size() > firstDisplayedLineIndex) {
                    statisticOfCurrentLine = ViewerController.this.lineStatistics_toBeAccessedSynchronized.get(firstDisplayedLineIndex);
                }
            }
            if (statisticOfCurrentLine != null) {
                moveToPosition(firstDisplayedLineIndex, statisticOfCurrentLine.getLength() - currentlyDisplayedColumns, linesConsumer);
            }
        }

        @Override
        public void onGoToFirstLine(final Consumer<Collection<LineContent>> linesConsumer) {
            moveToPosition(0, 0, linesConsumer);
        }

        @Override
        public void onGoToLastLine(final Consumer<Collection<LineContent>> linesConsumer) {
            int line;
            synchronized (lineStatistics_toBeAccessedSynchronized) {
                line = Math.max(0, lineStatistics_toBeAccessedSynchronized.size() - currentlyDisplayedLines);
            }
            moveToPosition(line, 0, linesConsumer);
        }

        @Override
        public void onGoTo(int firstDisplayedLineIndex, int firstDisplayedColumnIndex, Consumer<Collection<LineContent>> linesConsumer) {
            moveToPosition(firstDisplayedLineIndex, firstDisplayedColumnIndex, linesConsumer);
        }

        @Override
        public void onLargeJumpUp(final Consumer<Collection<LineContent>> linesConsumer) {
            moveVertical((-1) * largeLinesJump, linesConsumer);
        }

        @Override
        public void onLargeJumpDown(final Consumer<Collection<LineContent>> linesConsumer) {
            moveVertical(largeLinesJump, linesConsumer);
        }

        @Override
        public void onLargeJumpLeft(final Consumer<Collection<LineContent>> linesConsumer) {
            moveHorizontal((-1) * largeColumnsJump, linesConsumer);
        }

        @Override
        public void onLargeJumpRight(final Consumer<Collection<LineContent>> linesConsumer) {
            moveHorizontal(largeColumnsJump, linesConsumer); // TODO: Do we need to stop it, when we reach end of line? which line?
        }
    }

}
