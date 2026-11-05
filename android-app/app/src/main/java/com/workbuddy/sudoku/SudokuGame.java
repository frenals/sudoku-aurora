package com.workbuddy.sudoku;

import android.os.SystemClock;

import java.util.ArrayDeque;

/** Holds all mutable game state, including undo history and persistence. */
public final class SudokuGame {

    public static final String[] DIFFICULTY_NAMES = {"Easy", "Medium", "Hard"};

    private int[] givens = new int[81];
    private int[] solution = new int[81];
    private int[] values = new int[81];
    private int[] notes = new int[81];

    private int difficulty = 0;
    private int selected = -1;
    private boolean noteMode = false;
    private boolean autoCheck = false;
    private int mistakes = 0;
    private int hints = 0;
    private long elapsedMs = 0;
    private long startRealtime = 0;
    private boolean running = false;
    private boolean solved = false;
    private boolean started = false;

    private final ArrayDeque<Move> undoStack = new ArrayDeque<>();

    private static final class Move {
        final int[] values;
        final int[] notes;
        final int mistakes;
        final int hints;
        final boolean solved;

        Move(int[] values, int[] notes, int mistakes, int hints, boolean solved) {
            this.values = values;
            this.notes = notes;
            this.mistakes = mistakes;
            this.hints = hints;
            this.solved = solved;
        }
    }

    public boolean hasPuzzle() {
        return started;
    }

    public void newGame(SudokuGenerator.Puzzle puzzle) {
        givens = puzzle.cells.clone();
        solution = puzzle.solution.clone();
        values = puzzle.cells.clone();
        notes = new int[81];
        difficulty = puzzle.difficulty;
        selected = -1;
        noteMode = false;
        mistakes = 0;
        hints = 0;
        solved = false;
        started = true;
        undoStack.clear();
        elapsedMs = 0;
        startRealtime = SystemClock.elapsedRealtime();
        running = true;
    }

    public void select(int cell) {
        if (cell >= 0 && cell < 81) {
            selected = cell;
        }
    }

    /** Enters a digit, or toggles a pencil mark when notes mode is on. */
    public boolean input(int digit) {
        if (!started || solved || selected < 0 || digit < 1 || digit > 9) {
            return false;
        }
        int cell = selected;
        if (givens[cell] != 0) {
            return false;
        }

        if (noteMode) {
            if (values[cell] != 0) {
                return false;
            }
            pushMove();
            notes[cell] ^= (1 << (digit - 1));
            return true;
        }

        int newValue = (values[cell] == digit) ? 0 : digit;
        pushMove();
        values[cell] = newValue;
        if (newValue != 0) {
            clearPeerNotes(cell, newValue);
            if (autoCheck && newValue != solution[cell]) {
                mistakes++;
            }
        }
        checkSolved();
        return true;
    }

    public boolean erase() {
        if (!started || solved || selected < 0 || givens[selected] != 0) {
            return false;
        }
        if (values[selected] == 0 && notes[selected] == 0) {
            return false;
        }
        pushMove();
        values[selected] = 0;
        notes[selected] = 0;
        return true;
    }

    /** Fills the selected cell (or the first incorrect cell) with the correct value. */
    public boolean hint() {
        if (!started || solved) {
            return false;
        }
        int cell = selected;
        if (cell < 0 || givens[cell] != 0 || values[cell] == solution[cell]) {
            cell = -1;
            for (int i = 0; i < 81; i++) {
                if (values[i] != solution[i]) {
                    cell = i;
                    break;
                }
            }
        }
        if (cell < 0) {
            return false;
        }
        pushMove();
        values[cell] = solution[cell];
        notes[cell] = 0;
        clearPeerNotes(cell, solution[cell]);
        hints++;
        selected = cell;
        checkSolved();
        return true;
    }

