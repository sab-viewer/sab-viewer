package com.sab_engineering.tools.sab_viewer.gui;

import com.sab_engineering.tools.sab_viewer.io.LineContent;
import com.sab_engineering.tools.sab_viewer.io.LineStatistics;
import com.sab_engineering.tools.sab_viewer.io.Reader;
import com.sab_engineering.tools.sab_viewer.io.Scanner;

import javax.swing.Action;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.Vector;

public class GuiViewer {

    // TODO: Remove this and use main entry point instead (control with command line options or so).
    public static void main(String[] args) {
        new GuiViewer(args[0]).show();
    }

    private final String fileName;

    private int maxRows = 20; // TODO: This should be changed on window resize
    private int maxColumns = 40;

    private int firstDisplayedLineIndex = 0; // index in lineStatistics

    private JTextArea textArea;
    private int consumedLinesCount = 0;
    private final List<LineStatistics> lineStatistics = new Vector<>();

    public GuiViewer(final String fileName) {
        this.fileName = fileName;
    }

    public void show() {
        prepareGui();
        Thread scannerThread = new Thread(
                () -> Scanner.scanFile(fileName, this::consumeLine, maxColumns, lineStatistics::add),
                "Scanner"
        );
        scannerThread.start();
    }

    public void update() {
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
        final List<LineContent> lineContents = Reader.readSpecificLines(fileName, lineStatistics.subList(firstDisplayedLineIndex, oneAfterLastLineIndex), maxColumns);
        final StringBuilder text = new StringBuilder();
        for (LineContent lineContent : lineContents) {
            text.append(lineContent.getVisibleContent()).append("\n");
        }
        text.deleteCharAt(text.length() - 1);
        textArea.setText(text.toString());
    }

    private void consumeLine(LineContent lineContent) {
        if (consumedLinesCount < maxRows) {
            if (!textArea.getText().isEmpty()) {
                textArea.append("\n");
            }
            textArea.append(lineContent.getVisibleContent());
            consumedLinesCount++;
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
        JFrame frame = new JFrame("SAB-Viewer");
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
