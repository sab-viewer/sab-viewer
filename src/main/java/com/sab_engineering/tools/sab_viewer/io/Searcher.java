package com.sab_engineering.tools.sab_viewer.io;

import com.sab_engineering.tools.sab_viewer.controller.ViewerSettings;

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
import java.util.function.BiConsumer;

public class Searcher {
    private final String fileName;
    private final CharsetDecoder charsetDecoder;

    private final ByteBuffer readBuffer;
    private final CharBuffer decodeBuffer;

    public Searcher(String fileName, Charset charset) throws IOException {
        this.fileName = fileName;
        this.charsetDecoder = charset.newDecoder();
        this.charsetDecoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
        this.charsetDecoder.onMalformedInput(CodingErrorAction.REPLACE);

        this.readBuffer = ByteBuffer.allocate(IoConstants.NUMBER_OF_BYTES_TO_BUFFER_DURING_READ);
        this.decodeBuffer = CharBuffer.allocate(IoConstants.NUMBER_OF_BYTES_TO_DECODE_OPPORTUNISTICALLY);
    }

    public boolean searchInSpecificLines(String literalSearchTerm, LinePositions.LinePositionsView linePositions, ViewerSettings viewerSettings, BiConsumer<Integer, Long> resultListener, boolean stopOnFirstResult) throws IOException {
        long startTimestamp = System.currentTimeMillis();

        int currentLine = viewerSettings.getFirstDisplayedLineIndex();
        long currentLineLengthInCharacters = linePositions.getLengthInCharacters(currentLine);
        long currentColumnIndexInLine = viewerSettings.getFirstDisplayedColumnIndex() + 1;

        if (currentColumnIndexInLine >= currentLineLengthInCharacters) {
            currentLine += 1;
            currentColumnIndexInLine = 0;
        }

        long[] characterPositionsInBytes = linePositions.getCharacterPositionsInBytes(currentLine);
        long startPosition = characterPositionsInBytes[(int) (currentColumnIndexInLine / IoConstants.NUMBER_OF_CHARACTERS_PER_BYTE_POSITION)];
        int charactersToSkipAfterStartPosition = (int) (currentColumnIndexInLine % IoConstants.NUMBER_OF_CHARACTERS_PER_BYTE_POSITION);

        char[] searchTermCharacters = literalSearchTerm.toCharArray();
        int searchTermIndex = 0;
        int foundTerm = 0;

        try (SeekableByteChannel seekableByteChannel = Files.newByteChannel(Paths.get(fileName), StandardOpenOption.READ)) {
            seekableByteChannel.position(startPosition);

            int lineIndex = currentLine;
            long columnIndex = currentColumnIndexInLine;
            char lastCharacter = '\0';

            int beginningOfSearchTermInFile_lineIndex = -1;
            long beginningOfSearchTermInFile_columnIndex = -1;

            boolean byteChannelIsAtEOF;
            do {
                int bytesRead = seekableByteChannel.read(readBuffer);
                byteChannelIsAtEOF = bytesRead == -1;
                readBuffer.flip();
                if (readBuffer.hasRemaining()) {
                    do {
                        decodeBuffer.clear();

                        CoderResult decodeResult = charsetDecoder.decode(readBuffer, decodeBuffer, byteChannelIsAtEOF);
                        if (decodeResult == CoderResult.OVERFLOW || (decodeResult == CoderResult.UNDERFLOW && byteChannelIsAtEOF)) {
                            decodeBuffer.flip();

                            while (decodeBuffer.hasRemaining()) {
                                char currentCharacter = decodeBuffer.get();

                                if (charactersToSkipAfterStartPosition > 0) {
                                    charactersToSkipAfterStartPosition -= 1;
                                    continue;
                                }

                                if (currentCharacter == '\n' || currentCharacter == '\r') {
                                    if (lastCharacter != '\r' || currentCharacter != '\n') {
                                        lineIndex += 1;
                                        columnIndex = -1;
                                    }
                                }

                                if (currentCharacter == searchTermCharacters[searchTermIndex]) {
                                    if (searchTermIndex == 0) {
                                        beginningOfSearchTermInFile_lineIndex = lineIndex;
                                        beginningOfSearchTermInFile_columnIndex = columnIndex;
                                    }
                                    searchTermIndex += 1;

                                    if (searchTermIndex == searchTermCharacters.length) {
                                        foundTerm++;
                                        searchTermIndex = 0;

                                        resultListener.accept(beginningOfSearchTermInFile_lineIndex, beginningOfSearchTermInFile_columnIndex);

                                        if (stopOnFirstResult) {

                                            return endSearch(startTimestamp, true);

                                        }
                                    }
                                } else {
                                    searchTermIndex = 0;
                                }

                                columnIndex += 1;
                                lastCharacter = currentCharacter;
                            }

                        } else {
                            throw new IllegalStateException("Unexpected decoder result " + decodeResult.toString());
                        }
                    } while (readBuffer.hasRemaining() && (byteChannelIsAtEOF || bufferHasEnoughBytesToNotUnderflowDuringDecode(readBuffer)));
                    readBuffer.compact();
                }
            } while (!byteChannelIsAtEOF || readBuffer.hasRemaining());
        }

        return endSearch(startTimestamp, foundTerm > 0);
    }

    private boolean endSearch(long startTimestamp, boolean foundSearchTerm) {
        long timePassedInMs = 1 + System.currentTimeMillis() - startTimestamp;
        System.out.println("Search finished in less than " + timePassedInMs + "ms");
        return foundSearchTerm;
    }

    private boolean bufferHasEnoughBytesToNotUnderflowDuringDecode(ByteBuffer readBuffer) {
        return (readBuffer.limit() - readBuffer.position()) > (IoConstants.NUMBER_OF_BYTES_TO_DECODE_OPPORTUNISTICALLY * 3);
    }
}
