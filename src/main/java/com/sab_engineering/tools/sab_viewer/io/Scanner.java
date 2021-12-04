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
    private final String fileName;
    private final CharsetDecoder charsetDecoder;

    private final Consumer<LinePreview> linePreviewListener;
    private final int numberOfPreviewLines;
    private final int numberOfVisibleCharactersPerLine;
    private final Consumer<LinePositionBatch> statisticsListener;
    private final Consumer<MutableLinePositionBatch> statisticsPreviewListener;

    int numberOfMemoryRetries;

    private final ByteBuffer readBuffer;
    private final CharBuffer opportunisticDecodeBuffer;
    private final CharBuffer fallbackDecodeBuffer;

    private int numberOfLinesRead;

    private MutableLinePositionBatch mutableLinePositionBatch;

    private final char[] linePreviewBuffer;
    private int numberOfLinePreviewsPublished;

    public Scanner(String fileName, Charset charset, Consumer<LinePreview> linePreviewListener, int numberOfPreviewLines, int numberCharactersForLinePreview, Consumer<LinePositionBatch> statisticsListener, Consumer<MutableLinePositionBatch> statisticsPreviewListener) {
        this.fileName = fileName;
        this.charsetDecoder = charset.newDecoder();
        this.charsetDecoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
        this.charsetDecoder.onMalformedInput(CodingErrorAction.REPLACE);
        this.linePreviewListener = linePreviewListener;
        this.numberOfPreviewLines = numberOfPreviewLines;
        this.numberOfVisibleCharactersPerLine = numberCharactersForLinePreview;
        this.statisticsListener = statisticsListener;
        this.statisticsPreviewListener = statisticsPreviewListener;

        this.numberOfMemoryRetries = 10;

        this.readBuffer = ByteBuffer.allocate(IoConstants.NUMBER_OF_BYTES_TO_READ_IN_SCANNER);
        this.opportunisticDecodeBuffer = CharBuffer.allocate(IoConstants.NUMBER_OF_BYTES_TO_DECODE_OPPORTUNISTICALLY);
        this.fallbackDecodeBuffer = CharBuffer.allocate(1);

        this.numberOfLinesRead = 0;

        initStatisticsBatch();

        this.linePreviewBuffer = new char[numberCharactersForLinePreview];
        this.numberOfLinePreviewsPublished = 0;
    }

    public boolean scanFile() throws IOException, InterruptedException {
        try (SeekableByteChannel seekableByteChannel = Files.newByteChannel(Paths.get(fileName), StandardOpenOption.READ)) {
            long positionInBytes = 0;
            long positionInBytesToStartOpportunisticEncoding = 0;

            final ArrayList<Long> characterPositionEveryNCharactersInBytes = new ArrayList<>();
            long decodeFallbackCharacterPositionsSize;

            long charactersInCurrentLine = 0;
            long decodeFallbackCharacterInCurrentLine;

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
                        decodeFallbackCharacterInCurrentLine = charactersInCurrentLine;
                        decodeFallbackLastCharacter = lastCharacter;

                        int readBufferPositionBeforeDecode = readBuffer.position();

                        CharBuffer decodeBuffer;
                        if (positionInBytes < positionInBytesToStartOpportunisticEncoding) {
                            decodeBuffer = fallbackDecodeBuffer;
                        } else {
                            decodeBuffer = opportunisticDecodeBuffer;
                        }
                        decodeBuffer.clear();
                        int remainingCharactersBeforeMarker = IoConstants.NUMBER_OF_CHARACTERS_PER_BYTE_POSITION - (int) (charactersInCurrentLine % IoConstants.NUMBER_OF_CHARACTERS_PER_BYTE_POSITION);
                        decodeBuffer.limit(Math.min(decodeBuffer.capacity(), remainingCharactersBeforeMarker));

                        CoderResult decodeResult = charsetDecoder.decode(readBuffer, decodeBuffer, byteChannelIsAtEOF);
                        if (decodeResult == CoderResult.OVERFLOW || (decodeResult == CoderResult.UNDERFLOW && byteChannelIsAtEOF)) {
                            decodeBuffer.flip();

                            boolean containsMultiByteCharacters = decodeBuffer.limit() < readBuffer.position() - readBufferPositionBeforeDecode;

                            int decodedCharacters = 0;
                            while (decodeBuffer.hasRemaining()) {
                                if (charactersInCurrentLine % IoConstants.NUMBER_OF_CHARACTERS_PER_BYTE_POSITION == 0) {
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
                                        charactersInCurrentLine = decodeFallbackCharacterInCurrentLine;
                                        lastCharacter = decodeFallbackLastCharacter;
                                        break;
                                    }
                                    long lineEndPositionInBytes = positionInBytes + decodedCharacters;

                                    // when windows line ending is detected here, The line was already finished and published at the \r, so we just reset the counts to drop the \n
                                    if (lastCharacter != '\r' || currentCharacter != '\n') {
                                        if (numberOfLinesRead < numberOfPreviewLines && charactersInCurrentLine <= numberOfVisibleCharactersPerLine) {
                                            publishLinePreview((int) charactersInCurrentLine);
                                        }

                                        Runtime runtime = Runtime.getRuntime();
                                        if (runtime.totalMemory() * 2 > runtime.maxMemory() && runtime.freeMemory() < (250 * 1024 * 1024) && runtime.freeMemory() * 8 < runtime.totalMemory()) {
                                            if (numberOfMemoryRetries > 0) {
                                                numberOfMemoryRetries -= 1;

                                                runtime.gc();

                                                //noinspection BusyWait
                                                Thread.sleep(250); // wait a bit to give gc some time to run, then publish final statistics
                                            } else {
                                                return true;
                                            }
                                        }

                                        finishLine(characterPositionEveryNCharactersInBytes, lineEndPositionInBytes, charactersInCurrentLine);
                                    }
                                    characterPositionEveryNCharactersInBytes.clear();
                                    charactersInCurrentLine = -1;
                                } else if (numberOfLinesRead < numberOfPreviewLines && numberOfLinePreviewsPublished == numberOfLinesRead) {
                                    if (charactersInCurrentLine < numberOfVisibleCharactersPerLine) {
                                        linePreviewBuffer[(int) charactersInCurrentLine] = currentCharacter;
                                    } else if (charactersInCurrentLine == numberOfVisibleCharactersPerLine) {
                                        publishLinePreview((int) charactersInCurrentLine);
                                    }
                                }
                                lastCharacter = currentCharacter;
                                charactersInCurrentLine++;
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
                if (numberOfLinesRead < numberOfPreviewLines && charactersInCurrentLine <= numberOfVisibleCharactersPerLine) {
                    publishLinePreview((int) charactersInCurrentLine);
                }
                finishLine(characterPositionEveryNCharactersInBytes, positionInBytes, charactersInCurrentLine);
            }

            publishStatistics();
        }

        return false;
    }

    private void finishLine(final ArrayList<Long> characterPositionEveryNCharactersInBytes, long endPositionInBytes, long lengthInCharacters) {
        final long[] characterPositionsInBytes = characterPositionEveryNCharactersInBytes.stream().mapToLong(Long::longValue).toArray();

        int numberOfContainedLines = this.mutableLinePositionBatch.getNumberOfContainedLines();
        this.mutableLinePositionBatch.setCharacterPositionsInBytes(numberOfContainedLines, characterPositionsInBytes);
        this.mutableLinePositionBatch.setLengthInBytes(numberOfContainedLines, endPositionInBytes - characterPositionsInBytes[0]);
        this.mutableLinePositionBatch.setLengthInCharacters(numberOfContainedLines, lengthInCharacters);
        this.mutableLinePositionBatch.setNumberOfContainedLines(numberOfContainedLines + 1);

        if (mutableLinePositionBatch.getNumberOfContainedLines() == IoConstants.NUMBER_OF_LINES_PER_BATCH) {
            publishStatistics();
            initStatisticsBatch();
        } else if (numberOfLinesRead < IoConstants.NUMBER_OF_LINES_TO_PREVIEW_BATCH){
            publishStatisticsPreview();
        }
        numberOfLinesRead++;
    }

    private void publishStatisticsPreview() {
        if (this.mutableLinePositionBatch.getNumberOfContainedLines() > 0) {
            statisticsPreviewListener.accept(this.mutableLinePositionBatch);
        }
    }

    private void publishStatistics() {
        if (this.mutableLinePositionBatch.getNumberOfContainedLines() > 0) {
            statisticsListener.accept(new LinePositionBatch(this.mutableLinePositionBatch));
        }
    }

    private void publishLinePreview(int charactersInLinePreview) {
        linePreviewListener.accept(new LinePreview(new String(linePreviewBuffer, 0, charactersInLinePreview)));
        numberOfLinePreviewsPublished++;
    }

    private boolean bufferHasEnoughBytesToNotUnderflowDuringDecode(ByteBuffer readBuffer) {
        return (readBuffer.limit() - readBuffer.position()) > (IoConstants.NUMBER_OF_BYTES_TO_DECODE_OPPORTUNISTICALLY * 3);
    }

    private void initStatisticsBatch() {
        long[][] statisticsBatch_characterPositionsInBytes = new long[IoConstants.NUMBER_OF_LINES_PER_BATCH][];
        long[] statisticsBatch_lengthInBytes = new long[IoConstants.NUMBER_OF_LINES_PER_BATCH];
        long[] statisticsBatch_lengthInCharacters = new long[IoConstants.NUMBER_OF_LINES_PER_BATCH];
        this.mutableLinePositionBatch = new MutableLinePositionBatch(statisticsBatch_characterPositionsInBytes, statisticsBatch_lengthInBytes, statisticsBatch_lengthInCharacters, 0);
    }
}
