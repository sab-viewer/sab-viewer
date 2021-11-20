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

    private final Consumer<ScannerState> stateConsumer;
    private final Consumer<MessageInfo> messageConsumer;
    private final Thread scannerThread;

    private Reader reader;
    private final Semaphore readerSignal; // <= this semaphore is used in 'reverse'. The reader waits/blocks on 'acquire' waiting for somebody to call 'release'. This avoids busy waits (in our code)
    private Consumer<ViewerContent> contentConsumerWaitingForReader;
    private final Thread readerThread;

    public ViewerController(final String fileName, final int initiallyDisplayedLines, final int initiallyDisplayedColumns, final Consumer<ViewerContent> contentConsumer, final Consumer<ScannerState> stateConsumer, final Consumer<MessageInfo> messageConsumer) {
        currentViewerSettings_toBeAccessedSynchronized = new ViewerSettings(initiallyDisplayedLines, initiallyDisplayedColumns, 0, 0);

        initialLinesStillRelevant = new AtomicBoolean(true);
        initialLines_toBeAccessedSynchronized = new ArrayList<>(initiallyDisplayedLines);
        lineStatistics_toBeAccessedSynchronized = new ArrayList<>(100000);

        this.fileName = fileName;
        this.stateConsumer = stateConsumer;
        this.messageConsumer = messageConsumer;
        scannerThread = new Thread(() -> scanFile(contentConsumer, initiallyDisplayedLines, initiallyDisplayedColumns), "Scanner");
        scannerThread.start();

        readerSignal = new Semaphore(1);
        readerSignal.acquireUninterruptibly();
        this.contentConsumerWaitingForReader = null;
        readerThread = new Thread(this::readFile, "Reader");
        readerThread.start();
    }

    @Override
    public void interruptBackgroundThreads() {
        scannerThread.interrupt();
        readerThread.interrupt();
    }

    @Override
    public void resize(final int displayedLines, final int displayedColumns, final Consumer<ViewerContent> contentConsumer) {
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
            requestUpdate(contentConsumer);
        }
    }

    private void requestUpdate(final Consumer<ViewerContent> contentConsumer){
        contentConsumerWaitingForReader = contentConsumer;
        readerSignal.release();
    }

    // this method is supposed to be executed in scannerThread
    private void scanFile(final Consumer<ViewerContent> contentConsumer, final int initiallyDisplayedLines, final int initiallyDisplayedColumns) {
        try {
            Scanner.scanFile(fileName, charset, lineContent -> addInitialContent(lineContent, contentConsumer), initiallyDisplayedLines, initiallyDisplayedColumns, this::updateStatistics);

            LineStatistics lastStatistics = lineStatistics_toBeAccessedSynchronized.get(lineStatistics_toBeAccessedSynchronized.size() - 1);
            stateConsumer.accept(new ScannerState(lineStatistics_toBeAccessedSynchronized.size(), lastStatistics.getCharacterPositionsInBytes()[0] + lastStatistics.getLengthInBytes(), true));
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
                Consumer<ViewerContent> linesConsumerWaitingForReader = this.contentConsumerWaitingForReader;
                this.contentConsumerWaitingForReader = null;
                if (linesConsumerWaitingForReader == null) {
                    throw new IllegalStateException("Signaled to run reader, but nothing to do");
                }
                update(linesConsumerWaitingForReader);
            } while (true);
        } catch (InterruptedException interruptedException) {
            // readerThread should end. Nothing more to do.
        }
    }

    private void update(final Consumer<ViewerContent> contentConsumer) {
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
        synchronized (lineStatistics_toBeAccessedSynchronized) {
            lineStatistics_toBeAccessedSynchronized.add(statistics);
            numberOfLines = lineStatistics_toBeAccessedSynchronized.size();
        }
        if ((numberOfLines < 250 && numberOfLines % 10 == 0) || numberOfLines % 5000 == 0) {
            stateConsumer.accept(new ScannerState(numberOfLines, statistics.getCharacterPositionsInBytes()[0] + statistics.getLengthInBytes(), false));
        }
    }

    private void moveVertical(final int lineOffset, final Consumer<ViewerContent> contentConsumer) {
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            currentViewerSettings_toBeAccessedSynchronized.setFirstDisplayedLineIndex(Math.max(currentViewerSettings_toBeAccessedSynchronized.getFirstDisplayedLineIndex() + lineOffset, 0));
        }
        requestUpdate(contentConsumer);
    }

    private void moveHorizontal(final long columnOffset, final Consumer<ViewerContent> contentConsumer) {
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            currentViewerSettings_toBeAccessedSynchronized.setFirstDisplayedColumnIndex(Math.max(currentViewerSettings_toBeAccessedSynchronized.getFirstDisplayedColumnIndex() + columnOffset, 0));
        }
        requestUpdate(contentConsumer);
    }

    private void moveToVerticalPosition(final int firstDisplayedLineIndex, final Consumer<ViewerContent> contentConsumer) {
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            currentViewerSettings_toBeAccessedSynchronized.setFirstDisplayedLineIndex(Math.max(firstDisplayedLineIndex, 0));
        }
        requestUpdate(contentConsumer);
    }

    private void moveToHorizontalPosition(final long firstDisplayedColumnIndex, final Consumer<ViewerContent> contentConsumer) {
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            currentViewerSettings_toBeAccessedSynchronized.setFirstDisplayedColumnIndex(Math.max(firstDisplayedColumnIndex, 0));
        }
        requestUpdate(contentConsumer);
    }

    private void moveToPosition(final int firstDisplayedLineIndex, final long firstDisplayedColumnIndex, final Consumer<ViewerContent> contentConsumer) {
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            currentViewerSettings_toBeAccessedSynchronized.setFirstDisplayedLineIndex(Math.max(firstDisplayedLineIndex, 0));
            currentViewerSettings_toBeAccessedSynchronized.setFirstDisplayedColumnIndex(Math.max(firstDisplayedColumnIndex, 0));
        }
        requestUpdate(contentConsumer);
    }

    private UncheckedIOException displayAndCreateException(IOException exception, String verb)  {
        String message = "Unable to " + verb + " file '" + fileName + "': " + exception.getClass().getSimpleName();
        messageConsumer.accept(new MessageInfo("Unable to " + verb + " file", message, JOptionPane.ERROR_MESSAGE));
        return new UncheckedIOException(message, exception);
    }

    @Override
    public void onGoOneLineUp(final Consumer<ViewerContent> contentConsumer) {
        moveVertical(-1, contentConsumer);
    }

    @Override
    public void onGoOneLineDown(final Consumer<ViewerContent> contentConsumer) {
        moveVertical(1, contentConsumer);
    }

    @Override
    public void onGoOneColumnLeft(final Consumer<ViewerContent> contentConsumer) {
        moveHorizontal(-1, contentConsumer);
    }

    @Override
    public void onGoOneColumnRight(final Consumer<ViewerContent> contentConsumer) {
        moveHorizontal(1, contentConsumer);
    }

    @Override
    public void onGoOnePageUp(final Consumer<ViewerContent> contentConsumer) {
        ViewerSettings viewerSettings;
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            viewerSettings = new ViewerSettings(currentViewerSettings_toBeAccessedSynchronized);
        }
        moveVertical((viewerSettings.getDisplayedLines() - 1) * -1, contentConsumer);
    }

    @Override
    public void onGoOnePageDown(final Consumer<ViewerContent> contentConsumer) {
        ViewerSettings viewerSettings;
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            viewerSettings = new ViewerSettings(currentViewerSettings_toBeAccessedSynchronized);
        }
        moveVertical(viewerSettings.getDisplayedLines() - 1, contentConsumer);
    }

    @Override
    public void onGoOnePageLeft(final Consumer<ViewerContent> contentConsumer) {
        ViewerSettings viewerSettings;
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            viewerSettings = new ViewerSettings(currentViewerSettings_toBeAccessedSynchronized);
        }
        moveHorizontal((-1)*(viewerSettings.getDisplayedColumns() - 1), contentConsumer);
    }

    @Override
    public void onGoOnePageRight(final Consumer<ViewerContent> contentConsumer) {
        ViewerSettings viewerSettings;
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            viewerSettings = new ViewerSettings(currentViewerSettings_toBeAccessedSynchronized);
        }
        moveHorizontal(viewerSettings.getDisplayedColumns() - 1, contentConsumer);
    }

    @Override
    public void onGoToLineBegin(final Consumer<ViewerContent> contentConsumer) {
        moveToHorizontalPosition(0, contentConsumer);
    }

    @Override
    public void onGoToLineEnd(final Consumer<ViewerContent> contentConsumer) {
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
            moveToHorizontalPosition(statisticOfCurrentLine.getLengthInCharacters() - viewerSettings.getDisplayedColumns(), contentConsumer);
        }
    }

    @Override
    public void onGoToFirstLine(final Consumer<ViewerContent> contentConsumer) {
        moveToVerticalPosition(0, contentConsumer);
    }

    @Override
    public void onGoToLastLine(final Consumer<ViewerContent> contentConsumer) {
        ViewerSettings viewerSettings;
        synchronized (currentViewerSettings_toBeAccessedSynchronized) {
            viewerSettings = new ViewerSettings(currentViewerSettings_toBeAccessedSynchronized);
        }

        int line;
        synchronized (lineStatistics_toBeAccessedSynchronized) {
            line = Math.max(0, lineStatistics_toBeAccessedSynchronized.size() - viewerSettings.getDisplayedLines());
        }

        moveToVerticalPosition(line, contentConsumer);
    }

    @Override
    public void onGoTo(int firstDisplayedLineIndex, int firstDisplayedColumnIndex, Consumer<ViewerContent> contentConsumer) {
        moveToPosition(firstDisplayedLineIndex, firstDisplayedColumnIndex, contentConsumer);
    }

    @Override
    public void onLargeJumpUp(final Consumer<ViewerContent> contentConsumer) {
        moveVertical((-1) * largeLinesJump, contentConsumer);
    }

    @Override
    public void onLargeJumpDown(final Consumer<ViewerContent> contentConsumer) {
        moveVertical(largeLinesJump, contentConsumer);
    }

    @Override
    public void onLargeJumpLeft(final Consumer<ViewerContent> contentConsumer) {
        moveHorizontal((-1) * largeColumnsJump, contentConsumer);
    }

    @Override
    public void onLargeJumpRight(final Consumer<ViewerContent> contentConsumer) {
        moveHorizontal(largeColumnsJump, contentConsumer); // TODO: Do we need to stop it, when we reach end of line? which line?
    }
}
