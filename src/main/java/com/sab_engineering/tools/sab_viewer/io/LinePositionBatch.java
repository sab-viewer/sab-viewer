package com.sab_engineering.tools.sab_viewer.io;

public class LinePositionBatch {
    private final long[][] characterPositionsInBytes; // position in bytes of every n characters; for n see IoConstants.NUMBER_OF_CHARACTERS_PER_BYTE_POSITION
    private final long[] lengthInBytes;
    private final long[] lengthInCharacters;
    private final int numberOfContainedLines; // when "full" this should be equal to IoConstants.NUMBER_OF_LINES_PER_BATCH

    public LinePositionBatch(long[][] characterPositionsInBytes, long[] lengthInBytes, long[] lengthInCharacters, int numberOfContainedLines) {
        this.characterPositionsInBytes = characterPositionsInBytes;
        this.lengthInBytes = lengthInBytes;
        this.lengthInCharacters = lengthInCharacters;
        this.numberOfContainedLines = numberOfContainedLines;
    }

    public long[] getCharacterPositionsInBytes(int lineIndex) {
        return characterPositionsInBytes[lineIndex];
    }

    public long getLengthInBytes(int lineIndex) {
        return lengthInBytes[lineIndex];
    }

    public long getLengthInCharacters(int lineIndex) {
        return lengthInCharacters[lineIndex];
    }

    public int getNumberOfContainedLines() {
        return numberOfContainedLines;
    }
}
