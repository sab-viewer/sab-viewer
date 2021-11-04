package com.sab_engineering.tools.sab_viewer.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.function.Consumer;

public class Scanner {

    public static void scanFile(String fileName, Charset charset, Consumer<LineContent> lineListener, int numberOfVisibleCharactersPerLine, Consumer<LineStatistics> statisticsListener) throws IOException {
        try (
                InputStream inputStream = Files.newInputStream(Paths.get(fileName), StandardOpenOption.READ);
                InputStreamReader inputReader = new InputStreamReader(inputStream, charset);
        ){
            char[] lineBuffer = new char[numberOfVisibleCharactersPerLine];
            long position = 0;
            final ArrayList<Long> characterPositionEveryNCharactersInBytes = new ArrayList<>();
            long positionInLine = 0;
            char lastCharacter = '\0';
            int currentCharactersIntValue;
            do {
                currentCharactersIntValue = inputReader.read();
                if (currentCharactersIntValue > 0) {
                    if (positionInLine % IoConstants.NUMBER_OF_CHARACTERS_PER_BYTE_POSITION == 0) {
                        characterPositionEveryNCharactersInBytes.add(position);
                    }
                    char currentCharacter = (char) currentCharactersIntValue;
                    if (currentCharacter == '\n' || currentCharacter == '\r') {
                        // when windows line ending is detected here, The line was already finished and published at the \r, so we just reset the counts to drop the \n
                        if (lastCharacter != '\r' || currentCharacter != '\n') {
                            if (positionInLine <= numberOfVisibleCharactersPerLine) {
                                lineListener.accept(new LineContent(new String(lineBuffer, 0, (int) positionInLine)));
                            }
                            statisticsListener.accept(new LineStatistics(characterPositionEveryNCharactersInBytes.stream().mapToLong(Long::longValue).toArray(), positionInLine, positionInLine));
                        }
                        characterPositionEveryNCharactersInBytes.clear();
                        positionInLine = -1;
                    } else if (positionInLine < numberOfVisibleCharactersPerLine) {
                        lineBuffer[(int) positionInLine] = currentCharacter;
                    } else if (positionInLine == numberOfVisibleCharactersPerLine) {
                        lineListener.accept(new LineContent(new String(lineBuffer, 0, (int) positionInLine)));
                    }
                    lastCharacter = currentCharacter;
                    positionInLine++;
                    position++;
                } else /* EOF */ {
                    if (positionInLine <= numberOfVisibleCharactersPerLine) {
                        lineListener.accept(new LineContent(new String(lineBuffer, 0, (int) positionInLine)));
                    }
                    statisticsListener.accept(new LineStatistics(characterPositionEveryNCharactersInBytes.stream().mapToLong(Long::longValue).toArray(), positionInLine, positionInLine));
                }
            } while (currentCharactersIntValue != -1);
        }
    }
}
