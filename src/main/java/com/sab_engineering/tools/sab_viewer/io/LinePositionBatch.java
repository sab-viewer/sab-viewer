package com.sab_engineering.tools.sab_viewer.io;

public class LinePositionBatch {
    protected final long[][] characterPositionsInBytes; // position in bytes of every n characters; for n see IoConstants.NUMBER_OF_CHARACTERS_PER_BYTE_POSITION
    protected final long[] lengthInBytes;
    protected final long[] lengthInCharacters;
    protected int numberOfContainedLines; // when "full" this should be equal to IoConstants.NUMBER_OF_LINES_PER_BATCH

    protected LinePositionBatch(long[][] characterPositionsInBytes, long[] lengthInBytes, long[] lengthInCharacters, int numberOfContainedLines) {
        this.characterPositionsInBytes = characterPositionsInBytes;
        this.lengthInBytes = lengthInBytes;
        this.lengthInCharacters = lengthInCharacters;
        this.numberOfContainedLines = numberOfContainedLines;
    }

    public LinePositionBatch(LinePositionBatch other) {
        this.characterPositionsInBytes = other.characterPositionsInBytes;
        this.lengthInBytes = other.lengthInBytes;
        this.lengthInCharacters = other.lengthInCharacters;
        this.numberOfContainedLines = other.numberOfContainedLines;
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
