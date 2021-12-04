package com.sab_engineering.tools.sab_viewer.io;

import java.util.ArrayList;
import java.util.List;

public class LinePositions {
    private final List<LinePositionBatch> linePositionBatches;
    private MutableLinePositionBatch lastLinePositionBatch;
    private MutableLinePositionBatch lastLinePositionPreviewBatchReference;

    public LinePositions() {
        linePositionBatches = new ArrayList<>(1024);
        lastLinePositionBatch = null;
        lastLinePositionPreviewBatchReference = null;
    }

    public void add(LinePositionBatch positionBatch) {
        linePositionBatches.add(positionBatch);
        lastLinePositionBatch = null;
        lastLinePositionPreviewBatchReference = null;
    }

    public void updateLastBatch(MutableLinePositionBatch positionBatch) {
        if (positionBatch != this.lastLinePositionPreviewBatchReference) {
            lastLinePositionPreviewBatchReference = positionBatch;
            lastLinePositionBatch = new MutableLinePositionBatch(positionBatch);
        }
        for (int i = lastLinePositionBatch.getNumberOfContainedLines(); i < positionBatch.getNumberOfContainedLines(); i++) {
            lastLinePositionBatch.setCharacterPositionsInBytes(i, positionBatch.getCharacterPositionsInBytes(i));
            lastLinePositionBatch.setLengthInBytes(i, positionBatch.getLengthInBytes(i));
            lastLinePositionBatch.setLengthInCharacters(i, positionBatch.getLengthInCharacters(i));
            lastLinePositionBatch.setNumberOfContainedLines(lastLinePositionBatch.getNumberOfContainedLines() + 1);
        }
    }

    public boolean isEmpty() {
        return linePositionBatches.isEmpty() && (lastLinePositionBatch == null || lastLinePositionBatch.getNumberOfContainedLines() == 0);
    }

    public long[] getCharacterPositionsInBytes(int lineIndex) {
        int index = lineIndex / IoConstants.NUMBER_OF_LINES_PER_BATCH;

        LinePositionBatch linePositionBatch;
        if (index == linePositionBatches.size()) {
            linePositionBatch = lastLinePositionBatch;
        } else {
            linePositionBatch = linePositionBatches.get(index);
        }
        return linePositionBatch.getCharacterPositionsInBytes(lineIndex % IoConstants.NUMBER_OF_LINES_PER_BATCH);
    }

    public long getLengthInBytes(int lineIndex) {
        int index = lineIndex / IoConstants.NUMBER_OF_LINES_PER_BATCH;

        LinePositionBatch linePositionBatch;
        if (index == linePositionBatches.size()) {
            linePositionBatch = lastLinePositionBatch;
        } else {
            linePositionBatch = linePositionBatches.get(index);
        }
        return linePositionBatch.getLengthInBytes(lineIndex % IoConstants.NUMBER_OF_LINES_PER_BATCH);
    }

    public long getLengthInCharacters(int lineIndex) {
        int index = lineIndex / IoConstants.NUMBER_OF_LINES_PER_BATCH;

        LinePositionBatch linePositionBatch;
        if (index == linePositionBatches.size()) {
            linePositionBatch = lastLinePositionBatch;
        } else {
            linePositionBatch = linePositionBatches.get(index);
        }
        return linePositionBatch.getLengthInCharacters(lineIndex % IoConstants.NUMBER_OF_LINES_PER_BATCH);
    }

    public long getBytePositionOfEndOfLastLine() {
        LinePositionBatch linePositionBatch;
        if (lastLinePositionBatch != null) {
            linePositionBatch = lastLinePositionBatch;
        } else if (linePositionBatches.size() > 0){
            linePositionBatch = linePositionBatches.get(linePositionBatches.size() - 1);
        } else {
            return 0;
        }
        int lastLineIndexInBatch = linePositionBatch.getNumberOfContainedLines() -1;
        return linePositionBatch.getCharacterPositionsInBytes(lastLineIndexInBatch)[0] + linePositionBatch.getLengthInBytes(lastLineIndexInBatch);
    }

    public int getNumberOfContainedLines() {
        if (lastLinePositionBatch != null) {
            int linesInFinishedBatches = linePositionBatches.size() * IoConstants.NUMBER_OF_LINES_PER_BATCH;
            return linesInFinishedBatches + lastLinePositionBatch.getNumberOfContainedLines();
        } else if (linePositionBatches.size() > 0) {
            int linesInFinishedBatches = (linePositionBatches.size() - 1) * IoConstants.NUMBER_OF_LINES_PER_BATCH;
            return linesInFinishedBatches + linePositionBatches.get(linePositionBatches.size() - 1).numberOfContainedLines;
        } else {
            return 0;
        }
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
