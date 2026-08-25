package app.revanced.extension.youtube.bettercaptions;

/**
 * One of the four places a caption line can sit: two rows along the top edge, two along
 * the bottom.
 *
 * Captions belong to an edge rather than to a point. An upright player and a landscape
 * one are different shapes, so a position held as a fraction of one lands somewhere
 * else in the other, which is how the spoken line ended up adrift in the middle of a
 * landscape picture with its translation pinned to the bottom.
 *
 * Two lines take two of these four, which covers both languages together at either edge
 * and one at each edge with the picture clear between them.
 */
public enum CaptionSlot {
    TOP_FIRST,
    TOP_SECOND,
    BOTTOM_FIRST,
    BOTTOM_SECOND;

    public boolean isTop() {
        return this == TOP_FIRST || this == TOP_SECOND;
    }

    /**
     * @return Whether this is the upper of the two rows at its edge.
     */
    public boolean isFirstRow() {
        return this == TOP_FIRST || this == BOTTOM_FIRST;
    }

    /**
     * @return The slot at the same edge in the other row.
     */
    public CaptionSlot otherRow() {
        switch (this) {
            case TOP_FIRST: return TOP_SECOND;
            case TOP_SECOND: return TOP_FIRST;
            case BOTTOM_FIRST: return BOTTOM_SECOND;
            default: return BOTTOM_FIRST;
        }
    }

    public static CaptionSlot of(boolean top, boolean firstRow) {
        if (top) return firstRow ? TOP_FIRST : TOP_SECOND;
        return firstRow ? BOTTOM_FIRST : BOTTOM_SECOND;
    }
}
