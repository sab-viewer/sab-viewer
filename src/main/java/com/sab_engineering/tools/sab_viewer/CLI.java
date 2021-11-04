package com.sab_engineering.tools.sab_viewer;

import com.sab_engineering.tools.sab_viewer.gui.GuiSwing;
import com.sab_engineering.tools.sab_viewer.textmode.TextModeViewer;

import javax.swing.SwingUtilities;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

public class CLI {
    public static void main(String[] args) {
        if (args.length != 1 && args.length != 2) {
            System.err.println("You need to pass file name as first parameter");
            System.err.println("Or '--textMode' as first and file name as second parameter");
            System.exit(-1);
        }

        boolean textMode = false;
        String fileName;
        if ("--textMode".equals(args[0])) {
            textMode = true;
            fileName = args[1];
        } else {
            fileName = args[0];
        }

        try {
            if (textMode) {
                TextModeViewer.view(fileName);
            } else {
                SwingUtilities.invokeAndWait(() -> new GuiSwing(Optional.of(fileName)));
            }
        } catch (Exception e) {
            String message = e.getMessage();
            System.err.println("Error: " + message);
            System.err.println("====== technical error details ======");
            StringWriter errors = new StringWriter();
            e.printStackTrace(new PrintWriter(errors));
            String output;
            if (message != null && message.length() > 100) {
                output = errors.toString().replace(message, message.substring(0, 100).replace('\n', ' ') + " ...");
            } else {
                output = errors.toString();
            }
            System.err.println(output);
            System.exit(-2);
        }
    }
}
