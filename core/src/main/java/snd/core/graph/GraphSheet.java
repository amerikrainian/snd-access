package snd.core.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * The graph-native table/document emitter: one Tab-stop of vertically-stacked
 * REGIONS (each a region-jump target and a context level, so entering one
 * announces its title once via the path diff), rows navigated Up/Down with the
 * column preserved, cells Left/Right. The framing rules ride the graph's own
 * mechanisms:
 * <ul>
 * <li>column header on column change = left/right EDGE LABELS (the destination
 * column's header);</li>
 * <li>row name when landing off-primary = vertical edge labels into
 * non-primary cells;</li>
 * <li>the whole-row readout = the PRIMARY (column 0) cell's announcement list
 * carrying the row's metadata as extra parts — vertical navigation rides
 * column 0;</li>
 * <li>empty cells read the pluggable "blank" word.</li>
 * </ul>
 * Emit rows in one region, then start the next; {@link #finish()} closes the
 * last region. Raw mode underneath (explicit edges), so no auto positions.
 */
public final class GraphSheet {
    /** Pluggable wording (replaced by the strings layer later). */
    public static volatile String blankWord = "blank";
    public static volatile String tableRole = "table";

    private final GraphBuilder b;
    private final Object keyPrefix;
    private int regionIndex = -1;
    private boolean contextOpen;

    // Current region state. Row cells carry their LOGICAL column (sparse rows
    // skip empty cells — they aren't landable — but vertical navigation still
    // matches columns by logical number).
    private static final class CellRef {
        int col;
        ControlId id;
    }

    private String[] columns; // headers for cells 1..N (null = a plain list region)
    private int row = -1;
    private List<CellRef> prevRowIds;
    private List<CellRef> rowIds;
    private Supplier<String> rowName; // the current row's primary label
    private Supplier<String> prevRowName;
    private Object rowRef;            // the current row's domain object, or null
    private Runnable rowActivate;     // the primary's Enter/Backspace, inherited by cells
    private Runnable rowSecondary;

    public GraphSheet(GraphBuilder b, Object keyPrefix) {
        this.b = b;
        this.keyPrefix = keyPrefix;
    }

    /**
     * Start a region: a jump target and a context level ("Leaderboard,
     * table"). columns are the headers for the metadata cells (column 0 — the
     * primary — has none); null/empty = a plain one-column list region.
     */
    public GraphSheet region(String label) {
        return region(label, null, null);
    }

    public GraphSheet region(String label, String[] columns) {
        return region(label, columns, null);
    }

    public GraphSheet region(String label, String[] columns, String role) {
        closeRegion();
        regionIndex++;
        b.setRegion(CompositeKey.of(keyPrefix, "reg", regionIndex));
        if (label != null && !label.isEmpty()) {
            String effectiveRole = role != null ? role
                    : columns != null && columns.length > 0 ? tableRole : null;
            b.pushContext(label, effectiveRole, false);
            contextOpen = true;
        }
        this.columns = columns;
        return this;
    }

    /**
     * One row: the interactive/primary cell's vtable plus the metadata cell
     * values (their count should match the region's columns). Metadata cells
     * are read-only text. rowRef is the row's DOMAIN OBJECT and should be
     * passed whenever rows can appear/vanish/reorder: keys derive from it, so
     * a removed row's focus slides to a genuinely different identity and the
     * differ announces the landing. The primary additionally carries it as its
     * reference (tier-1 follow when the row moves).
     */
    @SafeVarargs
    public final GraphSheet row(NodeVtable primary, Object rowRef, Supplier<String>... cells) {
        beginRow(primary, rowRef);
        if (cells != null) {
            for (int i = 0; i < cells.length; i++) {
                final Supplier<String> v = cells[i];
                if (v == null) {
                    continue; // sparse: an empty logical column isn't landable
                }
                NodeVtable vt = new NodeVtable();
                vt.controlType = ControlTypes.TEXT;
                vt.announcements = Arrays.asList(new NodeAnnouncement(new Supplier<String>() {
                    @Override
                    public String get() {
                        return blank(v.get());
                    }
                }));
                vt.searchText = rowName; // type-ahead matches the row's name from any cell
                emitCell(vt, i + 1);
            }
        }
        wireVertical();
        return this;
    }

    /** One pre-built cell at an explicit LOGICAL column (1-based; 0 is the primary). */
    public static final class CellAt {
        public final int col;
        public final NodeVtable vtable;

        public CellAt(int col, NodeVtable vtable) {
            this.col = col;
            this.vtable = vtable;
        }
    }

    /** A row whose cells are pre-built vtables at explicit columns (sparse grids). */
    public GraphSheet rowAt(NodeVtable primary, Object rowRef, Iterable<CellAt> cells) {
        beginRow(primary, rowRef);
        if (cells != null) {
            for (CellAt cell : cells) {
                if (cell != null && cell.vtable != null) {
                    emitCell(cell.vtable, cell.col);
                }
            }
        }
        wireVertical();
        return this;
    }

    private void beginRow(NodeVtable primary, Object rowRef) {
        this.rowRef = rowRef;
        row++;
        prevRowIds = rowIds;
        prevRowName = rowName;
        rowIds = new ArrayList<CellRef>();

        // The row's name for vertical edge labels = the primary's label.
        rowName = primary.announcements != null && !primary.announcements.isEmpty()
                ? primary.announcements.get(0).text : null;

        // The row IS its associated element: remember the primary's activations
        // so every cell in the row answers Enter/Backspace like the element.
        rowActivate = primary.onActivate;
        rowSecondary = primary.onSecondary;

        emitCell(primary, 0);
    }

    /** A single full-width line (a lead row, a section note). */
    public GraphSheet line(NodeVtable vt) {
        beginRow(vt, null);
        wireVertical();
        return this;
    }

    /** Close the final region. Call once after the last row. */
    public void finish() {
        closeRegion();
    }

    private void closeRegion() {
        if (contextOpen) {
            b.popContext();
            contextOpen = false;
        }
        // Rows chain across region boundaries: the prev-row linkage carries
        // across region() so the last row of one region wires to the first of
        // the next.
        columns = null;
    }

    private void emitCell(NodeVtable vt, int col) {
        // A row is ONE logical thing spread across cells: Enter/Backspace on
        // any cell trigger the row's primary unless the cell defines its own.
        if (col > 0) {
            if (vt.onActivate == null) {
                vt.onActivate = rowActivate;
            }
            if (vt.onSecondary == null) {
                vt.onSecondary = rowSecondary;
            }
        }
        vt.column = col; // the engine preserves it across row-vanish/type-ahead jumps

        // Identity keys when the row has a domain object (typed composite —
        // never a string-concat hash); positional only for static lines.
        Object skey = rowRef != null
                ? CompositeKey.of(keyPrefix, "row", rowRef, col)
                : CompositeKey.of(keyPrefix, "r", row, col);
        ControlId id = rowRef != null && col == 0
                ? ControlId.referenced(rowRef, skey)
                : ControlId.structural(skey);
        b.addNode(id, vt);

        // Left/right to the nearest EMITTED cell (sparse rows skip empty
        // columns), labeled with the destination column's header (none onto
        // the primary, whose full readout identifies it).
        if (!rowIds.isEmpty()) {
            CellRef left = rowIds.get(rowIds.size() - 1);
            b.connect(id, GraphDir.LEFT, left.id, left.col == 0 ? null : header(left.col));
            b.connect(left.id, GraphDir.RIGHT, id, header(col));
        }
        CellRef ref = new CellRef();
        ref.col = col;
        ref.id = id;
        rowIds.add(ref);
    }

    // Vertical edges between the completed row and the previous one: the same
    // LOGICAL column where both rows have it, else the other row's primary
    // (sparse/ragged rows never dead-end). Labels name the destination ROW
    // when landing off-primary; landings on column 0 stay unlabeled.
    private void wireVertical() {
        if (prevRowIds == null || prevRowIds.isEmpty()) {
            return;
        }

        for (CellRef cell : rowIds) {
            boolean matched = hasCol(prevRowIds, cell.col);
            b.connect(cell.id, GraphDir.UP, findAt(prevRowIds, cell.col),
                    matched && cell.col > 0 ? text(prevRowName) : null);
        }
        for (CellRef cell : prevRowIds) {
            boolean matched = hasCol(rowIds, cell.col);
            b.connect(cell.id, GraphDir.DOWN, findAt(rowIds, cell.col),
                    matched && cell.col > 0 ? text(rowName) : null);
        }
    }

    private static ControlId findAt(List<CellRef> row, int col) {
        for (CellRef c : row) {
            if (c.col == col) {
                return c.id;
            }
        }
        return row.get(0).id; // fall to the row's primary
    }

    private static boolean hasCol(List<CellRef> row, int col) {
        for (CellRef c : row) {
            if (c.col == col) {
                return true;
            }
        }
        return false;
    }

    private String header(int col) {
        return columns != null && col - 1 >= 0 && col - 1 < columns.length ? columns[col - 1] : null;
    }

    private static String text(Supplier<String> f) {
        return f != null ? f.get() : null;
    }

    private static String blank(String v) {
        return v == null || v.trim().isEmpty() ? blankWord : v;
    }
}