    public boolean undo() {
        if (undoStack.isEmpty()) {
            return false;
        }
        Move m = undoStack.pop();
        boolean wasSolved = solved;
        values = m.values;
        notes = m.notes;
        mistakes = m.mistakes;
        hints = m.hints;
        solved = m.solved;
        if (wasSolved && !solved) {
            resume();
        }
        return true;
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    private void pushMove() {
        undoStack.push(new Move(values.clone(), notes.clone(), mistakes, hints, solved));
        if (undoStack.size() > 400) {
            // Keep memory bounded; the oldest history entry is discarded.
            undoStack.removeLast();
        }
    }

    private void clearPeerNotes(int cell, int value) {
        int bit = 1 << (value - 1);
        int row = cell / 9;
        int col = cell % 9;
        for (int i = 0; i < 9; i++) {
            notes[row * 9 + i] &= ~bit;
            notes[i * 9 + col] &= ~bit;
        }
        int boxRow = (row / 3) * 3;
        int boxCol = (col / 3) * 3;
        for (int r = boxRow; r < boxRow + 3; r++) {
            for (int c = boxCol; c < boxCol + 3; c++) {
                notes[r * 9 + c] &= ~bit;
            }
        }
    }

    private void checkSolved() {
        for (int i = 0; i < 81; i++) {
            if (values[i] != solution[i]) {
                return;
            }
        }
        solved = true;
        pause();
    }

    public boolean hasConflict(int cell) {
        int value = values[cell];
        if (value == 0) {
            return false;
        }
        int row = cell / 9;
        int col = cell % 9;
        for (int i = 0; i < 9; i++) {
            if (i != col && values[row * 9 + i] == value) {
                return true;
            }
            if (i != row && values[i * 9 + col] == value) {
                return true;
            }
        }
        int boxRow = (row / 3) * 3;
        int boxCol = (col / 3) * 3;
        for (int r = boxRow; r < boxRow + 3; r++) {
            for (int c = boxCol; c < boxCol + 3; c++) {
                int idx = r * 9 + c;
                if (idx != cell && values[idx] == value) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isWrong(int cell) {
        return autoCheck && values[cell] != 0 && values[cell] != solution[cell];
    }

    /** How many copies of this digit are still unplaced on the board. */
    public int remaining(int digit) {
        int placed = 0;
        for (int v : values) {
            if (v == digit) {
                placed++;
            }
        }
        return Math.max(0, 9 - placed);
    }

    /** Number of filled cells (used for the progress bar). */
    public int getFilledCount() {
        int n = 0;
        for (int v : values) {
            if (v != 0) {
                n++;
            }
        }
        return n;
    }

    public void resume() {
        if (started && !solved && !running) {
            startRealtime = SystemClock.elapsedRealtime();
            running = true;
        }
    }

    public void pause() {
        if (running) {
            elapsedMs += SystemClock.elapsedRealtime() - startRealtime;
            running = false;
        }
    }

    public long elapsedMs() {
        return elapsedMs + (running ? SystemClock.elapsedRealtime() - startRealtime : 0);
    }

    // ---- getters / toggles -------------------------------------------------

    public int getValue(int cell) { return values[cell]; }
    public int getNotes(int cell) { return notes[cell]; }
    public boolean isGiven(int cell) { return givens[cell] != 0; }
    public int getSelected() { return selected; }
    public int getDifficulty() { return difficulty; }
    public int getMistakes() { return mistakes; }
    public int getHints() { return hints; }
    public boolean isNoteMode() { return noteMode; }
    public boolean isAutoCheck() { return autoCheck; }
    public boolean isSolved() { return solved; }

    public void toggleNoteMode() { noteMode = !noteMode; }
    public void toggleAutoCheck() { autoCheck = !autoCheck; }

    // ---- persistence ------------------------------------------------------

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("1|")
                .append(difficulty).append('|')
                .append(autoCheck ? 1 : 0).append('|')
                .append(noteMode ? 1 : 0).append('|')
                .append(mistakes).append('|')
                .append(hints).append('|')
                .append(elapsedMs()).append('|')
                .append(selected).append('|')
                .append(solved ? 1 : 0).append('|')
                .append(started ? 1 : 0).append('|');
        appendArray(sb, givens);
        sb.append('|');
        appendArray(sb, solution);
        sb.append('|');
        appendArray(sb, values);
        sb.append('|');
        appendArray(sb, notes);
        return sb.toString();
    }

    public boolean restore(String state) {
        if (state == null || state.isEmpty()) {
            return false;
        }
        try {
            String[] p = state.split("\\|", -1);
            if (p.length != 14 || !"1".equals(p[0])) {
                return false;
            }
            int[] g = parseArray(p[10]);
            int[] s = parseArray(p[11]);
            int[] v = parseArray(p[12]);
            int[] n = parseArray(p[13]);
            if (g == null || s == null || v == null || n == null) {
                return false;
            }
            givens = g;
            solution = s;
            values = v;
            notes = n;
            difficulty = Integer.parseInt(p[1]);
            autoCheck = "1".equals(p[2]);
            noteMode = "1".equals(p[3]);
            mistakes = Integer.parseInt(p[4]);
            hints = Integer.parseInt(p[5]);
            elapsedMs = Long.parseLong(p[6]);
            selected = Integer.parseInt(p[7]);
            solved = "1".equals(p[8]);
            started = "1".equals(p[9]);
            running = false;
            undoStack.clear();
            return started && SudokuGenerator.isValidComplete(solution);
        } catch (Exception e) {
            return false;
        }
    }

    private static void appendArray(StringBuilder sb, int[] array) {
        for (int i = 0; i < array.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(array[i]);
        }
    }

    private static int[] parseArray(String text) {
        String[] parts = text.split(",", -1);
        if (parts.length != 81) {
            return null;
        }
        int[] out = new int[81];
        for (int i = 0; i < 81; i++) {
            out[i] = Integer.parseInt(parts[i]);
        }
        return out;
    }
}
