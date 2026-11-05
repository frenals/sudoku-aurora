package com.workbuddy.sudoku;

import java.util.Random;

/**
 * Generates Sudoku puzzles that are guaranteed to have exactly one solution.
 * Pure Java with no Android dependencies, so it can be verified on a desktop JVM.
 */
public final class SudokuGenerator {

    public static final int EASY = 0;
    public static final int MEDIUM = 1;
    public static final int HARD = 2;

    /** Number of pre-filled clues for each difficulty. */
    private static final int[] CLUES = {40, 33, 27};

    private final Random random;

    public SudokuGenerator() {
        this(new Random());
    }

    public SudokuGenerator(long seed) {
        this(new Random(seed));
    }

    private SudokuGenerator(Random random) {
        this.random = random;
    }

    public Puzzle generate(int difficulty) {
        int d = (difficulty < EASY || difficulty > HARD) ? EASY : difficulty;
        int[] solution = generateFullBoard();
        int[] puzzle = solution.clone();

        int targetBlanks = 81 - CLUES[d];
        int[] order = shuffledIndices();
        int blanks = 0;
        for (int idx : order) {
            if (blanks >= targetBlanks) {
                break;
            }
            int saved = puzzle[idx];
            puzzle[idx] = 0;
            if (countSolutions(puzzle, 2) == 1) {
                blanks++;
            } else {
                puzzle[idx] = saved;
            }
        }
        return new Puzzle(puzzle, solution, d);
    }

    private int[] generateFullBoard() {
        int[] board = new int[81];
        fill(board);
        return board;
    }

    /** Randomized backtracking using the minimum-remaining-values heuristic. */
    private boolean fill(int[] board) {
        int best = -1;
        int bestMask = 0;
        int bestCount = 10;
        for (int i = 0; i < 81; i++) {
            if (board[i] != 0) {
                continue;
            }
            int mask = candidateMask(board, i);
            int count = Integer.bitCount(mask);
            if (count == 0) {
                return false;
            }
            if (count < bestCount) {
                bestCount = count;
                best = i;
                bestMask = mask;
                if (count == 1) {
                    break;
                }
            }
        }
        if (best == -1) {
            return true;
        }

        int[] candidates = maskToValues(bestMask);
        shuffle(candidates);
        for (int value : candidates) {
            board[best] = value;
            if (fill(board)) {
                return true;
            }
            board[best] = 0;
        }
        return false;
    }

    /** Counts solutions, stopping as soon as {@code limit} is reached. */
    public static int countSolutions(int[] board, int limit) {
        return solveCount(board.clone(), limit);
    }

    private static int solveCount(int[] board, int limit) {
        if (limit <= 0) {
            return 0;
        }
        int best = -1;
        int bestMask = 0;
        int bestCount = 10;
        for (int i = 0; i < 81; i++) {
            if (board[i] != 0) {
                continue;
            }
            int mask = candidateMask(board, i);
            int count = Integer.bitCount(mask);
            if (count == 0) {
                return 0;
            }
            if (count < bestCount) {
                bestCount = count;
                best = i;
                bestMask = mask;
                if (count == 1) {
                    break;
                }
            }
        }
        if (best == -1) {
            return 1;
        }

        int total = 0;
        int[] candidates = maskToValues(bestMask);
        for (int value : candidates) {
            board[best] = value;
            total += solveCount(board, limit - total);
            board[best] = 0;
            if (total >= limit) {
                return total;
            }
        }
        return total;
    }

    /** Validates that a fully filled board obeys every row, column and box rule. */
    public static boolean isValidComplete(int[] board) {
        if (board == null || board.length != 81) {
            return false;
        }
        for (int i = 0; i < 9; i++) {
            int rowMask = 0;
            int colMask = 0;
            int boxMask = 0;
            for (int j = 0; j < 9; j++) {
                rowMask |= bit(board[i * 9 + j]);
                colMask |= bit(board[j * 9 + i]);
                int r = (i / 3) * 3 + j / 3;
                int c = (i % 3) * 3 + j % 3;
                boxMask |= bit(board[r * 9 + c]);
            }
            if (rowMask != 0x1FF || colMask != 0x1FF || boxMask != 0x1FF) {
                return false;
            }
        }
        return true;
    }

    private static int bit(int value) {
        return (value >= 1 && value <= 9) ? (1 << (value - 1)) : -1;
    }

    private static int candidateMask(int[] board, int cell) {
        int row = cell / 9;
        int col = cell % 9;
        int used = 0;
        for (int i = 0; i < 9; i++) {
            int a = board[row * 9 + i];
            int b = board[i * 9 + col];
            if (a != 0) {
                used |= 1 << (a - 1);
            }
            if (b != 0) {
                used |= 1 << (b - 1);
            }
        }
        int boxRow = (row / 3) * 3;
        int boxCol = (col / 3) * 3;
        for (int r = boxRow; r < boxRow + 3; r++) {
            for (int c = boxCol; c < boxCol + 3; c++) {
                int v = board[r * 9 + c];
                if (v != 0) {
                    used |= 1 << (v - 1);
                }
            }
        }
        return (~used) & 0x1FF;
    }

    private static int[] maskToValues(int mask) {
        int[] values = new int[Integer.bitCount(mask)];
        int k = 0;
        for (int v = 1; v <= 9; v++) {
            if ((mask & (1 << (v - 1))) != 0) {
                values[k++] = v;
            }
        }
        return values;
    }

    private void shuffle(int[] values) {
        for (int i = values.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int tmp = values[i];
            values[i] = values[j];
            values[j] = tmp;
        }
    }

    private int[] shuffledIndices() {
        int[] order = new int[81];
        for (int i = 0; i < 81; i++) {
            order[i] = i;
        }
        shuffle(order);
        return order;
    }

    public static final class Puzzle {
        public final int[] cells;
        public final int[] solution;
        public final int difficulty;

        Puzzle(int[] cells, int[] solution, int difficulty) {
            this.cells = cells;
            this.solution = solution;
            this.difficulty = difficulty;
        }

        public int clueCount() {
            int n = 0;
            for (int v : cells) {
                if (v != 0) {
                    n++;
                }
            }
            return n;
        }
    }
}
