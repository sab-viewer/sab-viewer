package com.sab_engineering.tools.sab_viewer.gui;

import com.sab_engineering.tools.sab_viewer.io.LineContent;
import com.sab_engineering.tools.sab_viewer.io.LineStatistics;
import com.sab_engineering.tools.sab_viewer.io.Reader;
import com.sab_engineering.tools.sab_viewer.io.Scanner;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

public class GuiViewer {
    private final String fileName;

    private int maxRows = 50; // TODO: This should be changed on window resize
    private int maxColumns = 150;

    private int firstDisplayedLineIndex; // index in lineStatistics
    private int lineOffset; // vertical scrolling

    private JFrame frame;
    private JTextArea textArea;

    private final Semaphore initialContentLock;
    private List<LineContent> initialContent;
    private final Semaphore lineStatisticsLock;
    private final List<LineStatistics> lineStatistics;

    private IOException scannerException = null;

    public GuiViewer(final String fileName) {
        this.fileName = fileName;
        firstDisplayedLineIndex = 0;
        lineOffset = 0;
        initialContentLock = new Semaphore(1);
        initialContent = new ArrayList<>(maxRows);
        lineStatisticsLock = new Semaphore(1);
        lineStatistics = new ArrayList<>(100000);
    }

    public void show() {
        Thread scannerThread = new Thread(
                () -> {
                    try {
                        Scanner.scanFile(fileName, this::addInitialContent, maxColumns, this::addLineStatistics);
                    } catch (IOException ioException) {
                        clearInitialContent();
                        scannerException = ioException;
                    }
                },
                "Scanner"
        );
        scannerThread.start();

        prepareGui();

        int consumedLinesCount = 0;
        while (scannerThread.isAlive() || (consumedLinesCount < maxRows && initialContent != null)) {
            try {
                initialContentLock.acquire();
                try {
                    while (initialContent != null && consumedLinesCount < initialContent.size()) {
                        LineContent newLine = initialContent.get(consumedLinesCount);
                        consumedLinesCount += 1;

                        if (!textArea.getText().isEmpty()) {
                            textArea.append("\n");
                        }
                        textArea.append(newLine.getVisibleContent());
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
        clearInitialContent();

        List<LineStatistics> linesToRead;
        try {
            lineStatisticsLock.acquire();
            try {
                if (lineStatistics.isEmpty()) {
                    return;
                }
                if (firstDisplayedLineIndex >= lineStatistics.size()) {
                    firstDisplayedLineIndex = lineStatistics.size() - 1;
                }
                final int oneAfterLastLineIndex = Math.min(lineStatistics.size(), firstDisplayedLineIndex + maxRows);
                linesToRead = new ArrayList<>(lineStatistics.subList(firstDisplayedLineIndex, oneAfterLastLineIndex));
            } finally {
                lineStatisticsLock.release();
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException("Unable to get statistics lock", e);
        }

        final List<LineContent> lineContents;
        try {
            lineContents = Reader.readSpecificLines(fileName, linesToRead, lineOffset, maxColumns);
        } catch (IOException ioException) {
            throw displayAndCreateException(ioException, "read");
        }
        boolean lineAppended = false;
        final StringBuilder text = new StringBuilder();
        for (LineContent lineContent : lineContents) {
            if (lineAppended) {
                text.append("\n");
            }
            text.append(lineContent.getVisibleContent());
            lineAppended = true;
        }
        textArea.setText(text.toString());
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
                        if (initialContent != null && initialContent.size() < maxRows) {
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
        try {
            lineStatisticsLock.acquire();
            try {
                lineStatistics.add(statistics);
            } finally {
                lineStatisticsLock.release();
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException("Unable to get statistics lock", e);
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

    private void prepareGui() {
        frame = new JFrame("SAB-Viewer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 800); // TODO: This we need to calculate based on geometry and font size

        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        menuBar.add(fileMenu);
        JMenuItem openMenuItem = new JMenuItem("Open");
        fileMenu.add(openMenuItem);

        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(Font.decode(Font.MONOSPACED));

        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "up");
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), "upPage");
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "down");
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), "downPage");
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "left");
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "right");
        textArea.getActionMap().put("up", new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                moveVertical(-1);
            }
        });
        textArea.getActionMap().put("upPage", new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                moveVertical((maxRows - 1) * -1);
            }
        });
        textArea.getActionMap().put("down", new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                moveVertical(1);
            }
        });
        textArea.getActionMap().put("downPage", new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                moveVertical((maxRows - 1));
            }
        });
        textArea.getActionMap().put("left", new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                moveHorizontal(-1);
            }
        });
        textArea.getActionMap().put("right", new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                moveHorizontal(1);
            }
        });

        frame.getContentPane().add(BorderLayout.NORTH, menuBar);
        frame.getContentPane().add(BorderLayout.CENTER, textArea);
        frame.setVisible(true);
    }

    private UncheckedIOException displayAndCreateException(IOException exception, String verb)  {
        String message = "Unable to " + verb + " file '" + fileName + "': " + exception.getClass().getSimpleName();
        JOptionPane.showMessageDialog(frame, message, "Unable to " + verb + " file", JOptionPane.ERROR_MESSAGE);
        return new UncheckedIOException(message, exception);
    }

    private static abstract class ActionStub implements Action {
        @Override
        public Object getValue(String key) {
            return null;
        }

        @Override
        public void putValue(String key, Object value) {

        }

        @Override
        public void setEnabled(boolean b) {

        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener listener) {

        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener listener) {

        }
    }
}
