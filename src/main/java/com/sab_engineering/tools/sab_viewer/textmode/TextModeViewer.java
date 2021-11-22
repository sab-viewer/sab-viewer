package com.sab_engineering.tools.sab_viewer.textmode;

import com.sab_engineering.tools.sab_viewer.controller.ScannerState;
import com.sab_engineering.tools.sab_viewer.controller.ViewerContent;
import com.sab_engineering.tools.sab_viewer.controller.ViewerController;
import com.sab_engineering.tools.sab_viewer.io.LinePreview;

import java.nio.charset.StandardCharsets;
import java.util.List;

// this just some simple demo for the Controller
public class TextModeViewer {
    private final static int ROWS = 40;
    private final static int COLUMNS = 80;

    private static ViewerController viewerController;
    private static int displayedLines = 0;

    public static void view(String fileName) {
        viewerController = new ViewerController(
                fileName,
                StandardCharsets.UTF_8,
                ROWS,
                COLUMNS,
                TextModeViewer::displayViewerContent,
                TextModeViewer::handleScannerState,
                messageInfo -> System.err.println(messageInfo.getMessage())
        );
    }

    private static void displayViewerContent(ViewerContent viewerContent) {
        List<LinePreview> lines = viewerContent.getLines();
        for (int i = displayedLines; i < lines.size(); i++) {
            System.out.println(lines.get(i).getVisibleContent());
            displayedLines++;
        }
    }

    private static void handleScannerState(ScannerState scannerState) {
        if (scannerState.isFinished()) {
            System.out.println("Finished Scanning " + scannerState.getLinesScanned() + " lines (" + scannerState.getBytesScanned() + " bytes) while consuming " + scannerState.getUsedMemory() / (1024 * 1024) + " MB of RAM");
            viewerController.interruptBackgroundThreads();
        }
    }

}
