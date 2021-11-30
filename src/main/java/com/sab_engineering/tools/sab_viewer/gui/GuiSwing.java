package com.sab_engineering.tools.sab_viewer.gui;

import com.sab_engineering.tools.sab_viewer.controller.MessageInfo;
import com.sab_engineering.tools.sab_viewer.controller.ScannerState;
import com.sab_engineering.tools.sab_viewer.controller.ViewerContent;
import com.sab_engineering.tools.sab_viewer.controller.ViewerController;
import com.sab_engineering.tools.sab_viewer.controller.ViewerUiListener;
import com.sab_engineering.tools.sab_viewer.io.LinePreview;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;

public class GuiSwing {

    public static final String AMK_GO_ONE_LINE_UP = "up";
    public static final String AMK_GO_ONE_LINE_DOWN = "down";
    public static final String AMK_GO_ONE_COLUMN_LEFT = "left";
    public static final String AMK_GO_ONE_COLUMN_RIGHT = "right";

    public static final String AMK_GO_ONE_PAGE_UP = "upPage";
    public static final String AMK_GO_ONE_PAGE_DOWN = "downPage";
    public static final String AMK_GO_ONE_PAGE_LEFT = "go_one_page_left";
    public static final String AMK_GO_ONE_PAGE_RIGHT = "go_one_page_right";

    public static final String AMK_LARGE_JUMP_UP = "ctrl+up";
    public static final String AMK_LARGE_JUMP_DOWN = "ctrl+down";
    public static final String AMK_LARGE_JUMP_LEFT = "ctrl+left";
    public static final String AMK_LARGE_JUMP_RIGHT = "ctrl+right";

    public static final String AMK_GO_TO_LINE_BEGIN = "home";
    public static final String AMK_GO_TO_LINE_END = "end";
    public static final String AMK_GO_TO_FIST_LINE = "ctrl+home";
    public static final String AMK_GO_TO_LAST_LINE = "ctrl+end";

    private Optional<ViewerUiListener> uiListener;
    private long uiListenerStartTimeStamp;

    private String directoryFromSelection;

    private JFrame frame;
    private JTextArea textArea;
    private final int widthPer10Chars;
    private final int heightPerLine;
    private int numberOfLinesToDisplay;
    private int numberOfColumnsToDisplay;
    private JLabel currentPosition;
    private JLabel memoryStatus;
    private JLabel scannerStatus;

    private String lastSearchTerm;

    public GuiSwing(final Optional<String> maybeFilePath) {
        uiListener = Optional.empty();
        directoryFromSelection = null;

        prepareGui();
        frame.setVisible(true);
        frame.pack();
        frame.validate();
        frame.repaint();
        frame.pack();

        FontMetrics fontMetrics = textArea.getGraphics().getFontMetrics(textArea.getFont());
        widthPer10Chars = fontMetrics.stringWidth("10   chars");
        heightPerLine = fontMetrics.getHeight();

        // put size computation and starting of scanner to the end of GUI Queue
        SwingUtilities.invokeLater(
                () -> {
                    computeSizeOfVisibleArea(textArea.getSize());

                    addResizeListener();

                    maybeFilePath.ifPresent(this::openFile);
                }
        );

        lastSearchTerm = null;
    }

    public void openFile(final String filePath) {
        openFile(new File(filePath));
    }

    public void openFile(final File fileToOpen) {
        directoryFromSelection = fileToOpen.getAbsoluteFile().getParentFile().getPath();

        Optional<ViewerUiListener> oldUiListener = uiListener;
        uiListenerStartTimeStamp = System.currentTimeMillis();
        uiListener = Optional.of(new ViewerController(fileToOpen.getPath(), StandardCharsets.UTF_8, numberOfLinesToDisplay, numberOfColumnsToDisplay, this::updateLines, this::updateState, this::showMessageDialog));

        oldUiListener.ifPresent(ViewerUiListener::interruptBackgroundThreads);
    }

    private void setLines(final Collection<LinePreview> lines) {
        boolean lineAppended = false;
        final StringBuilder text = new StringBuilder();
        for (LinePreview linePreview : lines) {
            if (lineAppended) {
                text.append("\n");
            }
            text.append(linePreview.getVisibleContent());
            lineAppended = true;
        }
        textArea.setText(text.toString());
    }

    // supposed to be called from other thread
    public void updateLines(final ViewerContent content) {
        SwingUtilities.invokeLater(
                () -> {
                    this.setLines(content.getLines());
                    this.currentPosition.setText(" " + content.getFirstDisplayedLine() + ":" + content.getFirstDisplayedColumn());
                }
        );
    }

