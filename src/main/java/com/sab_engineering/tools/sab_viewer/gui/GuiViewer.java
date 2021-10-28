package com.sab_engineering.tools.sab_viewer.gui;

import com.sab_engineering.tools.sab_viewer.io.LineContent;
import com.sab_engineering.tools.sab_viewer.io.LineStatistics;
import com.sab_engineering.tools.sab_viewer.io.Reader;
import com.sab_engineering.tools.sab_viewer.io.Scanner;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Vector;

public class GuiViewer {
    private final String fileName;

    private int maxRows = 20; // TODO: This should be changed on window resize
    private int maxColumns = 40;

    private int firstDisplayedLineIndex = 0; // index in lineStatistics

    private JFrame frame;
    private JTextArea textArea;

    private List<LineContent> initialContent;
    private final List<LineStatistics> lineStatistics;

    private IOException scannerException = null;

    public GuiViewer(final String fileName) {
        this.fileName = fileName;
        lineStatistics = new Vector<>(100000);
        initialContent = new Vector<>(maxRows);
    }

    public void show() {
        Thread scannerThread = new Thread(
                () -> {
                    try {
                        Scanner.scanFile(fileName, this::consumeLine, maxColumns, lineStatistics::add);
                    } catch (IOException ioException) {
                        scannerException = ioException;
                    }
                },
                "Scanner"
        );
        scannerThread.start();

        prepareGui();

        int consumedLinesCount = 0;
        while (scannerThread.isAlive() || (initialContent != null && consumedLinesCount < initialContent.size())) {
            List<LineContent> content = this.initialContent;
            while (content != null && consumedLinesCount < content.size()) {
                LineContent newLine = content.get(consumedLinesCount);
                consumedLinesCount += 1;

                if (!textArea.getText().isEmpty()) {
                    textArea.append("\n");
                }
                textArea.append(newLine.getVisibleContent());
            }
            try {
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

    private UncheckedIOException displayAndCreateException(IOException exception, String verb)  {
        String message = "Unable to " + verb + " file '" + fileName + "': " + exception.getClass().getSimpleName();
        JOptionPane.showMessageDialog(frame, message, "Unable to " + verb + " file", JOptionPane.ERROR_MESSAGE);
        return new UncheckedIOException(message, exception);
    }

    public void update() {
        initialContent = null; // prevent further updates by scanner
        textArea.setText("");
        if (lineStatistics.isEmpty()) {
            return;
        }
        if (firstDisplayedLineIndex < 0) {
            firstDisplayedLineIndex = 0;
        }
        if (firstDisplayedLineIndex >= lineStatistics.size()) {
            firstDisplayedLineIndex = lineStatistics.size() - 1;
        }
        final int oneAfterLastLineIndex = Math.min(lineStatistics.size(), firstDisplayedLineIndex + maxRows);
        final List<LineContent> lineContents;
        try {
            lineContents = Reader.readSpecificLines(fileName, lineStatistics.subList(firstDisplayedLineIndex, oneAfterLastLineIndex), 0, maxColumns);
        } catch (IOException ioException) {
            throw displayAndCreateException(ioException, "read");
        }
        final StringBuilder text = new StringBuilder();
        for (LineContent lineContent : lineContents) {
            if (text.length() > 0) {
                text.append("\n");
            }
            text.append(lineContent.getVisibleContent());
        }
        textArea.setText(text.toString());
    }

    private void consumeLine(LineContent lineContent) {
        List<LineContent> content = this.initialContent;
        if (content != null && content.size() < maxRows) {
            content.add(lineContent);
        }
    }

    private void moveDown() {
        firstDisplayedLineIndex++;
        update();
    }

    private void moveUp() {
        firstDisplayedLineIndex--;
        update();
    }

    private void prepareGui() {
        frame = new JFrame("SAB-Viewer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 400); // TODO: This we need to calculate based on geometry and font size

        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        menuBar.add(fileMenu);
        JMenuItem openMenuItem = new JMenuItem("Open");
        fileMenu.add(openMenuItem);

        textArea = new JTextArea();

        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "up");
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "down");
        textArea.getActionMap().put("up", new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                moveUp();
            }
        });
        textArea.getActionMap().put("down", new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                moveDown();
            }
        });

        frame.getContentPane().add(BorderLayout.NORTH, menuBar);
        frame.getContentPane().add(BorderLayout.CENTER, textArea);
        frame.setVisible(true);
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
