package com.sab_engineering.tools.sab_viewer.io;

import com.sab_engineering.tools.sab_viewer.controller.ViewerSettings;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class Reader implements Closeable {
    private final Charset charset;
    private final SeekableByteChannel seekableByteChannel;

    public Reader(String fileName, Charset charset) throws IOException {
        this.charset = charset;
        seekableByteChannel = Files.newByteChannel(Paths.get(fileName), StandardOpenOption.READ);
    }

    public List<LineContent> readSpecificLines(List<LineStatistics> linesToReadOrderedByStartPositionInFile, ViewerSettings viewerSettings) throws IOException {
        long startTimestamp = System.currentTimeMillis();
        long offsetFromBeginningOfLineInCharacters = viewerSettings.getFirstDisplayedColumnIndex();
        int numberOfVisibleCharactersPerLine = viewerSettings.getDisplayedColumns();

        if (offsetFromBeginningOfLineInCharacters < 0) {
            throw new IllegalStateException("Negative offsets are not supported: " + offsetFromBeginningOfLineInCharacters);
        }

        List<LineContent> resultingLines = new ArrayList<>(linesToReadOrderedByStartPositionInFile.size());
        for (LineStatistics lineStatistics : linesToReadOrderedByStartPositionInFile) {
            if (offsetFromBeginningOfLineInCharacters >= lineStatistics.getLengthInCharacters()) {
                resultingLines.add(new LineContent(""));
            } else {
                int charactersToRead = (int) Math.min(numberOfVisibleCharactersPerLine, lineStatistics.getLengthInCharacters() - offsetFromBeginningOfLineInCharacters);
                int characterMultipleToStartReading = (int) (offsetFromBeginningOfLineInCharacters / IoConstants.NUMBER_OF_CHARACTERS_PER_BYTE_POSITION);
                int characterMultipleToStopReading = 1 + (int) ((offsetFromBeginningOfLineInCharacters + charactersToRead) / IoConstants.NUMBER_OF_CHARACTERS_PER_BYTE_POSITION);

                long positionToStartReadingInBytes = lineStatistics.getCharacterPositionsInBytes()[characterMultipleToStartReading];
                long positionToStopReadingInBytes;
                if (characterMultipleToStopReading < lineStatistics.getCharacterPositionsInBytes().length) {
                    positionToStopReadingInBytes = lineStatistics.getCharacterPositionsInBytes()[characterMultipleToStopReading];
                } else {
                    positionToStopReadingInBytes = lineStatistics.getCharacterPositionsInBytes()[0] + lineStatistics.getLengthInBytes();
                }
                int bytesToRead = (int) (positionToStopReadingInBytes - positionToStartReadingInBytes);

                seekableByteChannel.position(positionToStartReadingInBytes);
                ByteBuffer lineBuffer = ByteBuffer.allocate(bytesToRead);
                int bytesRead = seekableByteChannel.read(lineBuffer);
                if (bytesRead != bytesToRead) {
                    throw new IllegalStateException("File content changed unexpectedly while reading it");
                }
                int numberOfCharactersToDiscardAtStartOfString = (int) (offsetFromBeginningOfLineInCharacters % IoConstants.NUMBER_OF_CHARACTERS_PER_BYTE_POSITION);
                String charactersRead = new String(lineBuffer.array(), charset).substring(numberOfCharactersToDiscardAtStartOfString, numberOfCharactersToDiscardAtStartOfString + charactersToRead);
                resultingLines.add(new LineContent(charactersRead));
            }
        }

        long timePassedInMs = 1 + System.currentTimeMillis() - startTimestamp;
        System.out.println("Reader finished in less than " + timePassedInMs + "ms");

        return resultingLines;
    }

    @Override
    public void close() throws IOException {
        seekableByteChannel.close();
    }
}
