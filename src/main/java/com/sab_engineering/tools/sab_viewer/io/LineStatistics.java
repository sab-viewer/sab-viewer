package com.sab_engineering.tools.sab_viewer.io;

public class LineStatistics {
    private final long[] characterPositionsInBytes; // position in bytes of every n characters; for n see IoConstants.NUMBER_OF_CHARACTERS_PER_BYTE_POSITION
    private final long lengthInBytes;
    private final long lengthInCharacters;

    public LineStatistics(long[] characterPositionsInBytes, long lengthInBytes, long lengthInCharacters) {
        this.characterPositionsInBytes = characterPositionsInBytes;
        this.lengthInBytes = lengthInBytes;
        this.lengthInCharacters = lengthInCharacters;
    }

    public long[] getCharacterPositionsInBytes() {
        return characterPositionsInBytes;
    }

    public long getLengthInBytes() {
        return lengthInBytes;
    }

    public long getLengthInCharacters() {
        return lengthInCharacters;
    }
}
