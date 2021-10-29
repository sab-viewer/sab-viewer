package com.sab_engineering.tools.sab_viewer.gui;

import com.sab_engineering.tools.sab_viewer.io.LineContent;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class GuiSwing {

    private final ViewerUiListener uiListener;
    private final LinesExchanger linesExchanger;

    private JFrame frame;
    private JTextArea textArea;

    public GuiSwing(final ViewerUiListener uiListener) {
        this.uiListener = uiListener;
        linesExchanger = new LinesExchanger();
        prepareGui();
    }

    public void start(final Optional<String> maybeFilePath) {
        frame.setVisible(true);
        maybeFilePath.ifPresent(filePath -> uiListener.onOpenFile(filePath, this::updateLines));
    }

    private void setLines(final Collection<LineContent> lines) {
        boolean lineAppended = false;
        final StringBuilder text = new StringBuilder();
        for (LineContent lineContent : lines) {
            if (lineAppended) {
                text.append("\n");
            }
            text.append(lineContent.getVisibleContent());
            lineAppended = true;
        }
        textArea.setText(text.toString());
    }

    // TODO: probably we can pass directly lineExchanger::setLines as a callback
    // supposed to be called from other thread
    public void updateLines(final Collection<LineContent> lines) {
        linesExchanger.setLines(lines); // called from the worker thread
        SwingUtilities.invokeLater(
                () -> linesExchanger.consumeLines(this::setLines) // called from the GUI thread (queued in the GUI event loop)
        );
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
                uiListener.onGoOneLineUp(GuiSwing.this::updateLines);
            }
        });
        textArea.getActionMap().put("upPage", new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uiListener.onGoOnePageUp(GuiSwing.this::updateLines);
            }
        });
        textArea.getActionMap().put("down", new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uiListener.onGoOneLineDown(GuiSwing.this::updateLines);
            }
        });
        textArea.getActionMap().put("downPage", new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uiListener.onGoOnePageDown(GuiSwing.this::updateLines);
            }
        });
        textArea.getActionMap().put("left", new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uiListener.onGoOneColumnLeft(GuiSwing.this::updateLines);
            }
        });
        textArea.getActionMap().put("right", new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uiListener.onGoOneColumnRight(GuiSwing.this::updateLines);
            }
        });

        frame.getContentPane().add(BorderLayout.NORTH, menuBar);
        frame.getContentPane().add(BorderLayout.CENTER, textArea);
    }

    // TODO: Find the way to show messages from the
    public void showMessageDialog(Object message, String title, int messageType) {
        JOptionPane.showMessageDialog(frame, message, title, messageType);
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

    private static class LinesExchanger {
        boolean newLinesWereWritten = false;
        List<LineContent> lines;

        public synchronized void setLines(final Collection<LineContent> newLines) {
            lines = new ArrayList<>(newLines);
            newLinesWereWritten = true;
        }

        public synchronized void consumeLines(final Consumer<List<LineContent>> linesConsumer) {
            if (newLinesWereWritten) {
                newLinesWereWritten = false;
                linesConsumer.accept(lines);
            }
        }
    }
}
