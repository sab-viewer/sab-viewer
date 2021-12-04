package com.sab_engineering.tools.sab_viewer.io;

public class IoConstants {
    public static final int NUMBER_OF_CHARACTERS_PER_BYTE_POSITION = 32 * 1024;
    public static final int NUMBER_OF_BYTES_TO_READ_IN_SCANNER = 1024 * 1024;
    public static final int NUMBER_OF_BYTES_TO_DECODE_OPPORTUNISTICALLY = 64; // three times this must be much less than NUMBER_OF_BYTES_TO_READ_IN_SCANNER

    public static final int NUMBER_OF_LINES_PER_BATCH = 10240;
    public static final int NUMBER_OF_LINES_TO_PREVIEW_BATCH = NUMBER_OF_LINES_PER_BATCH * 4; // until we reach this number each new line is published to controller. After this only new full batches
}