    // supposed to be called from other thread
    private void updateState(ScannerState scannerState) {
        SwingUtilities.invokeLater(
                () -> {
                    this.memoryStatus.setText(String.format("RAM used: %4d / %4d MB (VM limit: %4d MB)", scannerState.getUsedMemory() / (1024 * 1024), scannerState.getTotalMemory() / (1024 * 1024), scannerState.getMaxMemory() / (1024 * 1024)));

                    String runningIndicator;
                    String readSpeed;
                    if (scannerState.isFinished()) {
                        runningIndicator = "";
                        readSpeed = "";
                    } else {
                        runningIndicator = "+";
                        double timePassedInSeconds = (System.currentTimeMillis() - uiListenerStartTimeStamp) / 1000.0;
                        readSpeed = String.format(" (%5.2f MB/s)", scannerState.getBytesScanned() / (1024 * 1024 * timePassedInSeconds));
                    }
                    this.scannerStatus.setText(" KB: " + scannerState.getBytesScanned() / 1024 + runningIndicator + readSpeed + "  Lines: " + scannerState.getLinesScanned() + runningIndicator + " ");
                }
        );

    }

    private void closeApplication() {
        uiListener.ifPresent(ViewerUiListener::interruptBackgroundThreads);
    }

    private void prepareGui() {
        frame = new JFrame("SAB-Viewer");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter(){
            @Override
            public void windowClosing(WindowEvent e) {
                closeApplication();
                super.windowClosing(e);
            }
        });

        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(Font.decode(Font.MONOSPACED));
        textArea.setPreferredSize(new Dimension(1000, 800));

        prepareActionMapOfTextArea(textArea);
        prepareInputMapOfTextArea(textArea);

        JMenuBar menuBar = prepareMainMenu();

        JPanel statusBar = new JPanel(new BorderLayout());
        currentPosition = new JLabel(" 1:1");
        currentPosition.setHorizontalAlignment(SwingConstants.LEFT);
        statusBar.add(currentPosition, BorderLayout.WEST);
        memoryStatus = new JLabel();
        memoryStatus.setHorizontalAlignment(SwingConstants.CENTER);
        statusBar.add(memoryStatus, BorderLayout.CENTER);
        scannerStatus = new JLabel();
        scannerStatus.setHorizontalAlignment(SwingConstants.RIGHT);
        statusBar.add(scannerStatus, BorderLayout.EAST);

