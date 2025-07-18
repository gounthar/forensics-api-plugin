package io.jenkins.plugins.forensics.delta;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * A change made on specific lines within a specific file.
 *
 * <p>
 * The interval of lines which contains the change is defined by a starting and an ending point (1-based line counter).
 * Also, the affected lines of the file before the change has been inserted are specified by a starting and an ending
 * point, as already described, in order to be able to determine removed lines for example.
 * </p>
 *
 * @author Florian Orendi
 */
public class Change implements Serializable {
    @Serial
    private static final long serialVersionUID = 1543635877389921937L;

    private final ChangeEditType changeEditType;

    /**
     * The included starting point of the lines which will be affected by this change (1-based).
     */
    private final int changedFromLine; // since 1.9.0
    /**
     * The included ending point of the lines which will be affected by this change (1-based).
     */
    private final int changedToLine; // since 1.9.0

    /**
     * The included starting point of the lines which contain the change (1-based).
     */
    private final int fromLine;
    /**
     * The included ending point of the lines which contain the change (1-based).
     */
    private final int toLine;

    /**
     * Constructor for an instance which wraps a specific change within a file.
     *
     * @param changeEditType
     *         The type of the change
     * @param changedFromLine
     *         The starting line of the lines which are affected by the change
     * @param changedToLine
     *         The ending line of the lines which are affected by the change
     * @param fromLine
     *         The starting line of the inserted change
     * @param toLine
     *         The ending line of the inserted change
     */
    public Change(final ChangeEditType changeEditType, final int changedFromLine, final int changedToLine,
            final int fromLine, final int toLine) {
        this.changeEditType = changeEditType;
        this.changedFromLine = changedFromLine;
        this.changedToLine = changedToLine;
        this.fromLine = fromLine;
        this.toLine = toLine;
    }

    public ChangeEditType getEditType() {
        return changeEditType;
    }

    public int getChangedFromLine() {
        return changedFromLine;
    }

    public int getChangedToLine() {
        return changedToLine;
    }

    public int getFromLine() {
        return fromLine;
    }

    public int getToLine() {
        return toLine;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var change = (Change) o;
        return changedFromLine == change.changedFromLine && changedToLine == change.changedToLine
                && fromLine == change.fromLine && toLine == change.toLine && changeEditType == change.changeEditType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(changeEditType, changedFromLine, changedToLine, fromLine, toLine);
    }
}
