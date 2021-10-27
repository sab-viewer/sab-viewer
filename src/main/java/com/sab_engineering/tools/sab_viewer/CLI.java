package com.sab_engineering.tools.sab_viewer;

import com.sab_engineering.tools.sab_viewer.textmode.TextModeViewer;

import java.io.PrintWriter;
import java.io.StringWriter;

public class CLI {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("You need to pass file name as first parameter");
            System.exit(-1);
        }
        String fileName = args[0];

        try {
            TextModeViewer.view(fileName);
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
