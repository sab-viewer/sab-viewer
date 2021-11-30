package com.sab_engineering.tools.sab_viewer.io;

import java.util.ArrayList;
import java.util.List;

public class LinePositions {
    private final List<LinePositionBatch> linePositionBatches;
    private LinePositionBatch lastLinePositionBatch;

    public LinePositions() {
        linePositionBatches = new ArrayList<>(1024);
        lastLinePositionBatch = null;
    }

    public void add(LinePositionBatch positionBatch) {
        lastLinePositionBatch = positionBatch;
        linePositionBatches.add(lastLinePositionBatch);
    }

    public boolean isEmpty() {
        return linePositionBatches.isEmpty();
    }

    public long[] getCharacterPositionsInBytes(int lineIndex) {
        LinePositionBatch linePositionBatch = linePositionBatches.get(lineIndex / IoConstants.NUMBER_OF_LINES_PER_BATCH);
        return linePositionBatch.getCharacterPositionsInBytes(lineIndex % IoConstants.NUMBER_OF_LINES_PER_BATCH);
    }

    public long getLengthInBytes(int lineIndex) {
        LinePositionBatch linePositionBatch = linePositionBatches.get(lineIndex / IoConstants.NUMBER_OF_LINES_PER_BATCH);
        return linePositionBatch.getLengthInBytes(lineIndex % IoConstants.NUMBER_OF_LINES_PER_BATCH);
    }

    public long getLengthInCharacters(int lineIndex) {
        LinePositionBatch linePositionBatch = linePositionBatches.get(lineIndex / IoConstants.NUMBER_OF_LINES_PER_BATCH);
        return linePositionBatch.getLengthInCharacters(lineIndex % IoConstants.NUMBER_OF_LINES_PER_BATCH);
    }

    public long getBytePositionOfEndOfLastLine() {
        if (lastLinePositionBatch == null) {
            return 0;
        }
        int lastLineIndexInBatch = lastLinePositionBatch.getNumberOfContainedLines() -1;
        return lastLinePositionBatch.getCharacterPositionsInBytes(lastLineIndexInBatch)[0] + lastLinePositionBatch.getLengthInBytes(lastLineIndexInBatch);
    }

    public int getNumberOfContainedLines() {
        return ((linePositionBatches.size() - 1) * IoConstants.NUMBER_OF_LINES_PER_BATCH) + lastLinePositionBatch.getNumberOfContainedLines();
    }

    public LinePositionsView asView() {
        return subPositions(0, getNumberOfContainedLines());
    }

    public LinePositionsView subPositions(int fromLineIndex, int toLineIndexExclusive) {
        return new LinePositionsView(fromLineIndex, toLineIndexExclusive, this);
    }

    public static class LinePositionsView {
        private final int fromLineIndex;
        private final int toLineIndexExclusive;

        private final LinePositions positions;

        public LinePositionsView(int fromLineIndex, int toLineIndexExclusive, LinePositions positions) {
            this.fromLineIndex = fromLineIndex;
            this.toLineIndexExclusive = toLineIndexExclusive;
            this.positions = positions;
        }

        public long[] getCharacterPositionsInBytes(int lineIndex) {
            boundsCheck(lineIndex);
            return positions.getCharacterPositionsInBytes(lineIndex);
        }

        public long getLengthInBytes(int lineIndex) {
            boundsCheck(lineIndex);
            return positions.getLengthInBytes(lineIndex);
        }

        public long getLengthInCharacters(int lineIndex) {
            boundsCheck(lineIndex);
            return positions.getLengthInCharacters(lineIndex);
        }

        private void boundsCheck(int lineIndex) {
            if (lineIndex < fromLineIndex || lineIndex >= toLineIndexExclusive) {
                throw new IndexOutOfBoundsException("Line index is not between " + fromLineIndex + " and " + toLineIndexExclusive);
            }
        }
    }
}
