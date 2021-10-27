package com.sab_engineering.tools.sab_viewer.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class Reader {
    public static List<LineContent> readSpecificLines(String fileName, List<LineStatistics> linesToReadOrderedByStartPositionInFile, int numberOfVisibleCharactersPerLine) {
        try (
                InputStream inputStream = Files.newInputStream(Paths.get(fileName), StandardOpenOption.READ);
                InputStreamReader inputReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        ){
            List<LineContent> resultingLines = new ArrayList<>(linesToReadOrderedByStartPositionInFile.size());
            long lastPosition = 0;
            for (LineStatistics lineStatistics : linesToReadOrderedByStartPositionInFile) {
                long skippedCharacters = inputReader.skip(lineStatistics.getStartPositionInFile() - lastPosition);
                lastPosition += skippedCharacters;
                if (lastPosition != lineStatistics.getStartPositionInFile()) {
                    throw new IllegalStateException("File content changed unexpectedly while reading it");
                }
                int charactersToRead = (int) Math.min(numberOfVisibleCharactersPerLine, lineStatistics.getLength());
                char[] lineBuffer = new char[charactersToRead];
                int charactersRead = inputReader.read(lineBuffer);
                lastPosition += charactersRead;
                if (charactersRead != charactersToRead) {
                    throw new IllegalStateException("File content changed unexpectedly while reading it");
                }
                resultingLines.add(new LineContent(new String(lineBuffer)));
            }
            return resultingLines;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read '" + fileName + "'", e);
        }
    }
}
