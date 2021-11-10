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
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class ViewerController implements ViewerUiListener {
    private final Charset charset = StandardCharsets.UTF_8;
    private final String fileName;

    private int currentlyDisplayedLines;
    private int currentlyDisplayedColumns;

    private final int largeLinesJump = 500; // TODO: Should be modifiable by user in settings
    private final int largeColumnsJump = 500;

    private int firstDisplayedLineIndex; // index in lineStatistics
    private long firstDisplayedColumnIndex;

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

    public ViewerController(final String fileName, final int displayedLines, final int displayedColumns, final Consumer<ViewerContent> contentConsumer, final Consumer<ScannerState> stateConsumer, final Consumer<MessageInfo> messageConsumer) {
        firstDisplayedLineIndex = 0;
        firstDisplayedColumnIndex = 0;

        currentlyDisplayedLines = displayedLines;
        currentlyDisplayedColumns = displayedColumns;

        initialLinesStillRelevant = new AtomicBoolean(true);
        initialLines_toBeAccessedSynchronized = new ArrayList<>(currentlyDisplayedLines);
        lineStatistics_toBeAccessedSynchronized = new ArrayList<>(100000);

        this.fileName = fileName;
        this.stateConsumer = stateConsumer;
        this.messageConsumer = messageConsumer;
        scannerThread = new Thread(() -> scanFile(contentConsumer), "Scanner");
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
        if (currentlyDisplayedLines != displayedLines || currentlyDisplayedColumns != displayedColumns) {
            currentlyDisplayedLines = displayedLines;
            currentlyDisplayedColumns = displayedColumns;

            requestUpdate(contentConsumer);
        }
    }

    private void requestUpdate(final Consumer<ViewerContent> contentConsumer){
        contentConsumerWaitingForReader = contentConsumer;
        readerSignal.release();
    }

    // this method is supposed to be executed in scannerThread
    private void scanFile(final Consumer<ViewerContent> contentConsumer) {
        try {
            Scanner.scanFile(fileName, charset, lineContent -> addInitialContent(lineContent, contentConsumer), currentlyDisplayedLines, currentlyDisplayedColumns, this::updateStatistics);

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
                reader = new Reader(fileName, charset);
            }
            lineContents = reader.readSpecificLines(linesToRead, firstDisplayedColumnIndex, currentlyDisplayedColumns);
        } catch (IOException ioException) {
            throw displayAndCreateException(ioException, "read");
        }
        contentConsumer.accept(new ViewerContent(lineContents, firstDisplayedLineIndex + 1, firstDisplayedColumnIndex + 1));
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
        if ((numberOfLines < currentlyDisplayedLines && numberOfLines % 10 == 0) || numberOfLines % 5000 == 0) {
            stateConsumer.accept(new ScannerState(numberOfLines, statistics.getCharacterPositionsInBytes()[0] + statistics.getLengthInBytes(), false));
        }
    }

    private void moveVertical(final int lineOffset, final Consumer<ViewerContent> contentConsumer) {
        moveToPosition(firstDisplayedLineIndex + lineOffset, firstDisplayedColumnIndex, contentConsumer);
    }

    private void moveHorizontal(final long columnOffset, final Consumer<ViewerContent> contentConsumer) {
        moveToPosition(firstDisplayedLineIndex, firstDisplayedColumnIndex + columnOffset, contentConsumer);
    }

    private void moveToPosition(final int firstDisplayedLineIndex, final long firstDisplayedColumnIndex, final Consumer<ViewerContent> contentConsumer) {
        this.firstDisplayedLineIndex = Math.max(firstDisplayedLineIndex, 0);
        this.firstDisplayedColumnIndex = Math.max(firstDisplayedColumnIndex, 0);
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
        moveVertical((currentlyDisplayedLines - 1) * -1, contentConsumer);
    }

    @Override
    public void onGoOnePageDown(final Consumer<ViewerContent> contentConsumer) {
        moveVertical(currentlyDisplayedLines - 1, contentConsumer);
    }

    @Override
    public void onGoOnePageLeft(final Consumer<ViewerContent> contentConsumer) {
        moveHorizontal((-1)*(currentlyDisplayedColumns - 1), contentConsumer);
    }

    @Override
    public void onGoOnePageRight(final Consumer<ViewerContent> contentConsumer) {
        moveHorizontal(currentlyDisplayedColumns - 1, contentConsumer); // TODO: Do we need to stop it, when we reach end of line? which line?
    }

    @Override
    public void onGoToLineBegin(final Consumer<ViewerContent> contentConsumer) {
        moveToPosition(firstDisplayedLineIndex, 0, contentConsumer);
    }

    @Override
    public void onGoToLineEnd(final Consumer<ViewerContent> contentConsumer) {
        LineStatistics statisticOfCurrentLine = null;
        synchronized (lineStatistics_toBeAccessedSynchronized) {
            if (lineStatistics_toBeAccessedSynchronized.size() > firstDisplayedLineIndex) {
                statisticOfCurrentLine = ViewerController.this.lineStatistics_toBeAccessedSynchronized.get(firstDisplayedLineIndex);
            }
        }
        if (statisticOfCurrentLine != null) {
            moveToPosition(firstDisplayedLineIndex, statisticOfCurrentLine.getLengthInCharacters() - currentlyDisplayedColumns, contentConsumer);
        }
    }

    @Override
    public void onGoToFirstLine(final Consumer<ViewerContent> contentConsumer) {
        moveToPosition(0, firstDisplayedColumnIndex, contentConsumer);
    }

    @Override
    public void onGoToLastLine(final Consumer<ViewerContent> contentConsumer) {
        int line;
        synchronized (lineStatistics_toBeAccessedSynchronized) {
            line = Math.max(0, lineStatistics_toBeAccessedSynchronized.size() - currentlyDisplayedLines);
        }
        moveToPosition(line, firstDisplayedColumnIndex, contentConsumer);
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
