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

            final ByteBuffer readBuffer = ByteBuffer.allocate(IoConstants.NUMBER_OF_BYTES_TO_READ_IN_SCANNER);
            final CharBuffer opportunisticDecodeBuffer = CharBuffer.allocate(IoConstants.NUMBER_OF_BYTES_TO_DECODE_OPPORTUNISTICALLY);
            final CharBuffer fallbackDecodeBuffer = CharBuffer.allocate(1);

            long positionInBytes = 0;
            long positionInBytesToStartOpportunisticEncoding = 0;
            int numberOfLinesRead = 0;
            int numberOfLinesPublished = 0;

            final ArrayList<Long> characterPositionEveryNCharactersInBytes = new ArrayList<>();
            long decodeFallbackCharacterPositionsSize;

            char[] lineBuffer = new char[numberOfVisibleCharactersPerLine];
            long characterNumberInLine = 0;
            long decodeFallbackCharacterNumberInLine;

            char lastCharacter = '\0';
            char decodeFallbackLastCharacter;

            boolean byteChannelIsAtEOF;
            do {
                int bytesRead = seekableByteChannel.read(readBuffer);
                byteChannelIsAtEOF = bytesRead == -1;
                readBuffer.flip();
                if (readBuffer.hasRemaining()) {
                    do {
                        decodeFallbackCharacterPositionsSize = characterPositionEveryNCharactersInBytes.size();
                        decodeFallbackCharacterNumberInLine = characterNumberInLine;
                        decodeFallbackLastCharacter = lastCharacter;

                        int readBufferPositionBeforeDecode = readBuffer.position();

                        CharBuffer decodeBuffer;
                        if (positionInBytes < positionInBytesToStartOpportunisticEncoding) {
                            decodeBuffer = fallbackDecodeBuffer;
                        } else {
                            decodeBuffer = opportunisticDecodeBuffer;
                        }
                        decodeBuffer.clear();
                        int remainingCharactersBeforeMarker = IoConstants.NUMBER_OF_CHARACTERS_PER_BYTE_POSITION - (int) (characterNumberInLine % IoConstants.NUMBER_OF_CHARACTERS_PER_BYTE_POSITION);
                        decodeBuffer.limit(Math.min(decodeBuffer.capacity(), remainingCharactersBeforeMarker));

                        CoderResult decodeResult = charsetDecoder.decode(readBuffer, decodeBuffer, byteChannelIsAtEOF);
                        if (decodeResult == CoderResult.OVERFLOW || (decodeResult == CoderResult.UNDERFLOW && byteChannelIsAtEOF)) {
                            decodeBuffer.flip();

                            boolean containsMultiByteCharacters = decodeBuffer.limit() < readBuffer.position() - readBufferPositionBeforeDecode;

                            int decodedCharacters = 0;
                            while (decodeBuffer.hasRemaining()) {
                                if (characterNumberInLine % IoConstants.NUMBER_OF_CHARACTERS_PER_BYTE_POSITION == 0) {
                                    characterPositionEveryNCharactersInBytes.add(positionInBytes + decodedCharacters);
                                }
                                char currentCharacter = decodeBuffer.get();

                                if (currentCharacter == '\n' || currentCharacter == '\r') {
                                    if (containsMultiByteCharacters && decodeBuffer == opportunisticDecodeBuffer) {
                                        // mark next position to try again
                                        positionInBytesToStartOpportunisticEncoding = (positionInBytes - decodedCharacters) + readBuffer.position() - readBufferPositionBeforeDecode;

                                        // perform reset
                                        readBuffer.position(readBufferPositionBeforeDecode);
                                        while (characterPositionEveryNCharactersInBytes.size() > decodeFallbackCharacterPositionsSize) {
                                            characterPositionEveryNCharactersInBytes.remove(characterPositionEveryNCharactersInBytes.size() - 1);
                                        }
                                        characterNumberInLine = decodeFallbackCharacterNumberInLine;
                                        lastCharacter = decodeFallbackLastCharacter;
                                        break;
                                    }
                                    long lineEndPositionInBytes = positionInBytes + decodedCharacters;

                                    // when windows line ending is detected here, The line was already finished and published at the \r, so we just reset the counts to drop the \n
                                    if (lastCharacter != '\r' || currentCharacter != '\n') {
                                        if (numberOfLinesRead < numberOfInitialLines && characterNumberInLine <= numberOfVisibleCharactersPerLine) {
                                            lineListener.accept(new LineContent(new String(lineBuffer, 0, (int) characterNumberInLine)));
                                            numberOfLinesPublished++;
                                        }
                                        long[] characterPositionsInBytes = characterPositionEveryNCharactersInBytes.stream().mapToLong(Long::longValue).toArray();
                                        statisticsListener.accept(new LineStatistics(characterPositionsInBytes, lineEndPositionInBytes - characterPositionsInBytes[0], characterNumberInLine));
                                        numberOfLinesRead++;
                                        if (numberOfLinesRead > 0 && numberOfLinesRead % 25000 == 0) {
                                            printStats(startTimestamp, lineEndPositionInBytes, numberOfLinesRead, "read");
                                        }
                                    }
                                    characterPositionEveryNCharactersInBytes.clear();
                                    characterNumberInLine = -1;
                                } else if (numberOfLinesRead < numberOfInitialLines && numberOfLinesPublished == numberOfLinesRead) {
                                    if (characterNumberInLine < numberOfVisibleCharactersPerLine) {
                                        lineBuffer[(int) characterNumberInLine] = currentCharacter;
                                    } else if (characterNumberInLine == numberOfVisibleCharactersPerLine) {
                                        lineListener.accept(new LineContent(new String(lineBuffer, 0, (int) characterNumberInLine)));
                                        numberOfLinesPublished++;
                                    }
                                }
                                lastCharacter = currentCharacter;
                                characterNumberInLine++;
                                decodedCharacters++;
                            }
                            positionInBytes += (readBuffer.position() - readBufferPositionBeforeDecode);

                        } else {
                            throw new IllegalStateException("Unexpected decoder result " + decodeResult.toString());
                        }
                    } while (readBuffer.hasRemaining() && (byteChannelIsAtEOF || bufferHasEnoughBytesToNotUnderflowDuringDecode(readBuffer)));
                    readBuffer.compact();
                }
            } while (!byteChannelIsAtEOF || readBuffer.hasRemaining());

            if (characterPositionEveryNCharactersInBytes.size() > 0) {
                if (numberOfLinesRead < numberOfInitialLines && characterNumberInLine <= numberOfVisibleCharactersPerLine) {
                    lineListener.accept(new LineContent(new String(lineBuffer, 0, (int) characterNumberInLine)));
                }
                long[] characterPositionsInBytes = characterPositionEveryNCharactersInBytes.stream().mapToLong(Long::longValue).toArray();
                statisticsListener.accept(new LineStatistics(characterPositionsInBytes, positionInBytes - characterPositionsInBytes[0], characterNumberInLine));
                numberOfLinesRead++;
            }

            printStats(startTimestamp, positionInBytes, numberOfLinesRead, "finished reading");
        }
    }

    private static boolean bufferHasEnoughBytesToNotUnderflowDuringDecode(ByteBuffer readBuffer) {
        return (readBuffer.limit() - readBuffer.position()) > (IoConstants.NUMBER_OF_BYTES_TO_DECODE_OPPORTUNISTICALLY * 3);
    }

    private static void printStats(long startTimestamp, long positionInBytes, int numberOfLinesRead, String currentState) {
        long timePassedInMs = System.currentTimeMillis() - startTimestamp;
        double timePassedInSeconds = timePassedInMs / 1000.0;
        double mBytesPerSecond = positionInBytes / (1024 * 1024 * timePassedInSeconds);
        System.out.printf("Scanner " + currentState + " " + numberOfLinesRead + " lines in %.2f seconds. Read speed was %.2f MB/s\n", timePassedInSeconds, mBytesPerSecond);
        System.out.flush();
    }
}
