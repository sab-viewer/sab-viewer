package com.sab_engineering.tools.sab_viewer.controller;

import com.sab_engineering.tools.sab_viewer.io.LineContent;
import com.sab_engineering.tools.sab_viewer.io.LineStatistics;
import com.sab_engineering.tools.sab_viewer.io.Reader;
import com.sab_engineering.tools.sab_viewer.io.Scanner;

import javax.swing.JOptionPane;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class ViewerController implements ViewerUiListener {
    private final Charset charset = StandardCharsets.UTF_8;
    private final String fileName;

    private final ViewerSettings currentViewerSettings_toBeAccessedSynchronized;

    private final int largeLinesJump = 500; // TODO: Should be modifiable by user in settings
    private final int largeColumnsJump = 500;

    private final AtomicBoolean initialLinesStillRelevant;
    private final List<LineContent> initialLines_toBeAccessedSynchronized;

    private final List<LineStatistics> lineStatistics_toBeAccessedSynchronized;

    private final Consumer<ViewerContent> contentConsumer;
    private final Consumer<ScannerState> stateConsumer;
    private final Consumer<MessageInfo> messageConsumer;

    private long stateConsumer_lastUpdatedAtBytes;
    private boolean stateConsumer_oomEncountered;

    private final Thread scannerThread;

    private Reader reader;
    private final Semaphore readerSignal; // <= this semaphore is used in 'reverse'. The reader waits/blocks on 'acquire' waiting for somebody to call 'release'. This avoids busy waits (in our code)
    private final Thread readerThread;

    public ViewerController(final String fileName, final int initiallyDisplayedLines, final int initiallyDisplayedColumns, final Consumer<ViewerContent> contentConsumer, final Consumer<ScannerState> stateConsumer, final Consumer<MessageInfo> messageConsumer) {
        currentViewerSettings_toBeAccessedSynchronized = new ViewerSettings(initiallyDisplayedLines, initiallyDisplayedColumns, 0, 0);

        initialLinesStillRelevant = new AtomicBoolean(true);
        initialLines_toBeAccessedSynchronized = new ArrayList<>(initiallyDisplayedLines);
        lineStatistics_toBeAccessedSynchronized = new ArrayList<>(100000);

        this.fileName = fileName;
        this.contentConsumer = contentConsumer;
        this.stateConsumer = stateConsumer;
        this.messageConsumer = messageConsumer;

        this.stateConsumer_lastUpdatedAtBytes = 0;
        this.stateConsumer_oomEncountered = false;

        scannerThread = new Thread(() -> scanFile(initiallyDisplayedLines, initiallyDisplayedColumns), "Scanner");
        scannerThread.start();

        readerSignal = new Semaphore(1);
        readerSignal.acquireUninterruptibly();
        readerThread = new Thread(this::readFile, "Reader");
        readerThread.start();
    }

    @Override
    public void interruptBackgroundThreads() {
        scannerThread.interrupt();
        readerThread.interrupt();
    }

    @Override
    public void resize(final int displayedLines, final int displayedColumns) {
        boolean changed = false;
        synchronized (this.currentViewerSettings_toBeAccessedSynchronized) {
            if (currentViewerSettings_toBeAccessedSynchronized.getDisplayedLines() != displayedLines || currentViewerSettings_toBeAccessedSynchronized.getDisplayedColumns() != displayedColumns) {
                System.out.println("resize " + currentViewerSettings_toBeAccessedSynchronized.getDisplayedLines() + "x" + currentViewerSettings_toBeAccessedSynchronized.getDisplayedColumns() + " => " + displayedLines + "x" + displayedColumns);
                currentViewerSettings_toBeAccessedSynchronized.setDisplayedLines(displayedLines);
                currentViewerSettings_toBeAccessedSynchronized.setDisplayedColumns(displayedColumns);
                changed = true;
            }
        }
        if (changed) {
            requestUpdate();
        }
    }

    private void requestUpdate(){
        readerSignal.release();
    }

    // this method is supposed to be executed in scannerThread
    private void scanFile(final int initiallyDisplayedLines, final int initiallyDisplayedColumns) {
        try {
            Scanner scanner = new Scanner(fileName, charset, lineContent -> addInitialContent(lineContent, contentConsumer), initiallyDisplayedLines, initiallyDisplayedColumns, this::updateStatistics);
            scanner.scanFile();

            LineStatistics lastStatistics = lineStatistics_toBeAccessedSynchronized.get(lineStatistics_toBeAccessedSynchronized.size() - 1);
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long totalMemory = runtime.totalMemory();
            long maxMemory = runtime.maxMemory();
            stateConsumer.accept(new ScannerState(lineStatistics_toBeAccessedSynchronized.size(), lastStatistics.getCharacterPositionsInBytes()[0] + lastStatistics.getLengthInBytes(), true, usedMemory, totalMemory, maxMemory, stateConsumer_oomEncountered));
        } catch (ClosedByInterruptException interruptedException) {
            initialLinesStillRelevant.set(false);
            // scannerThread should end. Nothing more to do.
        } catch (IOException ioException) {
            initialLinesStillRelevant.set(false);
            throw displayAndCreateException(ioException, "scan");
        }
    }

    // this method is supposed to be executed in readerThread
    private void readFile() {
        try {
            do {
                readerSignal.acquire();
                update();
            } while (true);
        } catch (InterruptedException interruptedException) {
            // readerThread should end. Nothing more to do.
        }
    }

    private void update() {
        initialLinesStillRelevant.set(false);

        ViewerSettings viewerSettingsAtStartOfUpdate;
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            viewerSettingsAtStartOfUpdate = new ViewerSettings(currentViewerSettings_toBeAccessedSynchronized);
        }

        List<LineStatistics> linesToRead;
        synchronized (lineStatistics_toBeAccessedSynchronized) {
            if (lineStatistics_toBeAccessedSynchronized.isEmpty()) {
                return;
            }
            if (viewerSettingsAtStartOfUpdate.getFirstDisplayedLineIndex() >= lineStatistics_toBeAccessedSynchronized.size()) {
                viewerSettingsAtStartOfUpdate.setFirstDisplayedLineIndex(lineStatistics_toBeAccessedSynchronized.size() - 1);
            }
            final int oneAfterLastLineIndex = Math.min(lineStatistics_toBeAccessedSynchronized.size(), viewerSettingsAtStartOfUpdate.getFirstDisplayedLineIndex() + viewerSettingsAtStartOfUpdate.getDisplayedLines());
            linesToRead = new ArrayList<>(lineStatistics_toBeAccessedSynchronized.subList(viewerSettingsAtStartOfUpdate.getFirstDisplayedLineIndex(), oneAfterLastLineIndex));
        }

        final List<LineContent> lineContents;
        try {
            if (reader == null) {
                reader = new Reader(fileName, charset);
            }
            lineContents = reader.readSpecificLines(linesToRead, viewerSettingsAtStartOfUpdate);
        } catch (IOException ioException) {
            throw displayAndCreateException(ioException, "read");
        }

        ViewerSettings viewerSettingsAtEndOfUpdate;
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            viewerSettingsAtEndOfUpdate = new ViewerSettings(currentViewerSettings_toBeAccessedSynchronized);
        }

        if (Objects.equals(viewerSettingsAtStartOfUpdate, viewerSettingsAtEndOfUpdate)) {
            contentConsumer.accept(
                    new ViewerContent(
                            lineContents,
                            viewerSettingsAtStartOfUpdate.getFirstDisplayedLineIndex() + 1,
                            viewerSettingsAtStartOfUpdate.getFirstDisplayedColumnIndex() + 1
                    )
            );
        }
    }

    private void addInitialContent(final LineContent lineContent, final Consumer<ViewerContent> lineConsumer) {
        if (initialLinesStillRelevant.get()) {
            synchronized (initialLines_toBeAccessedSynchronized) {
                initialLines_toBeAccessedSynchronized.add(lineContent);
                ArrayList<LineContent> contents = new ArrayList<>(initialLines_toBeAccessedSynchronized);
                if (initialLinesStillRelevant.get()) {
                    lineConsumer.accept(new ViewerContent(contents, 1, 1));
                }
            }
        }
    }

    private void updateStatistics(final LineStatistics statistics) {
        int numberOfLines;
        OutOfMemoryError oomError = null;
        synchronized (lineStatistics_toBeAccessedSynchronized) {
            try {
                lineStatistics_toBeAccessedSynchronized.add(statistics);
            } catch (OutOfMemoryError oom) {
                oomError = oom;
                stateConsumer_oomEncountered = true;
            }
            numberOfLines = lineStatistics_toBeAccessedSynchronized.size();
        }
        long bytesScanned = statistics.getCharacterPositionsInBytes()[0] + statistics.getLengthInBytes();
        if ((numberOfLines < 250 && numberOfLines % 10 == 0) || stateConsumer_lastUpdatedAtBytes / (1024 * 1024) != bytesScanned / (1024 * 1024) || stateConsumer_oomEncountered) {
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long totalMemory = runtime.totalMemory();
            long maxMemory = runtime.maxMemory();
            stateConsumer.accept(new ScannerState(numberOfLines, bytesScanned, false, usedMemory, totalMemory, maxMemory, stateConsumer_oomEncountered));
            stateConsumer_lastUpdatedAtBytes = bytesScanned;
        }
        if (oomError != null) {
            throw oomError;
        }
    }

    private void moveVertical(final int lineOffset) {
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            currentViewerSettings_toBeAccessedSynchronized.setFirstDisplayedLineIndex(Math.max(currentViewerSettings_toBeAccessedSynchronized.getFirstDisplayedLineIndex() + lineOffset, 0));
        }
        requestUpdate();
    }

    private void moveHorizontal(final long columnOffset) {
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            currentViewerSettings_toBeAccessedSynchronized.setFirstDisplayedColumnIndex(Math.max(currentViewerSettings_toBeAccessedSynchronized.getFirstDisplayedColumnIndex() + columnOffset, 0));
        }
        requestUpdate();
    }

    private void moveToVerticalPosition(final int firstDisplayedLineIndex) {
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            currentViewerSettings_toBeAccessedSynchronized.setFirstDisplayedLineIndex(Math.max(firstDisplayedLineIndex, 0));
        }
        requestUpdate();
    }

    private void moveToHorizontalPosition(final long firstDisplayedColumnIndex) {
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            currentViewerSettings_toBeAccessedSynchronized.setFirstDisplayedColumnIndex(Math.max(firstDisplayedColumnIndex, 0));
        }
        requestUpdate();
    }

    private void moveToPosition(final int firstDisplayedLineIndex, final long firstDisplayedColumnIndex) {
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            currentViewerSettings_toBeAccessedSynchronized.setFirstDisplayedLineIndex(Math.max(firstDisplayedLineIndex, 0));
            currentViewerSettings_toBeAccessedSynchronized.setFirstDisplayedColumnIndex(Math.max(firstDisplayedColumnIndex, 0));
        }
        requestUpdate();
    }

    private UncheckedIOException displayAndCreateException(IOException exception, String verb)  {
        String message = "Unable to " + verb + " file '" + fileName + "': " + exception.getClass().getSimpleName();
        messageConsumer.accept(new MessageInfo("Unable to " + verb + " file", message, JOptionPane.ERROR_MESSAGE));
        return new UncheckedIOException(message, exception);
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
        ViewerSettings viewerSettings;
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            viewerSettings = new ViewerSettings(currentViewerSettings_toBeAccessedSynchronized);
        }
        moveVertical((viewerSettings.getDisplayedLines() - 1) * -1);
    }

    @Override
    public void onGoOnePageDown() {
        ViewerSettings viewerSettings;
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            viewerSettings = new ViewerSettings(currentViewerSettings_toBeAccessedSynchronized);
        }
        moveVertical(viewerSettings.getDisplayedLines() - 1);
    }

    @Override
    public void onGoOnePageLeft() {
        ViewerSettings viewerSettings;
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            viewerSettings = new ViewerSettings(currentViewerSettings_toBeAccessedSynchronized);
        }
        moveHorizontal((-1)*(viewerSettings.getDisplayedColumns() - 1));
    }

    @Override
    public void onGoOnePageRight() {
        ViewerSettings viewerSettings;
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            viewerSettings = new ViewerSettings(currentViewerSettings_toBeAccessedSynchronized);
        }
        moveHorizontal(viewerSettings.getDisplayedColumns() - 1);
    }

    @Override
    public void onGoToLineBegin() {
        moveToHorizontalPosition(0);
    }

    @Override
    public void onGoToLineEnd() {
        ViewerSettings viewerSettings;
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            viewerSettings = new ViewerSettings(currentViewerSettings_toBeAccessedSynchronized);
        }
        LineStatistics statisticOfCurrentLine = null;
        synchronized (lineStatistics_toBeAccessedSynchronized) {
            if (lineStatistics_toBeAccessedSynchronized.size() > viewerSettings.getFirstDisplayedLineIndex()) {
                statisticOfCurrentLine = ViewerController.this.lineStatistics_toBeAccessedSynchronized.get(viewerSettings.getFirstDisplayedLineIndex());
            }
        }
        if (statisticOfCurrentLine != null) {
            moveToHorizontalPosition(statisticOfCurrentLine.getLengthInCharacters() - viewerSettings.getDisplayedColumns());
        }
    }

    @Override
    public void onGoToFirstLine() {
        moveToVerticalPosition(0);
    }

    @Override
    public void onGoToLastLine() {
        ViewerSettings viewerSettings;
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            viewerSettings = new ViewerSettings(currentViewerSettings_toBeAccessedSynchronized);
        }

        int line;
        synchronized (lineStatistics_toBeAccessedSynchronized) {
            line = Math.max(0, lineStatistics_toBeAccessedSynchronized.size() - viewerSettings.getDisplayedLines());
        }

        moveToVerticalPosition(line);
    }

    @Override
    public void onGoTo(int firstDisplayedLineIndex, int firstDisplayedColumnIndex) {
        moveToPosition(firstDisplayedLineIndex, firstDisplayedColumnIndex);
    }

    @Override
    public void onLargeJumpUp() {
        moveVertical((-1) * largeLinesJump);
    }

    @Override
    public void onLargeJumpDown() {
        moveVertical(largeLinesJump);
    }

    @Override
    public void onLargeJumpLeft() {
        moveHorizontal((-1) * largeColumnsJump);
    }

    @Override
    public void onLargeJumpRight() {
        moveHorizontal(largeColumnsJump);
    }
}
