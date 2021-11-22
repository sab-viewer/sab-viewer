package com.sab_engineering.tools.sab_viewer.controller;

import com.sab_engineering.tools.sab_viewer.io.IoConstants;
import com.sab_engineering.tools.sab_viewer.io.LinePreview;
import com.sab_engineering.tools.sab_viewer.io.LinePositionBatch;
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
    private final Charset charset;
    private final String fileName;

    private final ViewerSettings currentViewerSettings_toBeAccessedSynchronized;

    private final int largeLinesJump = 500; // TODO: Should be modifiable by user in settings
    private final int largeColumnsJump = 500;

    private final AtomicBoolean linePreviewsStillRelevant;
    private final List<LinePreview> linePreviews_toBeAccessedSynchronized;

    private final List<LinePositionBatch> linePositionBatch_toBeAccessedSynchronized;
    private LinePositionBatch lastLinePositionBatch;

    private final Consumer<ViewerContent> contentConsumer;
    private final Consumer<ScannerState> stateConsumer;
    private final Consumer<MessageInfo> messageConsumer;

    private long stateConsumer_lastUpdatedAtBytes;

    private final Thread scannerThread;

    private Reader reader;
    private final Semaphore readerSignal; // <= this semaphore is used in 'reverse'. The reader waits/blocks on 'acquire' waiting for somebody to call 'release'. This avoids busy waits (in our code)
    private final Thread readerThread;

    public ViewerController(final String fileName, Charset charset, final int initiallyDisplayedLines, final int initiallyDisplayedColumns, final Consumer<ViewerContent> contentConsumer, final Consumer<ScannerState> stateConsumer, final Consumer<MessageInfo> messageConsumer) {
        linePreviewsStillRelevant = new AtomicBoolean(true);
        linePreviews_toBeAccessedSynchronized = new ArrayList<>(initiallyDisplayedLines);
        linePositionBatch_toBeAccessedSynchronized = new ArrayList<>(1024);
        lastLinePositionBatch = null;

        this.fileName = fileName;
        this.charset = charset;

        this.currentViewerSettings_toBeAccessedSynchronized = new ViewerSettings(initiallyDisplayedLines, initiallyDisplayedColumns, 0, 0);

        this.contentConsumer = contentConsumer;
        this.stateConsumer = stateConsumer;
        this.messageConsumer = messageConsumer;

        this.stateConsumer_lastUpdatedAtBytes = 0;

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

            System.gc();

            Thread.sleep(50); // wait a bit to give gc some time to run, then publish final statistics

            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long totalMemory = runtime.totalMemory();
            long maxMemory = runtime.maxMemory();
            int linesScanned = getNumberOfAllLinePositions_toBeAccessedSynchronized();
            int lastLineIndexInBatch = lastLinePositionBatch.getNumberOfContainedLines() -1;
            stateConsumer.accept(new ScannerState(linesScanned, lastLinePositionBatch.getCharacterPositionsInBytes(lastLineIndexInBatch)[0] + lastLinePositionBatch.getLengthInBytes(lastLineIndexInBatch), true, usedMemory, totalMemory, maxMemory));
        } catch (InterruptedException|ClosedByInterruptException interruptedException) {
            linePreviewsStillRelevant.set(false);
            // scannerThread should end. Nothing more to do.
        } catch (IOException ioException) {
            linePreviewsStillRelevant.set(false);
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
        linePreviewsStillRelevant.set(false);

        ViewerSettings viewerSettingsAtStartOfUpdate;
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            viewerSettingsAtStartOfUpdate = new ViewerSettings(currentViewerSettings_toBeAccessedSynchronized);
        }

        int oneAfterLastLineIndex;
        List<LinePositionBatch> relevantLineBatches;
        synchronized (linePositionBatch_toBeAccessedSynchronized) {
            if (linePositionBatch_toBeAccessedSynchronized.isEmpty()) {
                return;
            }
            int linesScanned = getNumberOfAllLinePositions_toBeAccessedSynchronized();
            if (viewerSettingsAtStartOfUpdate.getFirstDisplayedLineIndex() >= linesScanned) {
                viewerSettingsAtStartOfUpdate.setFirstDisplayedLineIndex(linesScanned - 1);
            }
            oneAfterLastLineIndex = Math.min(linesScanned, viewerSettingsAtStartOfUpdate.getFirstDisplayedLineIndex() + viewerSettingsAtStartOfUpdate.getDisplayedLines());
            int indexOfFirstBatch = viewerSettingsAtStartOfUpdate.getFirstDisplayedLineIndex() / IoConstants.NUMBER_OF_LINES_PER_BATCH;
            int indexOfLastBatch = Math.min(1 + (oneAfterLastLineIndex / IoConstants.NUMBER_OF_LINES_PER_BATCH), linePositionBatch_toBeAccessedSynchronized.size());
            relevantLineBatches = new ArrayList<>(linePositionBatch_toBeAccessedSynchronized.subList(indexOfFirstBatch, indexOfLastBatch));
        }

        final List<LinePreview> linePreviews;
        try {
            if (reader == null) {
                reader = new Reader(fileName, charset);
            }
            int startIndexInBatch = viewerSettingsAtStartOfUpdate.getFirstDisplayedLineIndex() % IoConstants.NUMBER_OF_LINES_PER_BATCH;
            int offsetOfStartIndexInBatch = viewerSettingsAtStartOfUpdate.getFirstDisplayedLineIndex() - startIndexInBatch;
            linePreviews = reader.readSpecificLines(relevantLineBatches, startIndexInBatch, oneAfterLastLineIndex - offsetOfStartIndexInBatch, viewerSettingsAtStartOfUpdate);
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
                            linePreviews,
                            viewerSettingsAtStartOfUpdate.getFirstDisplayedLineIndex() + 1,
                            viewerSettingsAtStartOfUpdate.getFirstDisplayedColumnIndex() + 1
                    )
            );
        }
    }

    private void addInitialContent(final LinePreview linePreview, final Consumer<ViewerContent> lineConsumer) {
        if (linePreviewsStillRelevant.get()) {
            synchronized (linePreviews_toBeAccessedSynchronized) {
                linePreviews_toBeAccessedSynchronized.add(linePreview);
                ArrayList<LinePreview> contents = new ArrayList<>(linePreviews_toBeAccessedSynchronized);
                if (linePreviewsStillRelevant.get()) {
                    lineConsumer.accept(new ViewerContent(contents, 1, 1));
                }
            }
        }
    }

    private void updateStatistics(final LinePositionBatch statistics) {
        int numberOfLines;
        synchronized (linePositionBatch_toBeAccessedSynchronized) {
            lastLinePositionBatch = statistics;
            linePositionBatch_toBeAccessedSynchronized.add(statistics);
            numberOfLines = getNumberOfAllLinePositions_toBeAccessedSynchronized();
        }
        long bytesScanned = statistics.getCharacterPositionsInBytes(lastLinePositionBatch.getNumberOfContainedLines() - 1)[0] + statistics.getLengthInBytes(lastLinePositionBatch.getNumberOfContainedLines() - 1);
        if ((numberOfLines < 250 && numberOfLines % 10 == 0) || stateConsumer_lastUpdatedAtBytes / (1024 * 1024) != bytesScanned / (1024 * 1024)) {
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long totalMemory = runtime.totalMemory();
            long maxMemory = runtime.maxMemory();
            stateConsumer.accept(new ScannerState(numberOfLines, bytesScanned, false, usedMemory, totalMemory, maxMemory));
            stateConsumer_lastUpdatedAtBytes = bytesScanned;
        }
    }

    private int getNumberOfAllLinePositions_toBeAccessedSynchronized() {
        return ((linePositionBatch_toBeAccessedSynchronized.size() - 1) * IoConstants.NUMBER_OF_LINES_PER_BATCH) + lastLinePositionBatch.getNumberOfContainedLines();
    }

    private void moveVertical(final int lineOffset) {
        int linesScanned;
        synchronized (linePositionBatch_toBeAccessedSynchronized) {
            linesScanned = getNumberOfAllLinePositions_toBeAccessedSynchronized();
        }
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            int newFirstLineIndex = Math.max(Math.min(currentViewerSettings_toBeAccessedSynchronized.getFirstDisplayedLineIndex() + lineOffset, linesScanned - 1), 0);
            currentViewerSettings_toBeAccessedSynchronized.setFirstDisplayedLineIndex(newFirstLineIndex);
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
        int linesScanned;
        synchronized (linePositionBatch_toBeAccessedSynchronized) {
            linesScanned = getNumberOfAllLinePositions_toBeAccessedSynchronized();
        }
        int newFirstLineIndex = Math.max(Math.min(firstDisplayedLineIndex, linesScanned - 1), 0);
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            currentViewerSettings_toBeAccessedSynchronized.setFirstDisplayedLineIndex(newFirstLineIndex);
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
        int linesScanned;
        synchronized (linePositionBatch_toBeAccessedSynchronized) {
            linesScanned = getNumberOfAllLinePositions_toBeAccessedSynchronized();
        }
        int newFirstLineIndex = Math.max(Math.min(firstDisplayedLineIndex, linesScanned - 1), 0);
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            currentViewerSettings_toBeAccessedSynchronized.setFirstDisplayedLineIndex(newFirstLineIndex);
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
        LinePositionBatch statisticOfCurrentLine = null;
        synchronized (linePositionBatch_toBeAccessedSynchronized) {
            if (viewerSettings.getFirstDisplayedLineIndex() > getNumberOfAllLinePositions_toBeAccessedSynchronized()) {
                statisticOfCurrentLine = ViewerController.this.linePositionBatch_toBeAccessedSynchronized.get(viewerSettings.getFirstDisplayedLineIndex() / IoConstants.NUMBER_OF_LINES_PER_BATCH);
            }
        }
        if (statisticOfCurrentLine != null) {
            moveToHorizontalPosition(statisticOfCurrentLine.getLengthInCharacters(viewerSettings.getFirstDisplayedLineIndex() % IoConstants.NUMBER_OF_LINES_PER_BATCH) - viewerSettings.getDisplayedColumns());
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
        synchronized (linePositionBatch_toBeAccessedSynchronized) {
            line = Math.max(0, getNumberOfAllLinePositions_toBeAccessedSynchronized() - viewerSettings.getDisplayedLines());
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
