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

    private final Consumer<LineContent> linePreviewListener;
    private final int numberOfPreviewLines;
    private final int numberOfVisibleCharactersPerLine;
    private final Consumer<LineStatistics> statisticsListener;

    private final ByteBuffer readBuffer;
    private final CharBuffer opportunisticDecodeBuffer;
    private final CharBuffer fallbackDecodeBuffer;

    int numberOfLinesRead;
    int numberOfLinePreviewsPublished;

    char[] linePreviewBuffer;

    public Scanner(String fileName, Charset charset, Consumer<LineContent> linePreviewListener, int numberOfPreviewLines, int numberCharactersForLinePreview, Consumer<LineStatistics> statisticsListener) {
        this.fileName = fileName;
        this.charsetDecoder = charset.newDecoder();
        this.charsetDecoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
        this.charsetDecoder.onMalformedInput(CodingErrorAction.REPLACE);
        this.linePreviewListener = linePreviewListener;
        this.numberOfPreviewLines = numberOfPreviewLines;
        this.numberOfVisibleCharactersPerLine = numberCharactersForLinePreview;
        this.statisticsListener = statisticsListener;

        this.readBuffer = ByteBuffer.allocate(IoConstants.NUMBER_OF_BYTES_TO_READ_IN_SCANNER);
        this.opportunisticDecodeBuffer = CharBuffer.allocate(IoConstants.NUMBER_OF_BYTES_TO_DECODE_OPPORTUNISTICALLY);
        this.fallbackDecodeBuffer = CharBuffer.allocate(1);

        this.numberOfLinesRead = 0;
        this.numberOfLinePreviewsPublished = 0;

        this.linePreviewBuffer = new char[numberCharactersForLinePreview];
    }

    public void scanFile() throws IOException {
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
                                        long[] characterPositionsInBytes = characterPositionEveryNCharactersInBytes.stream().mapToLong(Long::longValue).toArray();
                                        publishLineStatistic(characterPositionsInBytes, lineEndPositionInBytes, charactersInCurrentLine);
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
                long[] characterPositionsInBytes = characterPositionEveryNCharactersInBytes.stream().mapToLong(Long::longValue).toArray();
                publishLineStatistic(characterPositionsInBytes, positionInBytes, charactersInCurrentLine);
            }
        }
    }

    private void publishLineStatistic(long[] characterPositionsInBytes, long endPositionInBytes, long lengthInCharacters) {
        statisticsListener.accept(new LineStatistics(characterPositionsInBytes, endPositionInBytes - characterPositionsInBytes[0], lengthInCharacters));
        numberOfLinesRead++;
    }

    private void publishLinePreview(int charactersInLinePreview) {
        linePreviewListener.accept(new LineContent(new String(linePreviewBuffer, 0, charactersInLinePreview)));
        numberOfLinePreviewsPublished++;
    }

    private boolean bufferHasEnoughBytesToNotUnderflowDuringDecode(ByteBuffer readBuffer) {
        return (readBuffer.limit() - readBuffer.position()) > (IoConstants.NUMBER_OF_BYTES_TO_DECODE_OPPORTUNISTICALLY * 3);
    }
}
