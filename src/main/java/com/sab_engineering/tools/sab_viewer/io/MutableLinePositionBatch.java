package com.sab_engineering.tools.sab_viewer.io;

public class MutableLinePositionBatch extends LinePositionBatch {

    public MutableLinePositionBatch(LinePositionBatch other) {
        super(other);
    }

    public MutableLinePositionBatch(long[][] characterPositionsInBytes, long[] lengthInBytes, long[] lengthInCharacters, int numberOfContainedLines) {
        super(characterPositionsInBytes, lengthInBytes, lengthInCharacters, numberOfContainedLines);
    }

    public void setCharacterPositionsInBytes(int lineIndex, long[] characterPositionsInBytes) {
        this.characterPositionsInBytes[lineIndex] = characterPositionsInBytes;
    }

    public void setLengthInBytes(int lineIndex, long lengthInBytes) {
        this.lengthInBytes[lineIndex] = lengthInBytes;
    }

    public void setLengthInCharacters(int lineIndex, long lengthInCharacters) {
        this.lengthInCharacters[lineIndex] = lengthInCharacters;
    }

    public void setNumberOfContainedLines(int numberOfContainedLines) {
        this.numberOfContainedLines = numberOfContainedLines;
    }
}
