package com.sab_engineering.tools.sab_viewer.gui;

import com.sab_engineering.tools.sab_viewer.io.LineContent;

import javax.swing.Action;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class GuiSwing {

    public static final String AMK_GO_ONE_LINE_UP = "up";
    public static final String AMK_GO_ONE_LINE_DOWN = "down";
    public static final String AMK_GO_ONE_PAGE_UP = "upPage";
    public static final String AMK_GO_ONE_PAGE_DOWN = "downPage";
    public static final String AMK_GO_ONE_COLUMN_LEFT = "left";
    public static final String AMK_GO_ONE_COLUMN_RIGHT = "right";
    public static final String AMK_GO_TO_LINE_BEGIN = "home";
    public static final String AMK_GO_TO_LINE_END = "end";
    public static final String AMK_GO_TO_FIST_LINE = "ctrl+home";
    public static final String AMK_GO_TO_LAST_LINE = "ctrl+end";
    public static final String AMK_LARGE_JUMP_UP = "ctrl+up";
    public static final String AMK_LARGE_JUMP_DOWN = "ctrl+down";
    public static final String AMK_LARGE_JUMP_LEFT = "ctrl+left";
    public static final String AMK_LARGE_JUMP_RIGHT = "ctrl+right";
    public static final String AMK_OPEN_FILE = "ctrl+o";
    public static final String AMK_GO_ONE_PAGE_LEFT = "go_one_page_left";
    public static final String AMK_GO_ONE_PAGE_RIGHT = "go_one_page_right";

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

    private void onOpenFile() {
        final JFileChooser fileChooser = new JFileChooser();
//                fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
        final int result = fileChooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            final File selectedFile = fileChooser.getSelectedFile();
            uiListener.onOpenFile(selectedFile.getPath(), this::updateLines, this::showMessageDialog);
        }
    }

    private void prepareGui() {
        frame = new JFrame("SAB-Viewer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 800); // TODO: This we need to calculate based on geometry and font size

        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(Font.decode(Font.MONOSPACED));

        prepareActionMapOfTextArea(textArea);
        prepareInputMapOfTextArea(textArea);

        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        menuBar.add(fileMenu);

        JMenuItem openMenuItem = new JMenuItem("Open");
        fileMenu.add(openMenuItem);
        openMenuItem.addActionListener(e -> onOpenFile());

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

        frame.getContentPane().add(BorderLayout.NORTH, menuBar);
        frame.getContentPane().add(BorderLayout.CENTER, textArea);
    }

    private void prepareInputMapOfTextArea(final JTextArea textArea) {
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), AMK_GO_ONE_LINE_UP);
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), AMK_GO_ONE_LINE_DOWN);
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), AMK_GO_ONE_PAGE_UP);
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), AMK_GO_ONE_PAGE_DOWN);
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), AMK_GO_ONE_COLUMN_LEFT);
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), AMK_GO_ONE_COLUMN_RIGHT);
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), AMK_GO_TO_LINE_BEGIN);
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_END, 0), AMK_GO_TO_LINE_END);
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, InputEvent.CTRL_DOWN_MASK), AMK_GO_TO_FIST_LINE);
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_END, InputEvent.CTRL_DOWN_MASK), AMK_GO_TO_LAST_LINE);
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.CTRL_DOWN_MASK), AMK_LARGE_JUMP_UP);
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.CTRL_DOWN_MASK), AMK_LARGE_JUMP_DOWN);
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.CTRL_DOWN_MASK), AMK_LARGE_JUMP_LEFT);
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.CTRL_DOWN_MASK), AMK_LARGE_JUMP_RIGHT);
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK), AMK_GO_ONE_PAGE_UP);
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK), AMK_GO_ONE_PAGE_DOWN);
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK), AMK_GO_ONE_PAGE_LEFT);
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK), AMK_GO_ONE_PAGE_RIGHT);
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK), AMK_OPEN_FILE);
    }

    private void prepareActionMapOfTextArea(final JTextArea textArea) {
        textArea.getActionMap().put(AMK_GO_ONE_LINE_UP, new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uiListener.onGoOneLineUp(GuiSwing.this::updateLines);
            }
        });
        textArea.getActionMap().put(AMK_GO_ONE_PAGE_UP, new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uiListener.onGoOnePageUp(GuiSwing.this::updateLines);
            }
        });
        textArea.getActionMap().put(AMK_GO_ONE_LINE_DOWN, new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uiListener.onGoOneLineDown(GuiSwing.this::updateLines);
            }
        });
        textArea.getActionMap().put(AMK_GO_ONE_PAGE_DOWN, new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uiListener.onGoOnePageDown(GuiSwing.this::updateLines);
            }
        });
        textArea.getActionMap().put(AMK_GO_ONE_COLUMN_LEFT, new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uiListener.onGoOneColumnLeft(GuiSwing.this::updateLines);
            }
        });
        textArea.getActionMap().put(AMK_GO_ONE_COLUMN_RIGHT, new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uiListener.onGoOneColumnRight(GuiSwing.this::updateLines);
            }
        });
        textArea.getActionMap().put(AMK_GO_TO_LINE_BEGIN, new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uiListener.onGoToLineBegin(GuiSwing.this::updateLines);
            }
        });
        textArea.getActionMap().put(AMK_GO_TO_LINE_END, new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uiListener.onGoToLineEnd(GuiSwing.this::updateLines);
            }
        });
        textArea.getActionMap().put(AMK_GO_TO_FIST_LINE, new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uiListener.onGoToFirstLine(GuiSwing.this::updateLines);
            }
        });
        textArea.getActionMap().put(AMK_GO_TO_LAST_LINE, new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uiListener.onGoToLastLine(GuiSwing.this::updateLines);
            }
        });
        textArea.getActionMap().put(AMK_LARGE_JUMP_UP, new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uiListener.onLargeJumpUp(GuiSwing.this::updateLines);
            }
        });
        textArea.getActionMap().put(AMK_LARGE_JUMP_DOWN, new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uiListener.onLargeJumpDown(GuiSwing.this::updateLines);
            }
        });
        textArea.getActionMap().put(AMK_LARGE_JUMP_LEFT, new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uiListener.onLargeJumpLeft(GuiSwing.this::updateLines);
            }
        });
        textArea.getActionMap().put(AMK_LARGE_JUMP_RIGHT, new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uiListener.onLargeJumpRight(GuiSwing.this::updateLines);
            }
        });
        textArea.getActionMap().put(AMK_GO_ONE_PAGE_LEFT, new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uiListener.onGoOnePageLeft(GuiSwing.this::updateLines);
            }
        });
        textArea.getActionMap().put(AMK_GO_ONE_PAGE_RIGHT, new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uiListener.onGoOnePageRight(GuiSwing.this::updateLines);
            }
        });
        textArea.getActionMap().put(AMK_OPEN_FILE, new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onOpenFile();
            }
        });
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
