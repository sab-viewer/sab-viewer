package com.sab_engineering.tools.sab_viewer.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.function.Consumer;

public class Scanner {
    private static final int BUFFER_SIZE = 4096;

    public static void scanFile(String fileName, Consumer<LineContent> lineListener, int numberOfVisibleCharactersPerLine, Consumer<LineStatistics> statisticsListener) {
        try (
                InputStream inputStream = Files.newInputStream(Paths.get(fileName), StandardOpenOption.READ);
                InputStreamReader inputReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        ){
            char[] buffer = new char[BUFFER_SIZE];
            char[] lineBuffer = new char[numberOfVisibleCharactersPerLine];
            long position = 0;
            long positionOfLastNewLineStart = 0;
            int positionInLine = 0;
            int charsRead;
            do {
                charsRead = inputReader.read(buffer);
                for (int positionInBuffer = 0; positionInBuffer < charsRead; positionInBuffer++, positionInLine++, position++) {
                    if (buffer[positionInBuffer] == '\n') {
                        if (positionInLine <= numberOfVisibleCharactersPerLine) {
                            lineListener.accept(new LineContent(new String(lineBuffer, 0, positionInLine)));
                        }
                        statisticsListener.accept(new LineStatistics(positionOfLastNewLineStart, positionInLine));
                        positionOfLastNewLineStart = position + 1;
                        lineBuffer = new char[numberOfVisibleCharactersPerLine];
                        positionInLine = -1;
                    } else if (positionInLine < numberOfVisibleCharactersPerLine) {
                        lineBuffer[positionInLine] = buffer[positionInBuffer];
                    } else if (positionInLine == numberOfVisibleCharactersPerLine) {
                        lineListener.accept(new LineContent(new String(lineBuffer, 0, positionInLine)));
                    }
                }
            } while (charsRead != -1);

            if (positionInLine > 0) {
                lineListener.accept(new LineContent(new String(lineBuffer, 0, positionInLine)));
                statisticsListener.accept(new LineStatistics(positionOfLastNewLineStart, positionInLine));
            }

        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read '" + fileName + "'", e);
        }
    }
}
