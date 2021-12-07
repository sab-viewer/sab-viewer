package com.sab_engineering.tools.sab_viewer.controller;

import com.sab_engineering.tools.sab_viewer.io.IoConstants;
import com.sab_engineering.tools.sab_viewer.io.LinePositionBatch;
import com.sab_engineering.tools.sab_viewer.io.LinePositions;
import com.sab_engineering.tools.sab_viewer.io.LinePreview;
import com.sab_engineering.tools.sab_viewer.io.MutableLinePositionBatch;
import com.sab_engineering.tools.sab_viewer.io.Reader;
import com.sab_engineering.tools.sab_viewer.io.Scanner;
import com.sab_engineering.tools.sab_viewer.io.Searcher;

import javax.swing.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

public class ViewerController implements ViewerUiListener {
    private final Charset charset;
    private final String fileName;

    private final ViewerSettings currentViewerSettings_toBeAccessedSynchronized;

    private final int largeLinesJump = 500; // TODO: Should be modifiable by user in settings
    private final int largeColumnsJump = 500;

    private final LinePositions linePositions_toBeAccessedSynchronized;

    private final Consumer<ViewerContent> contentConsumer;
    private final Consumer<ScannerState> stateConsumer;
    private final Consumer<MessageInfo> messageConsumer;

    private long stateConsumer_lastUpdatedAtTimeStampInMillis;

    private final Thread scannerThread;

    private Reader reader;
    private final Semaphore readerSignal; // <= this semaphore is used in 'reverse'. The reader waits/blocks on 'acquire' waiting for somebody to call 'release'. This avoids busy waits (in our code)
    private final Thread readerThread;

    private final Semaphore searchLock;
    private Thread searcherThread_toBeAccessedLocked;

    public ViewerController(final String fileName, Charset charset, final int initiallyDisplayedLines, final int initiallyDisplayedColumns, final Consumer<ViewerContent> contentConsumer, final Consumer<ScannerState> stateConsumer, final Consumer<MessageInfo> messageConsumer) {
        linePositions_toBeAccessedSynchronized = new LinePositions();

        this.fileName = fileName;
        this.charset = charset;

        currentViewerSettings_toBeAccessedSynchronized = new ViewerSettings(initiallyDisplayedLines, initiallyDisplayedColumns, 0, 0);

        this.contentConsumer = contentConsumer;
        this.stateConsumer = stateConsumer;
        this.messageConsumer = messageConsumer;

        stateConsumer_lastUpdatedAtTimeStampInMillis = System.currentTimeMillis();

        scannerThread = new Thread(this::scanFile, "Scanner");
        scannerThread.start();

        readerSignal = new Semaphore(1);
        readerSignal.acquireUninterruptibly();
        readerThread = new Thread(this::readFile, "Reader");
        readerThread.start();

        searchLock = new Semaphore(1);
        searcherThread_toBeAccessedLocked = null;
    }