        frame.getContentPane().add(BorderLayout.NORTH, menuBar);
        frame.getContentPane().add(BorderLayout.CENTER, textArea);
        frame.getContentPane().add(BorderLayout.SOUTH, statusBar);
    }

    private JMenuBar prepareMainMenu() {
        final JMenuBar menuBar = new JMenuBar();

        // File
        final JMenu fileMenu = new JMenu("File");
        menuBar.add(fileMenu);

        final JMenuItem openMenuItem = new JMenuItem("Open...");
        openMenuItem.addActionListener(e -> onOpenFile());
        openMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        fileMenu.add(openMenuItem);

        final JMenuItem fileStatisticsMenuItem = new JMenuItem("File Statistics...");
        // TODO
        fileMenu.add(fileStatisticsMenuItem);

        final JMenuItem exitMenuItem = new JMenuItem("Exit");
        exitMenuItem.addActionListener(actionEvent -> {
            frame.dispose();
            closeApplication(); // For some reason it is not called, when frame.dispose() is called... by the way, I am not sure, what is the correct order for those two calls.
        });
        fileMenu.addSeparator();
        fileMenu.add(exitMenuItem);

        // Search
        final JMenu searchMenu = new JMenu("Search");
        menuBar.add(searchMenu);

        final JMenuItem findMenuItem = new JMenuItem("Find...");
        findMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK));
        findMenuItem.addActionListener(actionEvent -> onFind());
        searchMenu.add(findMenuItem);

        final JMenuItem findNextMenuItem = new JMenuItem("Find next");
        findNextMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_DOWN_MASK));
        findNextMenuItem.addActionListener(actionEvent -> onFindNext());
        searchMenu.add(findNextMenuItem);

        // Navigate
        final JMenu navigateMenu = new JMenu("Navigate");
        menuBar.add(navigateMenu);

        final JMenuItem goToMenuItem = new JMenuItem("Go To Position...");
        goToMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_DOWN_MASK));
        goToMenuItem.addActionListener(actionEvent -> onGoToPosition());
        navigateMenu.add(goToMenuItem);

        navigateMenu.add(createMenuItem("Page Up", KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK), ViewerUiListener::onGoOnePageUp));
        navigateMenu.add(createMenuItem("Page Down", KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK), ViewerUiListener::onGoOnePageDown));
        navigateMenu.add(createMenuItem("Page Left", KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK), ViewerUiListener::onGoOnePageLeft));
        navigateMenu.add(createMenuItem("Page Right", KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK), ViewerUiListener::onGoOnePageRight));

        navigateMenu.add(createMenuItem("Large Jump Up", KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.CTRL_DOWN_MASK), ViewerUiListener::onLargeJumpUp));
        navigateMenu.add(createMenuItem("Large Jump Down", KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.CTRL_DOWN_MASK), ViewerUiListener::onLargeJumpDown));
        navigateMenu.add(createMenuItem("Large Jump Left", KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.CTRL_DOWN_MASK), ViewerUiListener::onLargeJumpLeft));
        navigateMenu.add(createMenuItem("Large Jump Right", KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.CTRL_DOWN_MASK), ViewerUiListener::onLargeJumpRight));

        navigateMenu.add(createMenuItem("Go To First Line in File", KeyStroke.getKeyStroke(KeyEvent.VK_HOME, InputEvent.CTRL_DOWN_MASK), ViewerUiListener::onGoToFirstLine));
        navigateMenu.add(createMenuItem("Go To Last Line in File", KeyStroke.getKeyStroke(KeyEvent.VK_END, InputEvent.CTRL_DOWN_MASK), ViewerUiListener::onGoToLastLine));

        // Edit
        final JMenu editMenu = new JMenu("Edit");
        menuBar.add(editMenu);

        final JMenuItem settingsMenuItem = new JMenuItem("Settings...");
        // TODO
        editMenu.add(settingsMenuItem);

        // Help
        final JMenu helpMenu = new JMenu("Help");
        menuBar.add(helpMenu);

        final JMenuItem aboutMenuItem = new JMenuItem("About...");
        // TODO
        helpMenu.add(aboutMenuItem);

        return menuBar;
    }

    private JMenuItem createMenuItem(String text, KeyStroke keyStroke, Consumer<ViewerUiListener> action) {
        final JMenuItem menuItem = new JMenuItem(text);
        menuItem.setAccelerator(keyStroke);
        menuItem.addActionListener(actionEvent -> uiListener.ifPresent(action));
        return menuItem;
    }

    private void onOpenFile() {
        final JFileChooser fileChooser = new JFileChooser(directoryFromSelection);
        final int result = fileChooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            final File selectedFile = fileChooser.getSelectedFile();
            openFile(selectedFile);
        }
    }

    private void onFind() {
        lastSearchTerm = (String)JOptionPane.showInputDialog(
                frame,
                "Enter Search Term",
                "Find",
                JOptionPane.QUESTION_MESSAGE,
                null,
                null,
                ""
        );
        if (lastSearchTerm != null) {
            handleFind(lastSearchTerm);
        }
    }

    private void onFindNext() {
        if (lastSearchTerm != null) {
            handleFind(lastSearchTerm);
        }
    }

    private void onGoToPosition() {
        String result = (String)JOptionPane.showInputDialog(
                frame,
                "Enter Line[:Column]",
                "GoTo",
                JOptionPane.QUESTION_MESSAGE,
                null,
                null,
                "1:1"
        );
        if (result != null) {
            handleGoTo(result);
        }
    }

    private void addResizeListener() {
        textArea.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                Dimension newSize = e.getComponent().getSize();
                computeSizeOfVisibleArea(newSize);
                uiListener.ifPresent(viewerUiListener -> viewerUiListener.resize(numberOfLinesToDisplay, numberOfColumnsToDisplay));
            }
            @Override
            public void componentMoved(ComponentEvent e) {
                // don't care
            }
        });
    }

    private void computeSizeOfVisibleArea(Dimension newSize) {
        numberOfLinesToDisplay = Math.max(0, (int) Math.floor(newSize.getHeight() / heightPerLine));
        numberOfColumnsToDisplay = Math.max(0,(int) Math.floor((10.0 * newSize.getWidth()) / widthPer10Chars));
    }

    private void prepareInputMapOfTextArea(final JTextArea textArea) {
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), AMK_GO_ONE_LINE_UP);
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), AMK_GO_ONE_LINE_DOWN);
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), AMK_GO_ONE_COLUMN_LEFT);
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), AMK_GO_ONE_COLUMN_RIGHT);

        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), AMK_GO_ONE_PAGE_UP);
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK), AMK_GO_ONE_PAGE_UP);
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), AMK_GO_ONE_PAGE_DOWN);
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK), AMK_GO_ONE_PAGE_DOWN);
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK), AMK_GO_ONE_PAGE_LEFT);
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK), AMK_GO_ONE_PAGE_RIGHT);

        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.CTRL_DOWN_MASK), AMK_LARGE_JUMP_UP);
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.CTRL_DOWN_MASK), AMK_LARGE_JUMP_DOWN);
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.CTRL_DOWN_MASK), AMK_LARGE_JUMP_LEFT);
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.CTRL_DOWN_MASK), AMK_LARGE_JUMP_RIGHT);

        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, InputEvent.CTRL_DOWN_MASK), AMK_GO_TO_FIST_LINE);
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_END, InputEvent.CTRL_DOWN_MASK), AMK_GO_TO_LAST_LINE);
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), AMK_GO_TO_LINE_BEGIN);
        textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_END, 0), AMK_GO_TO_LINE_END);
    }

    private void prepareActionMapOfTextArea(final JTextArea textArea) {
        addToActionMap(textArea, AMK_GO_ONE_LINE_UP, ViewerUiListener::onGoOneLineUp);
        addToActionMap(textArea, AMK_GO_ONE_LINE_DOWN, ViewerUiListener::onGoOneLineDown);
        addToActionMap(textArea, AMK_GO_ONE_COLUMN_LEFT, ViewerUiListener::onGoOneColumnLeft);
        addToActionMap(textArea, AMK_GO_ONE_COLUMN_RIGHT, ViewerUiListener::onGoOneColumnRight);

        addToActionMap(textArea, AMK_GO_ONE_PAGE_UP, ViewerUiListener::onGoOnePageUp);
        addToActionMap(textArea, AMK_GO_ONE_PAGE_DOWN, ViewerUiListener::onGoOnePageDown);
        addToActionMap(textArea, AMK_GO_ONE_PAGE_LEFT, ViewerUiListener::onGoOnePageLeft);
        addToActionMap(textArea, AMK_GO_ONE_PAGE_RIGHT, ViewerUiListener::onGoOnePageRight);

        addToActionMap(textArea, AMK_LARGE_JUMP_UP, ViewerUiListener::onLargeJumpUp);
        addToActionMap(textArea, AMK_LARGE_JUMP_DOWN, ViewerUiListener::onLargeJumpDown);
        addToActionMap(textArea, AMK_LARGE_JUMP_LEFT, ViewerUiListener::onLargeJumpLeft);
        addToActionMap(textArea, AMK_LARGE_JUMP_RIGHT, ViewerUiListener::onLargeJumpRight);

        addToActionMap(textArea, AMK_GO_TO_FIST_LINE, ViewerUiListener::onGoToFirstLine);
        addToActionMap(textArea, AMK_GO_TO_LAST_LINE, ViewerUiListener::onGoToLastLine);
        addToActionMap(textArea, AMK_GO_TO_LINE_BEGIN, ViewerUiListener::onGoToLineBegin);
        addToActionMap(textArea, AMK_GO_TO_LINE_END, ViewerUiListener::onGoToLineEnd);
    }

    private void addToActionMap(JTextArea textArea, String actionMapKey, Consumer<ViewerUiListener> action) {
        textArea.getActionMap().put(actionMapKey, new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uiListener.ifPresent(action);
            }
        });
    }

    private void handleFind(String result) {
        uiListener.ifPresent(viewerUiListener -> viewerUiListener.moveToLocationOfSearchTerm(result));
    }

    private void handleGoTo(String result) {
        if (!uiListener.isPresent()) {
            return;
        }
        if (result.contains(":")) {
            String[] address = result.split(":");
            if (address.length == 2) {
                if (address[0].matches("^\\d+$") && address[1].matches("^\\d+$")) {
                    int line = Integer.parseInt(address[0]) - 1;
                    long column = Long.parseLong(address[1]) - 1;
                    uiListener.get().onGoTo(line, column);

                    return;
                }
            }
        } else if (result.matches("^\\d+$")) {
            int line = Integer.parseInt(result) - 1;
            uiListener.get().onGoTo(line, 0);

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
}
