package net.bdew.generators.menu;

/**
 * Machine values are sent to the client in 15-bit pieces (vanilla syncs menu data as shorts), three per int.
 * The server cuts a value up; the client keeps each piece as received and joins them again.
 */
final class SyncChunks {
    static final int BITS = 15;
    static final int MASK = (1 << BITS) - 1;
    static final int PIECES = 3;

    private SyncChunks() {}

    /** Piece {@code n} (0 = lowest) of a value. */
    static int piece(int value, int n) { return (value >>> (n * BITS)) & MASK; }

    /** Joins the three pieces of a value back together. */
    static int join(int piece0, int piece1, int piece2) { return (piece0 & MASK) | ((piece1 & MASK) << BITS) | (piece2 << (2 * BITS)); }
}