    @Override
    public void interruptBackgroundThreads() {
        scannerThread.interrupt();
        readerThread.interrupt();
        try {
            searchLock.acquire();
            try {
                if (searcherThread_toBeAccessedLocked != null) {
                    searcherThread_toBeAccessedLocked.interrupt();
                }
            } finally {
                searchLock.release();
            }
        } catch (InterruptedException e) {
            // don't care
        }
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

    // this method is supposed to be executed in scannerThread
    private void scanFile() {
        try {
            Scanner scanner = new Scanner(fileName, charset, this::processFinishedPositions, this::processPositionPreview);
            boolean stoppedBecauseOom = scanner.scanFile();

            System.gc();

            Thread.sleep(50); // wait a bit to give gc some time to run, then publish final statistics

            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long totalMemory = runtime.totalMemory();
            long maxMemory = runtime.maxMemory();
            int linesScanned = linePositions_toBeAccessedSynchronized.getNumberOfContainedLines();
            stateConsumer.accept(new ScannerState(linesScanned, linePositions_toBeAccessedSynchronized.getBytePositionOfEndOfLastLine(), true, stoppedBecauseOom, usedMemory, totalMemory, maxMemory));
        } catch (InterruptedException|ClosedByInterruptException interruptedException) {
            // scannerThread should end. Nothing more to do.
        } catch (IOException ioException) {
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
        } catch (ClosedByInterruptException | InterruptedException interruptedException) {
            // readerThread should end. Nothing more to do.
        }
    }

    private void requestUpdate(){
        readerSignal.release();
    }

    private void update() throws ClosedByInterruptException {
        ViewerSettings viewerSettingsAtStartOfUpdate;
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            viewerSettingsAtStartOfUpdate = new ViewerSettings(currentViewerSettings_toBeAccessedSynchronized);
        }

        int oneAfterLastLineIndex;
        LinePositions.LinePositionsView relevantLinePositions;
        synchronized (linePositions_toBeAccessedSynchronized) {
            if (linePositions_toBeAccessedSynchronized.isEmpty()) {
                return;
            }
            int linesScanned = linePositions_toBeAccessedSynchronized.getNumberOfContainedLines();
            if (viewerSettingsAtStartOfUpdate.getFirstDisplayedLineIndex() >= linesScanned) {
                viewerSettingsAtStartOfUpdate.setFirstDisplayedLineIndex(linesScanned - 1);
            }
            oneAfterLastLineIndex = Math.min(linesScanned, viewerSettingsAtStartOfUpdate.getFirstDisplayedLineIndex() + viewerSettingsAtStartOfUpdate.getDisplayedLines());
            relevantLinePositions = linePositions_toBeAccessedSynchronized.subPositions(viewerSettingsAtStartOfUpdate.getFirstDisplayedLineIndex(), oneAfterLastLineIndex);
        }

        final List<LinePreview> linePreviews;
        try {
            if (reader == null) {
                reader = new Reader(fileName, charset);
            }
            linePreviews = reader.readSpecificLines(relevantLinePositions, viewerSettingsAtStartOfUpdate.getFirstDisplayedLineIndex(), oneAfterLastLineIndex, viewerSettingsAtStartOfUpdate);
        } catch(ClosedByInterruptException cbie) {
            throw cbie;
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

    private void processFinishedPositions(final LinePositionBatch positionBatch) {
        int numberOfLines;
        long bytesScanned;
        synchronized (linePositions_toBeAccessedSynchronized) {
            linePositions_toBeAccessedSynchronized.addFinishedBatch(positionBatch);
            numberOfLines = linePositions_toBeAccessedSynchronized.getNumberOfContainedLines();
            bytesScanned = linePositions_toBeAccessedSynchronized.getBytePositionOfEndOfLastLine();
        }
        requestUpdateIfPositionsAreInRange(numberOfLines - positionBatch.getNumberOfContainedLines(), numberOfLines - 1, null);
        publishState(numberOfLines, bytesScanned);
    }

    private void processPositionPreview(final MutableLinePositionBatch positionBatch) {
        int numberOfLines;
        long bytesScanned;
        synchronized (linePositions_toBeAccessedSynchronized) {
            linePositions_toBeAccessedSynchronized.updateLastBatchPreview(positionBatch);
            numberOfLines = linePositions_toBeAccessedSynchronized.getNumberOfContainedLines();
            bytesScanned = linePositions_toBeAccessedSynchronized.getBytePositionOfEndOfLastLine();
        }
        requestUpdateIfPositionsAreInRange(numberOfLines - 1, numberOfLines - 1, positionBatch.getCharacterPositionsInBytes(positionBatch.getNumberOfContainedLines() - 1));
        publishState(numberOfLines, bytesScanned);
    }

    private void requestUpdateIfPositionsAreInRange(int indexOfFirstLineInBatch, int indexOfLastLineInBatch, long[] characterPositionsInBytes) {
        ViewerSettings viewerSettingsAtStartOfUpdate;
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            viewerSettingsAtStartOfUpdate = new ViewerSettings(currentViewerSettings_toBeAccessedSynchronized);
        }
        int firstDisplayedLineIndex = viewerSettingsAtStartOfUpdate.getFirstDisplayedLineIndex();
        int lastDisplayedLineIndex = firstDisplayedLineIndex + (viewerSettingsAtStartOfUpdate.getDisplayedLines() - 1);
        if (
                isInRangeInclusive(firstDisplayedLineIndex, indexOfFirstLineInBatch, indexOfLastLineInBatch)
                || isInRangeInclusive(lastDisplayedLineIndex, indexOfFirstLineInBatch, indexOfLastLineInBatch)
                || isInRangeInclusive(indexOfFirstLineInBatch, firstDisplayedLineIndex, lastDisplayedLineIndex)
                || isInRangeInclusive(indexOfLastLineInBatch, firstDisplayedLineIndex, lastDisplayedLineIndex)
        ) {
            if (characterPositionsInBytes != null) {
                long lastUpdatedCharacterIndex = characterPositionsInBytes.length * (long) IoConstants.NUMBER_OF_CHARACTERS_PER_BYTE_POSITION;
                long firstUpdatedCharacterIndex = lastUpdatedCharacterIndex - IoConstants.NUMBER_OF_CHARACTERS_PER_BYTE_POSITION;
                long firstDisplayedColumnIndex = viewerSettingsAtStartOfUpdate.getFirstDisplayedColumnIndex();
                long lastDisplayedColumnIndex = firstDisplayedColumnIndex + (viewerSettingsAtStartOfUpdate.getDisplayedColumns() - 1);
                if (
                    isInRangeInclusive(firstDisplayedColumnIndex, firstUpdatedCharacterIndex, lastUpdatedCharacterIndex)
                    || isInRangeInclusive(lastDisplayedColumnIndex, firstUpdatedCharacterIndex, lastUpdatedCharacterIndex)
                    || isInRangeInclusive(firstUpdatedCharacterIndex, firstDisplayedColumnIndex, lastDisplayedColumnIndex)
                    || isInRangeInclusive(lastUpdatedCharacterIndex, firstDisplayedColumnIndex, lastDisplayedColumnIndex)
                ) {
                    requestUpdate();
                }
            } else {
                requestUpdate();
            }
        }
    }

    private boolean isInRangeInclusive(long needle, long lowerBound, long upperBound) {
        return needle >= lowerBound && needle <= upperBound;
    }

    private void publishState(int numberOfLines, long bytesScanned) {
        if (stateConsumer_lastUpdatedAtTimeStampInMillis / 500 != System.currentTimeMillis() / 500) {
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long totalMemory = runtime.totalMemory();
            long maxMemory = runtime.maxMemory();
            stateConsumer.accept(new ScannerState(numberOfLines, bytesScanned, false, false, usedMemory, totalMemory, maxMemory));
            stateConsumer_lastUpdatedAtTimeStampInMillis = System.currentTimeMillis();
        }
    }

    private void moveVertical(final int lineOffset) {
        int linesScanned;
        synchronized (linePositions_toBeAccessedSynchronized) {
            linesScanned = linePositions_toBeAccessedSynchronized.getNumberOfContainedLines();
        }
        boolean changed = false;
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            int newFirstLineIndex = Math.max(Math.min(currentViewerSettings_toBeAccessedSynchronized.getFirstDisplayedLineIndex() + lineOffset, linesScanned - 1), 0);
            if (newFirstLineIndex != currentViewerSettings_toBeAccessedSynchronized.getFirstDisplayedLineIndex()) {
                currentViewerSettings_toBeAccessedSynchronized.setFirstDisplayedLineIndex(newFirstLineIndex);
                changed = true;
            }
        }
        if (changed) {
            requestUpdate();
        }
    }

