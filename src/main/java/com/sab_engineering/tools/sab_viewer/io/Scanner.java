package com.sab_engineering.tools.sab_viewer.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.function.Consumer;

public class Scanner {

    public static void scanFile(String fileName, Charset charset, Consumer<LineContent> lineListener, int numberOfInitialLines, int numberOfVisibleCharactersPerLine, Consumer<LineStatistics> statisticsListener) throws IOException {
        try (
            SeekableByteChannel seekableByteChannel = Files.newByteChannel(Paths.get(fileName), StandardOpenOption.READ);
        ){
            long startTimestamp = System.currentTimeMillis();

            CharsetDecoder charsetDecoder = charset.newDecoder();
            charsetDecoder.onUnmappableCharacter(CodingErrorAction.REPLACE);

            ByteBuffer readBuffer = ByteBuffer.allocate(IoConstants.NUMBER_OF_CHARACTERS_PER_BYTE_POSITION);
            CharBuffer decodeBuffer = CharBuffer.allocate(1);

            long positionInBytes = 0;
            int numberOfLinesRead = 0;

            final ArrayList<Long> characterPositionEveryNCharactersInBytes = new ArrayList<>();

            char[] lineBuffer = new char[numberOfVisibleCharactersPerLine];
            long characterNumberInLine = 0;

            char lastCharacter = '\0';

            int bytesRead;
            do {
                bytesRead = seekableByteChannel.read(readBuffer);
                readBuffer.flip();
                if (readBuffer.hasRemaining()) {
                    boolean continueDecoding = true;
                    do {
                        boolean getLastCharacter = false;
                        int readBufferPositionBeforeDecode = readBuffer.position();
                        CoderResult decodeResult = charsetDecoder.decode(readBuffer, decodeBuffer, false);
                        if (decodeResult == CoderResult.UNDERFLOW && bytesRead == -1) {
                            decodeResult = charsetDecoder.decode(readBuffer, decodeBuffer, true);
                            getLastCharacter = true;
                            continueDecoding = false;
                        }
                        if (decodeResult == CoderResult.OVERFLOW || (decodeResult == CoderResult.UNDERFLOW && getLastCharacter)) {
                            if (characterNumberInLine % IoConstants.NUMBER_OF_CHARACTERS_PER_BYTE_POSITION == 0) {
                                characterPositionEveryNCharactersInBytes.add(positionInBytes);
                            }
                            decodeBuffer.flip();
                            char currentCharacter = decodeBuffer.get();
                            decodeBuffer.clear();

                            if (currentCharacter == '\n' || currentCharacter == '\r') {
                                // when windows line ending is detected here, The line was already finished and published at the \r, so we just reset the counts to drop the \n
                                if (lastCharacter != '\r' || currentCharacter != '\n') {
                                    if (numberOfLinesRead < numberOfInitialLines && characterNumberInLine <= numberOfVisibleCharactersPerLine) {
                                        lineListener.accept(new LineContent(new String(lineBuffer, 0, (int) characterNumberInLine)));
                                    }
                                    long[] characterPositionsInBytes = characterPositionEveryNCharactersInBytes.stream().mapToLong(Long::longValue).toArray();
                                    statisticsListener.accept(new LineStatistics(characterPositionsInBytes, positionInBytes - characterPositionsInBytes[0], characterNumberInLine));
                                    numberOfLinesRead++;
                                    if (numberOfLinesRead > 0 && numberOfLinesRead % 25000 == 0) {
                                        printStats(startTimestamp, positionInBytes, numberOfLinesRead, "read");
                                    }
                                }
                                characterPositionEveryNCharactersInBytes.clear();
                                characterNumberInLine = -1;
                            } else if (numberOfLinesRead < numberOfInitialLines) {
                                if (characterNumberInLine < numberOfVisibleCharactersPerLine) {
                                    lineBuffer[(int) characterNumberInLine] = currentCharacter;
                                } else if (characterNumberInLine == numberOfVisibleCharactersPerLine) {
                                    lineListener.accept(new LineContent(new String(lineBuffer, 0, (int) characterNumberInLine)));
                                }
                            }
                            lastCharacter = currentCharacter;
                            characterNumberInLine++;
                            positionInBytes += readBuffer.position() - readBufferPositionBeforeDecode;

                        } else {
                            throw new IllegalStateException("Unexpected decoder result " + decodeResult.toString());
                        }
                        if (readBuffer.limit() - readBuffer.position() < 10 && bytesRead != -1) {
                            readBuffer.compact();
                            continueDecoding = false;
                        }
                    } while (continueDecoding);
                }
            } while (bytesRead != -1 || readBuffer.hasRemaining());

            if (characterPositionEveryNCharactersInBytes.size() > 0) {
                if (numberOfLinesRead < numberOfInitialLines && characterNumberInLine <= numberOfVisibleCharactersPerLine) {
                    lineListener.accept(new LineContent(new String(lineBuffer, 0, (int) characterNumberInLine)));
                }
                long[] characterPositionsInBytes = characterPositionEveryNCharactersInBytes.stream().mapToLong(Long::longValue).toArray();
                statisticsListener.accept(new LineStatistics(characterPositionsInBytes, positionInBytes - characterPositionsInBytes[0], characterNumberInLine));
            }

            printStats(startTimestamp, positionInBytes, numberOfLinesRead, "finished reading");
        }
    }

    private static void printStats(long startTimestamp, long positionInBytes, int numberOfLinesRead, String currentState) {
        long timePassedInMs = System.currentTimeMillis() - startTimestamp;
        double timePassedInSeconds = timePassedInMs / 1000.0;
        double mBytesPerSecond = positionInBytes / (1024 * 1024 * timePassedInSeconds);
        System.out.printf("Scanner " + currentState + " " + numberOfLinesRead + " lines in %.2f seconds. Read speed was %.2f MB/s\n", timePassedInSeconds, mBytesPerSecond);
        System.out.flush();
    }
}
