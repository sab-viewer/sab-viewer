package com.sab_engineering.tools.sab_viewer.gui;

import com.sab_engineering.tools.sab_viewer.io.LineContent;

import javax.swing.Action;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.Optional;

public class GuiSwing implements ViewerUi {

    private final ViewerUiListener uiListener;

    private JFrame frame;
    private JTextArea textArea;

    public GuiSwing(final ViewerUiListener uiListener) {
        this.uiListener = uiListener;
        this.uiListener.setUi(this); // I really don't like this solution, but I cannot come up with something better. We really need references in both directions.
        prepareGui();
    }

    public void start(final Optional<String> maybeFilePath) {
        frame.setVisible(true);
        maybeFilePath.ifPresent(uiListener::onOpenFile);
    }

    @Override
    public void setLines(final Collection<LineContent> lines) {
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
                uiListener.onGoOneLineUp();
            }
        });
        textArea.getActionMap().put("upPage", new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uiListener.onGoOnePageUp();
            }
        });
        textArea.getActionMap().put("down", new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uiListener.onGoOneLineDown();
            }
        });
        textArea.getActionMap().put("downPage", new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uiListener.onGoOnePageDown();
            }
        });
        textArea.getActionMap().put("left", new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uiListener.onGoOneColumnLeft();
            }
        });
        textArea.getActionMap().put("right", new ActionStub() {
            @Override
            public void actionPerformed(ActionEvent e) {
                uiListener.onGoOneColumnRight();
            }
        });

        frame.getContentPane().add(BorderLayout.NORTH, menuBar);
        frame.getContentPane().add(BorderLayout.CENTER, textArea);
    }

    @Override
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
}
