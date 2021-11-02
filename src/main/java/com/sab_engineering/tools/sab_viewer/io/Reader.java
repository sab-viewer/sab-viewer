package com.sab_engineering.tools.sab_viewer.io;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class Reader implements Closeable {
    private final SeekableByteChannel seekableByteChannel;

    public Reader(String fileName) throws IOException {
        seekableByteChannel = Files.newByteChannel(Paths.get(fileName), StandardOpenOption.READ);
    }

    public List<LineContent> readSpecificLines(List<LineStatistics> linesToReadOrderedByStartPositionInFile, long offsetFromBeginningOfLine, int numberOfVisibleCharactersPerLine) throws IOException {
        if (offsetFromBeginningOfLine < 0) {
            throw new IllegalStateException("Negative offsets are not supported: " + offsetFromBeginningOfLine);
        }
        System.out.println(System.currentTimeMillis() + " read starts");
        List<LineContent> resultingLines = new ArrayList<>(linesToReadOrderedByStartPositionInFile.size());
        for (LineStatistics lineStatistics : linesToReadOrderedByStartPositionInFile) {
            long offsetToApply = Math.min(offsetFromBeginningOfLine, lineStatistics.getLength());
            int charactersToRead = (int) Math.min(numberOfVisibleCharactersPerLine, lineStatistics.getLength() - offsetToApply);
            if (charactersToRead > 0) {
                seekableByteChannel.position(lineStatistics.getStartPositionInFile() + offsetToApply);
                ByteBuffer lineBuffer = ByteBuffer.allocate(charactersToRead);
                int charactersRead = seekableByteChannel.read(lineBuffer);
                if (charactersRead != charactersToRead) {
                    throw new IllegalStateException("File content changed unexpectedly while reading it");
                }
                resultingLines.add(new LineContent(new String(lineBuffer.array(), StandardCharsets.UTF_8)));
            } else {
                resultingLines.add(new LineContent(""));
            }
        }
        System.out.println(System.currentTimeMillis() + " read ends");
        return resultingLines;
    }

    @Override
    public void close() throws IOException {
        seekableByteChannel.close();
    }
}
