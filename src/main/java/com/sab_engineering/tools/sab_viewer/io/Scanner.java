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

    private final Consumer<LinePositionBatch> positionsListener;
    private final Consumer<MutableLinePositionBatch> positionsPreviewListener;

    int numberOfMemoryRetries;

    private final ByteBuffer readBuffer;
    private final CharBuffer opportunisticDecodeBuffer;
    private final CharBuffer fallbackDecodeBuffer;

    private int numberOfLinesRead;

    private MutableLinePositionBatch mutableLinePositionBatch;

    public Scanner(String fileName, Charset charset, Consumer<LinePositionBatch> positionsListener, Consumer<MutableLinePositionBatch> positionsPreviewListener) {
        this.fileName = fileName;
        this.charsetDecoder = charset.newDecoder();
        this.charsetDecoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
        this.charsetDecoder.onMalformedInput(CodingErrorAction.REPLACE);
        this.positionsListener = positionsListener;
        this.positionsPreviewListener = positionsPreviewListener;

        this.numberOfMemoryRetries = 10;

        this.readBuffer = ByteBuffer.allocate(IoConstants.NUMBER_OF_BYTES_TO_READ_IN_SCANNER);
        this.opportunisticDecodeBuffer = CharBuffer.allocate(IoConstants.NUMBER_OF_BYTES_TO_DECODE_OPPORTUNISTICALLY);
        this.fallbackDecodeBuffer = CharBuffer.allocate(1);

        this.numberOfLinesRead = 0;

        initPositionsBatch();
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
                                    if (charactersInCurrentLine > 0) {
                                        publishLinePositionPreview(characterPositionEveryNCharactersInBytes, positionInBytes, charactersInCurrentLine);
                                    }
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
                                        Runtime runtime = Runtime.getRuntime();
                                        if (runtime.totalMemory() * 2 > runtime.maxMemory() && runtime.freeMemory() < (250 * 1024 * 1024) && runtime.freeMemory() * 8 < runtime.totalMemory()) {
                                            if (numberOfMemoryRetries > 0) {
                                                numberOfMemoryRetries -= 1;

                                                runtime.gc();

                                                //noinspection BusyWait
                                                Thread.sleep(250); // wait a bit to give gc some time to run, then publish final line positions
                                            } else {
                                                return true;
                                            }
                                        }

                                        finishLine(characterPositionEveryNCharactersInBytes, lineEndPositionInBytes, charactersInCurrentLine);
                                    }
                                    characterPositionEveryNCharactersInBytes.clear();
                                    charactersInCurrentLine = -1;
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
                finishLine(characterPositionEveryNCharactersInBytes, positionInBytes, charactersInCurrentLine);
            }

            publishFinishedPositionBatch();
        }

        return false;
    }

    private void publishLinePositionPreview(final ArrayList<Long> currentCharacterPositionEveryNCharactersInBytes, long currentPositionInBytes, long currentLengthInCharacters) {
        final long[] characterPositionsInBytes = currentCharacterPositionEveryNCharactersInBytes.stream().mapToLong(Long::longValue).toArray();

        int lineIndex = numberOfLinesRead % IoConstants.NUMBER_OF_LINES_PER_BATCH;

        this.mutableLinePositionBatch.setCharacterPositionsInBytes(lineIndex, characterPositionsInBytes);
        this.mutableLinePositionBatch.setLengthInBytes(lineIndex, currentPositionInBytes - characterPositionsInBytes[0]);
        this.mutableLinePositionBatch.setLengthInCharacters(lineIndex, currentLengthInCharacters);
        this.mutableLinePositionBatch.setNumberOfContainedLines(lineIndex + 1);

        if (numberOfLinesRead < IoConstants.NUMBER_OF_LINES_TO_PREVIEW_BATCH){
            publishPositionBatchPreview();
        }
    }

    private void finishLine(final ArrayList<Long> characterPositionEveryNCharactersInBytes, long endPositionInBytes, long lengthInCharacters) {
        final long[] characterPositionsInBytes = characterPositionEveryNCharactersInBytes.stream().mapToLong(Long::longValue).toArray();

        int lineIndex = numberOfLinesRead % IoConstants.NUMBER_OF_LINES_PER_BATCH;

        this.mutableLinePositionBatch.setCharacterPositionsInBytes(lineIndex, characterPositionsInBytes);
        this.mutableLinePositionBatch.setLengthInBytes(lineIndex, endPositionInBytes - characterPositionsInBytes[0]);
        this.mutableLinePositionBatch.setLengthInCharacters(lineIndex, lengthInCharacters);
        this.mutableLinePositionBatch.setNumberOfContainedLines(lineIndex + 1);

        if (mutableLinePositionBatch.getNumberOfContainedLines() == IoConstants.NUMBER_OF_LINES_PER_BATCH) {
            publishFinishedPositionBatch();
            initPositionsBatch();
        } else if (numberOfLinesRead < IoConstants.NUMBER_OF_LINES_TO_PREVIEW_BATCH){
            publishPositionBatchPreview();
        }
        numberOfLinesRead++;
    }

    private void publishPositionBatchPreview() {
        if (this.mutableLinePositionBatch.getNumberOfContainedLines() > 0) {
            positionsPreviewListener.accept(this.mutableLinePositionBatch);
        }
    }

    private void publishFinishedPositionBatch() {
        if (this.mutableLinePositionBatch.getNumberOfContainedLines() > 0) {
            positionsListener.accept(new LinePositionBatch(this.mutableLinePositionBatch));
        }
    }

    private boolean bufferHasEnoughBytesToNotUnderflowDuringDecode(ByteBuffer readBuffer) {
        return (readBuffer.limit() - readBuffer.position()) > (IoConstants.NUMBER_OF_BYTES_TO_DECODE_OPPORTUNISTICALLY * 3);
    }

    private void initPositionsBatch() {
        long[][] positionsBatch_characterPositionsInBytes = new long[IoConstants.NUMBER_OF_LINES_PER_BATCH][];
        long[] positionsBatch_lengthInBytes = new long[IoConstants.NUMBER_OF_LINES_PER_BATCH];
        long[] positionsBatch_lengthInCharacters = new long[IoConstants.NUMBER_OF_LINES_PER_BATCH];
        this.mutableLinePositionBatch = new MutableLinePositionBatch(positionsBatch_characterPositionsInBytes, positionsBatch_lengthInBytes, positionsBatch_lengthInCharacters, 0);
    }
}