    private void moveHorizontal(final long columnOffset) {
        boolean changed = false;
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            long newColumnIndex = Math.max(currentViewerSettings_toBeAccessedSynchronized.getFirstDisplayedColumnIndex() + columnOffset, 0);
            if (newColumnIndex != currentViewerSettings_toBeAccessedSynchronized.getFirstDisplayedColumnIndex()) {
                currentViewerSettings_toBeAccessedSynchronized.setFirstDisplayedColumnIndex(newColumnIndex);
                changed = true;
            }
        }
        if (changed) {
            requestUpdate();
        }
    }

    private void moveToVerticalPosition(final int firstDisplayedLineIndex) {
        int linesScanned;
        synchronized (linePositions_toBeAccessedSynchronized) {
            linesScanned = linePositions_toBeAccessedSynchronized.getNumberOfContainedLines();
        }
        int newFirstLineIndex = Math.max(Math.min(firstDisplayedLineIndex, linesScanned - 1), 0);
        boolean changed = false;
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            if (newFirstLineIndex != currentViewerSettings_toBeAccessedSynchronized.getFirstDisplayedLineIndex()) {
                currentViewerSettings_toBeAccessedSynchronized.setFirstDisplayedLineIndex(newFirstLineIndex);
                changed = true;
            }
        }
        if (changed) {
            requestUpdate();
        }
    }

    private void moveToHorizontalPosition(final long firstDisplayedColumnIndex) {
        boolean changed = false;
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            long newColumnIndex = Math.max(firstDisplayedColumnIndex, 0);
            if (newColumnIndex != currentViewerSettings_toBeAccessedSynchronized.getFirstDisplayedColumnIndex()) {
                currentViewerSettings_toBeAccessedSynchronized.setFirstDisplayedColumnIndex(newColumnIndex);
                changed = true;
            }
        }
        if (changed) {
            requestUpdate();
        }
    }

    private void moveToPosition(final int firstDisplayedLineIndex, final long firstDisplayedColumnIndex) {
        int linesScanned;
        synchronized (linePositions_toBeAccessedSynchronized) {
            linesScanned = linePositions_toBeAccessedSynchronized.getNumberOfContainedLines();
        }
        int newFirstLineIndex = Math.max(Math.min(firstDisplayedLineIndex, linesScanned - 1), 0);
        long newColumnIndex = Math.max(firstDisplayedColumnIndex, 0);
        boolean changed = false;
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            if (
                    newFirstLineIndex != currentViewerSettings_toBeAccessedSynchronized.getFirstDisplayedLineIndex()
                    || newColumnIndex != currentViewerSettings_toBeAccessedSynchronized.getFirstDisplayedColumnIndex()
            ) {
                currentViewerSettings_toBeAccessedSynchronized.setFirstDisplayedLineIndex(newFirstLineIndex);
                currentViewerSettings_toBeAccessedSynchronized.setFirstDisplayedColumnIndex(newColumnIndex);
                changed = true;
            }
        }
        if (changed) {
            requestUpdate();
        }
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
        long lengthOfCurrentLineInCharacters = -1;
        synchronized (linePositions_toBeAccessedSynchronized) {
            if (viewerSettings.getFirstDisplayedLineIndex() < linePositions_toBeAccessedSynchronized.getNumberOfContainedLines()) {
                lengthOfCurrentLineInCharacters = linePositions_toBeAccessedSynchronized.getLengthInCharacters(viewerSettings.getFirstDisplayedLineIndex());
            }
        }
        if (lengthOfCurrentLineInCharacters != -1) {
            moveToHorizontalPosition(lengthOfCurrentLineInCharacters - viewerSettings.getDisplayedColumns());
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
        synchronized (linePositions_toBeAccessedSynchronized) {
            line = Math.max(0, linePositions_toBeAccessedSynchronized.getNumberOfContainedLines() - viewerSettings.getDisplayedLines());
        }

        moveToVerticalPosition(line);
    }

    @Override
    public void onGoTo(int firstDisplayedLineIndex, long firstDisplayedColumnIndex) {
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

    @Override
    public void moveToLocationOfSearchTerm(String literalSearchTerm) {
        if (literalSearchTerm.length() == 0) {
            messageConsumer.accept(new MessageInfo("Unable to search file", "Search term is empty", JOptionPane.WARNING_MESSAGE));
            return;
        }

        try {
            searchLock.acquire();

            try {
                if (searcherThread_toBeAccessedLocked != null) {
                    if (searcherThread_toBeAccessedLocked.isAlive()) {
                        searcherThread_toBeAccessedLocked.interrupt();
                    }
                    searcherThread_toBeAccessedLocked = null;
                }
                ViewerSettings viewerSettingsAtStartOfSearch;
                synchronized (currentViewerSettings_toBeAccessedSynchronized) {
                    viewerSettingsAtStartOfSearch = new ViewerSettings(currentViewerSettings_toBeAccessedSynchronized);
                }

                LinePositions.LinePositionsView linePositions;
                synchronized (linePositions_toBeAccessedSynchronized) {
                    linePositions = linePositions_toBeAccessedSynchronized.asView();
                }

                searcherThread_toBeAccessedLocked = new Thread(() -> searchForTerm(literalSearchTerm, viewerSettingsAtStartOfSearch, linePositions), "Searcher");
                searcherThread_toBeAccessedLocked.start();
            } finally {
                searchLock.release();
            }
        } catch (InterruptedException e) {
            // stop waiting for searchLock
        }
    }

    // supposed to be run in searcher thread
    private void searchForTerm(String literalSearchTerm, ViewerSettings viewerSettingsAtStartOfSearch, LinePositions.LinePositionsView linePositions) {
        try {
            Searcher searcher = new Searcher(fileName, charset);
            boolean foundTerm = searcher.searchInSpecificLines(literalSearchTerm, linePositions, viewerSettingsAtStartOfSearch, this::moveToPosition, true);
            if (!foundTerm) {
                messageConsumer.accept(new MessageInfo("File search done", "Could not locate term between current position and end of file", JOptionPane.INFORMATION_MESSAGE));
            }
        } catch (ClosedByInterruptException interruptedException) {
            // searcherThread should end. Nothing more to do.
        } catch (IOException ioException) {
            throw displayAndCreateException(ioException, "search");
        }
    }
}
