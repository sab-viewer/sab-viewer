package com.sab_engineering.tools.sab_viewer.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
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
            CharsetEncoder charsetEncoder = charset.newEncoder();
            CharBuffer encodeInputBuffer = CharBuffer.allocate(1);
            ByteBuffer encodedChar = ByteBuffer.allocate(Math.round(charsetEncoder.maxBytesPerChar() + 0.5f));
            int[] characterSizesInBytes = new int[Character.MAX_VALUE + 1];

            long positionInBytes = 0;

            final ArrayList<Long> characterPositionEveryNCharactersInBytes = new ArrayList<>();

            char[] lineBuffer = new char[numberOfVisibleCharactersPerLine];
            long characterNumberInLine = 0;

            char lastCharacter = '\0';
            int currentCharactersIntValue;
            do {
                currentCharactersIntValue = inputReader.read();
                if (currentCharactersIntValue > 0) {
                    if (characterNumberInLine % IoConstants.NUMBER_OF_CHARACTERS_PER_BYTE_POSITION == 0) {
                        characterPositionEveryNCharactersInBytes.add(positionInBytes);
                    }
                    char currentCharacter = (char) currentCharactersIntValue;

                    if (currentCharacter == '\n' || currentCharacter == '\r') {
                        // when windows line ending is detected here, The line was already finished and published at the \r, so we just reset the counts to drop the \n
                        if (lastCharacter != '\r' || currentCharacter != '\n') {
                            if (characterNumberInLine <= numberOfVisibleCharactersPerLine) {
                                lineListener.accept(new LineContent(new String(lineBuffer, 0, (int) characterNumberInLine)));
                            }
                            long[] characterPositionsInBytes = characterPositionEveryNCharactersInBytes.stream().mapToLong(Long::longValue).toArray();
                            statisticsListener.accept(new LineStatistics(characterPositionsInBytes, positionInBytes - characterPositionsInBytes[0], characterNumberInLine));
                        }
                        characterPositionEveryNCharactersInBytes.clear();
                        characterNumberInLine = -1;
                    } else if (characterNumberInLine < numberOfVisibleCharactersPerLine) {
                        lineBuffer[(int) characterNumberInLine] = currentCharacter;
                    } else if (characterNumberInLine == numberOfVisibleCharactersPerLine) {
                        lineListener.accept(new LineContent(new String(lineBuffer, 0, (int) characterNumberInLine)));
                    }
                    lastCharacter = currentCharacter;
                    characterNumberInLine++;

                    // determine byte size of the character
                    if (characterSizesInBytes[currentCharactersIntValue] != 0) {
                        positionInBytes += characterSizesInBytes[currentCharactersIntValue];
                    } else {
                        encodedChar.clear();
                        encodeInputBuffer.clear();

                        encodeInputBuffer.put(0, currentCharacter);

                        charsetEncoder.reset();
                        CoderResult encodingResult = charsetEncoder.encode(encodeInputBuffer, encodedChar, true);
                        if (!encodingResult.isUnderflow()) {
                            throw new IllegalStateException("Unable to determine byte size of character " + currentCharacter + " in " + charset.displayName());
                        }
                        charsetEncoder.flush(encodedChar);
                        if (!encodingResult.isUnderflow()) {
                            throw new IllegalStateException("Unable to determine byte size of character " + currentCharacter + " in " + charset.displayName());
                        }

                        characterSizesInBytes[currentCharactersIntValue] = encodedChar.position();
                        positionInBytes += encodedChar.position();
                    }
                } else /* EOF */ {
                    if (characterPositionEveryNCharactersInBytes.size() > 0) {
                        if (characterNumberInLine <= numberOfVisibleCharactersPerLine) {
                            lineListener.accept(new LineContent(new String(lineBuffer, 0, (int) characterNumberInLine)));
                        }
                        long[] characterPositionsInBytes = characterPositionEveryNCharactersInBytes.stream().mapToLong(Long::longValue).toArray();
                        statisticsListener.accept(new LineStatistics(characterPositionsInBytes, positionInBytes - characterPositionsInBytes[0], characterNumberInLine));
                    }
                }
            } while (currentCharactersIntValue != -1);
        }
    }
}
