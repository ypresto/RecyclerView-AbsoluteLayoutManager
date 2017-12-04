package net.ypresto.recyclerview.absolutelayoutmanager;

/**
 * Calculate offset for each span, coping with decimal pixels.
 * This is necessary to align column border, by normalizing x position of cells in each row.
 * Note that Android only supports integer rect.
 * So length of span may vary for each index (&lt;= +-1px).
 */
public class SpanCalculator {
    private final int mInterItemSpacing;

    public SpanCalculator() {
        this(0);
    }

    public SpanCalculator(int interItemSpacing) {
        mInterItemSpacing = interItemSpacing;
    }

    public int calculateStartOffsetForSpan(int spanIndex, int wholeSpanCount, int wholeLength) {
        int spacingLength = mInterItemSpacing * (wholeSpanCount - 1);
        int contentLength = wholeLength - spacingLength;
        return Math.round((float) contentLength * spanIndex / wholeSpanCount + spanIndex * mInterItemSpacing);
    }

    public int calculateEndOffsetForSpan(int spanIndex, int wholeSpanCount, int wholeLength) {
        // NOTE: Calculates space by subtracting to always use same space regardless of round() result.
        return calculateStartOffsetForSpan(spanIndex + 1, wholeSpanCount, wholeLength) - mInterItemSpacing;
    }
}
