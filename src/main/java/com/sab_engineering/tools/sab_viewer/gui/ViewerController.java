package com.sab_engineering.tools.sab_viewer.gui;

import com.sab_engineering.tools.sab_viewer.io.LineContent;
import com.sab_engineering.tools.sab_viewer.io.LineStatistics;
import com.sab_engineering.tools.sab_viewer.io.Reader;
import com.sab_engineering.tools.sab_viewer.io.Scanner;

import javax.swing.JOptionPane;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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

    private Reader reader;

    private final Consumer<MessageInfo> messageConsumer;
    private final Thread scannerThread;

    public ViewerController(final String fileName, final int displayedLines, final int displayedColumns, final Consumer<Collection<LineContent>> linesConsumer, final Consumer<MessageInfo> messageConsumer) {
        firstDisplayedLineIndex = 0;
        firstDisplayedColumnIndex = 0;

        currentlyDisplayedLines = displayedLines;
        currentlyDisplayedColumns = displayedColumns;

        initialLinesStillRelevant = new AtomicBoolean(true);
        initialLines_toBeAccessedSynchronized = new ArrayList<>(currentlyDisplayedLines);
        lineStatistics_toBeAccessedSynchronized = new ArrayList<>(100000);

        this.fileName = fileName;
        this.messageConsumer = messageConsumer;
        scannerThread = new Thread(() -> scanFile(linesConsumer), "Scanner");
        scannerThread.start();
    }

    @Override
    public void interruptBackgroundThreads() {
        scannerThread.interrupt();
    }

    @Override
    public void resize(final int displayedLines, final int displayedColumns, final Consumer<Collection<LineContent>> linesConsumer) {
        if (currentlyDisplayedLines != displayedLines || currentlyDisplayedColumns != displayedColumns) {
            currentlyDisplayedLines = displayedLines;
            currentlyDisplayedColumns = displayedColumns;
            update(linesConsumer);
        }
    }

    private void update(final Consumer<Collection<LineContent>> linesConsumer) {
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
        linesConsumer.accept(lineContents);
    }

    // this method is supposed to be executed in scannerThread
    private void scanFile(final Consumer<Collection<LineContent>> lineConsumer) {
        try {
            Scanner.scanFile(fileName, charset, lineContent -> addInitialContent(lineContent, lineConsumer), currentlyDisplayedLines, currentlyDisplayedColumns, this::addLineStatistics);
        } catch (IOException ioException) {
            initialLinesStillRelevant.set(false);
            throw displayAndCreateException(ioException, "scan");
        }
    }

    private void addInitialContent(final LineContent lineContent, final Consumer<Collection<LineContent>> lineConsumer) {
        if (initialLinesStillRelevant.get()) {
            synchronized (initialLines_toBeAccessedSynchronized) {
                initialLines_toBeAccessedSynchronized.add(lineContent);
                ArrayList<LineContent> contents = new ArrayList<>(initialLines_toBeAccessedSynchronized);
                if (initialLinesStillRelevant.get()) {
                    lineConsumer.accept(contents);
                }
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
        String message = "Unable to " + verb + " file '" + fileName + "': " + exception.getClass().getSimpleName();
        messageConsumer.accept(new MessageInfo("Unable to " + verb + " file", message, JOptionPane.ERROR_MESSAGE));
        return new UncheckedIOException(message, exception);
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
            moveToPosition(firstDisplayedLineIndex, statisticOfCurrentLine.getLengthInCharacters() - currentlyDisplayedColumns, linesConsumer);
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
