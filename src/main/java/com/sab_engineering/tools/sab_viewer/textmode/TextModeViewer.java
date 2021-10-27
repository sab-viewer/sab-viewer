package com.sab_engineering.tools.sab_viewer.textmode;

import com.sab_engineering.tools.sab_viewer.io.LineContent;
import com.sab_engineering.tools.sab_viewer.io.LineStatistics;
import com.sab_engineering.tools.sab_viewer.io.Reader;
import com.sab_engineering.tools.sab_viewer.io.Scanner;

import java.util.List;
import java.util.Vector;

// this just some simple demo for the scanner to show and test the components a real GUI would need
public class TextModeViewer {
    private final static int ROWS = 40;
    private final static int COLUMNS = 80;

    private static final List<LineContent> LINE_CONTENTS = new Vector<>(); // synchronized vector is useful after all
    private static final List<LineStatistics> LINE_STATISTICS = new Vector<>();

    @SuppressWarnings("BusyWait")
    public static void view(String fileName) {
        Thread scannerThread = new Thread(
                () -> Scanner.scanFile(fileName, TextModeViewer::offer, COLUMNS, LINE_STATISTICS::add),
                "Scanner"
        );
        scannerThread.start();

        int printedSize = 0;
        while (scannerThread.isAlive() || printedSize < LINE_CONTENTS.size()) {
            while (printedSize < LINE_CONTENTS.size()) {
                LineContent newLine = LINE_CONTENTS.get(printedSize);
                printedSize += 1;

                System.out.println(newLine.getVisibleContent());
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                scannerThread.interrupt();
            }
        }

        if (LINE_STATISTICS.size() > ROWS) {
            List<LineContent> lineContents = Reader.readSpecificLines(fileName, LINE_STATISTICS.subList(LINE_STATISTICS.size() / 2 - 5, LINE_STATISTICS.size() / 2 + 5), 10, COLUMNS);
            System.out.println("=============Middle lines, starting from column 10 =============");
            for (LineContent lineContent : lineContents) {
                System.out.println(lineContent.getVisibleContent());
            }

            lineContents = Reader.readSpecificLines(fileName, LINE_STATISTICS.subList(LINE_STATISTICS.size() - 10, LINE_STATISTICS.size()), 0, COLUMNS);
            System.out.println("=============Last lines=============");
            for (LineContent lineContent : lineContents) {
                System.out.println(lineContent.getVisibleContent());
            }
        }

        System.out.println("File " + fileName + " contained " + LINE_STATISTICS.size() + " lines with " + LINE_STATISTICS.stream().mapToLong(LineStatistics::getLength).sum() + " characters (excluding new line characters)");
    }

    private static void offer(LineContent lineContent) {
        if (LINE_CONTENTS.size() < ROWS) {
            LINE_CONTENTS.add(lineContent);
        }
    }
}
