package com.sab_engineering.tools.sab_viewer.gui;

import com.sab_engineering.tools.sab_viewer.io.LineContent;

import java.util.Collection;

public interface ViewerUi {

    void setLines(final Collection<LineContent> lines);

    /** Just forwards arguments to @{code JOptionPane.showMessageDialog()}, wile using the gui's frame as parent. */
    void showMessageDialog(Object message, String title, int messageType); // probably we can make it is some more elegant way

}
