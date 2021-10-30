package com.sab_engineering.tools.sab_viewer.gui;

import com.sab_engineering.tools.sab_viewer.io.LineContent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
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

    public GuiSwing() {
        this.uiListener = new ViewerController().getViewerListener(); // if we every need to store direct access to controller, then we can change the way it is done here
        linesExchanger = new LinesExchanger();
        prepareGui();
    }

    public void start(final Optional<String> maybeFilePath) {
        frame.setVisible(true);
        maybeFilePath.ifPresent(filePath -> uiListener.onOpenFile(filePath, this::updateLines, this::showMessageDialog));
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

        JMenuItem goToMenuItem = new JMenuItem("GoTo");
        goToMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_DOWN_MASK));
        goToMenuItem.addActionListener(actionEvent -> {
            String result = (String)JOptionPane.showInputDialog(
                    frame,
                    "Enter Line[:Column]",
                    "GoTo",
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    null,
                    "1:1"
            );
            handleGoTo(result);
        });
        JMenu navigateMenu = new JMenu("Navigate");
        navigateMenu.add(goToMenuItem);
        menuBar.add(navigateMenu);

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

    private void handleGoTo(String result) {
        if (result.contains(":")) {
            String[] address = result.split(":");
            if (address.length == 2) {
                if (address[0].matches("^\\d+$") && address[1].matches("^\\d+$")) {
                    int line = Integer.parseInt(address[0]) - 1;
                    int column = Integer.parseInt(address[1]) - 1;
                    uiListener.onGoTo(line, column, GuiSwing.this::updateLines);

                    return;
                }
            }
        } else if (result.matches("^\\d+$")) {
            int line = Integer.parseInt(result) - 1;
            uiListener.onGoTo(line, 0, GuiSwing.this::updateLines);

            return;
        }
        showMessageDialog(new MessageInfo("Invalid GoTo address", "The GoTo Address '" + result + "' cannot be parsed", JOptionPane.ERROR_MESSAGE));
    }

    public void showMessageDialog(MessageInfo messageInfo) {
        JOptionPane.showMessageDialog(frame, messageInfo.getMessage(), messageInfo.getTitle(), messageInfo.getMessageType());
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
