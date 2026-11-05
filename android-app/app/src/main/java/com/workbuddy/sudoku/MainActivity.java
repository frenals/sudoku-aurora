package com.workbuddy.sudoku;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** Main screen: wires the game engine to the animated board, keyboard and HUD. */
public final class MainActivity extends Activity {

    private static final String PREFS = "sudoku";
    private static final String KEY_STATE = "state";

    private static final int COLOR_MUTED = 0xFF8B98C2;
    private static final int COLOR_TEXT = 0xFFEAF0FF;
    private static final int COLOR_ACCENT = 0xFF2DD4BF;
    private static final int COLOR_WRONG = 0xFFFF6B7A;
    private static final int COLOR_BTN = 0xFF161E3A;
    private static final int COLOR_BTN_ON = 0xFF2C3A6B;
    private static final int COLOR_STROKE = 0xFF2C3760;
    private static final int COLOR_PRIMARY = 0xFF7C5CFF;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateStatus();
            handler.postDelayed(this, 1000);
        }
    };

    private SudokuGame game;
    private SudokuBoardView board;
    private NumberPadView numberPad;
    private SegmentedControlView segmented;
    private ConfettiView confetti;
    private SharedPreferences prefs;

    private TextView timerView;
    private TextView mistakesView;
    private TextView checkToggle;
    private TextView progressText;
    private TextView winStats;
    private TextView winClose;
    private TextView winAgain;
    private ProgressBar progressBar;
    private View boardCard;
    private View genOverlay;
    private View winOverlay;
    private View winCard;

    private final TextView[] actionButtons = new TextView[5];
    private Toast toast;

    private boolean generating = false;
    private boolean winShown = false;
    private int lastMistakes = 0;
    private int lastProgress = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        game = new SudokuGame();

        board = findViewById(R.id.board);
        board.setGame(game);
        board.setOnCellSelectedListener(cell -> {
            game.select(cell);
            board.invalidate();
            updateNumberPad();
            save();
        });

        numberPad = findViewById(R.id.numberPad);
        numberPad.setOnDigitListener(this::onDigit);

        segmented = findViewById(R.id.difficulty);
        segmented.setOnSegmentSelectedListener(this::confirmNewGame);

        timerView = findViewById(R.id.timer);
        mistakesView = findViewById(R.id.mistakes);
        checkToggle = findViewById(R.id.checkToggle);
        progressText = findViewById(R.id.progressText);
        progressBar = findViewById(R.id.progress);
        boardCard = findViewById(R.id.boardCard);
        genOverlay = findViewById(R.id.genOverlay);
        winOverlay = findViewById(R.id.winOverlay);
        winCard = findViewById(R.id.winCard);
        winStats = findViewById(R.id.winStats);
        winClose = findViewById(R.id.winClose);
        winAgain = findViewById(R.id.winAgain);
        confetti = findViewById(R.id.confetti);

        actionButtons[0] = findViewById(R.id.actionUndo);
        actionButtons[1] = findViewById(R.id.actionErase);
        actionButtons[2] = findViewById(R.id.actionNotes);
        actionButtons[3] = findViewById(R.id.actionHint);
        actionButtons[4] = findViewById(R.id.actionNew);

        for (TextView b : actionButtons) {
            attachPressAnimation(b);
        }
        attachPressAnimation(checkToggle);
        attachPressAnimation(winClose);
        attachPressAnimation(winAgain);

        GradientDrawable playPill = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{COLOR_PRIMARY, COLOR_ACCENT});
        playPill.setCornerRadius(dp(12));
        winAgain.setBackground(playPill);

        setupActionButtons();

        checkToggle.setOnClickListener(v -> {
            game.toggleAutoCheck();
            save();
            refreshAll();
            toast(game.isAutoCheck() ? "AUTO-CHECK ON" : "AUTO-CHECK OFF");
        });

        winClose.setOnClickListener(v -> hideWin());
        winAgain.setOnClickListener(v -> {
            hideWin();
            showDifficultyDialog();
        });

        boolean restored = restore();
        winShown = game.isSolved();
        lastMistakes = game.getMistakes();
        if (restored) {
            segmented.setSelected(game.getDifficulty(), false);
            setInputsEnabled(true);
            refreshAll();
            if (winShown) {
                winOverlay.setVisibility(View.VISIBLE);
            }
        } else {
            startNewGame(SudokuGenerator.EASY);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        game.resume();
        handler.post(ticker);
        refreshAll();
    }

    @Override
    protected void onPause() {
        super.onPause();
        game.pause();
        handler.removeCallbacks(ticker);
        save();
    }

    private void setupActionButtons() {
        actionButtons[0].setOnClickListener(v -> doUndo());
        actionButtons[1].setOnClickListener(v -> doErase());
        actionButtons[2].setOnClickListener(v -> {
            game.toggleNoteMode();
            save();
            refreshAll();
            toast(game.isNoteMode() ? "PENCIL MARKS ON" : "PENCIL MARKS OFF");
        });
        actionButtons[3].setOnClickListener(v -> doHint());
        actionButtons[4].setOnClickListener(v -> showDifficultyDialog());
    }

    private void onDigit(int digit) {
        if (generating || !game.hasPuzzle() || game.isSolved()) {
            return;
        }
        int cell = game.getSelected();
        if (cell < 0) {
            toast("TAP A CELL FIRST");
            return;
        }
        if (game.isGiven(cell)) {
            toast("THAT'S A GIVEN CLUE");
            return;
        }
        boolean noteMode = game.isNoteMode();
        if (game.input(digit)) {
            board.onGameEvent(cell, noteMode
                    ? SudokuBoardView.EVENT_NOTE
                    : SudokuBoardView.EVENT_INPUT);
            if (!noteMode && game.isWrong(cell)) {
                board.onGameEvent(cell, SudokuBoardView.EVENT_WRONG);
            }
            save();
            refreshAll();
            checkWin();
        }
    }

    private void doUndo() {
        if (generating || !game.undo()) {
            return;
        }
        if (winShown && !game.isSolved()) {
            hideWin();
            winShown = false;
        }
        board.onGameEvent(-1, SudokuBoardView.EVENT_UNDO);
        save();
        refreshAll();
    }

    private void doErase() {
        if (generating) {
            return;
        }
        int cell = game.getSelected();
        if (game.erase()) {
            board.onGameEvent(cell, SudokuBoardView.EVENT_ERASE);
            save();
            refreshAll();
        }
    }

    private void doHint() {
        if (generating || !game.hint()) {
            return;
        }
        board.onGameEvent(game.getSelected(), SudokuBoardView.EVENT_HINT);
        save();
        refreshAll();
        checkWin();
    }

    private void confirmNewGame(int difficulty) {
        if (game.hasPuzzle() && !game.isSolved() && game.elapsedMs() > 1000) {
            new AlertDialog.Builder(this)
                    .setTitle("New " + SudokuGame.DIFFICULTY_NAMES[difficulty] + " game?")
                    .setMessage("Your current progress will be lost.")
                    .setPositiveButton("Start", (dialog, which) -> startNewGame(difficulty))
                    .setNegativeButton("Cancel", (dialog, which) ->
                            segmented.setSelected(game.getDifficulty(), false))
                    .setOnCancelListener(dialog ->
                            segmented.setSelected(game.getDifficulty(), false))
                    .show();
        } else {
            startNewGame(difficulty);
        }
    }

    private void showDifficultyDialog() {
        String[] items = {"Easy  ·  40 clues", "Medium  ·  33 clues", "Hard  ·  27 clues"};
        new AlertDialog.Builder(this)
                .setTitle("New game")
                .setItems(items, (dialog, which) -> startNewGame(which))
                .show();
    }

    private void startNewGame(final int difficulty) {
        if (generating) {
            return;
        }
        generating = true;
        winShown = false;
        hideWin();
        genOverlay.setVisibility(View.VISIBLE);
        setInputsEnabled(false);
        segmented.setSelected(difficulty, true);
        timerView.setText("···");

        new Thread(() -> {
            final SudokuGenerator.Puzzle puzzle =
                    new SudokuGenerator().generate(difficulty);
            runOnUiThread(() -> {
                if (isFinishing()) {
                    return;
                }
                generating = false;
                genOverlay.setVisibility(View.GONE);
                game.newGame(puzzle);
                winShown = false;
                lastMistakes = 0;
                lastProgress = -1;
                setInputsEnabled(true);
                board.reveal();
                save();
                refreshAll();

                boardCard.setScaleX(0.92f);
                boardCard.setScaleY(0.92f);
                boardCard.setAlpha(0f);
                boardCard.animate()
                        .scaleX(1f).scaleY(1f).alpha(1f)
                        .setDuration(460)
                        .setInterpolator(new OvershootInterpolator(0.8f))
                        .start();
            });
        }).start();
    }

    private void checkWin() {
        if (!game.isSolved() || winShown) {
            return;
        }
        winShown = true;
        board.onGameEvent(-1, SudokuBoardView.EVENT_SOLVED);
        winStats.setText(String.format(Locale.US,
                "DIFFICULTY     %s%nTIME                %s%nHINTS USED     %d%nMISTAKES         %d",
                SudokuGame.DIFFICULTY_NAMES[game.getDifficulty()].toUpperCase(Locale.US),
                formatTime(game.elapsedMs()), game.getHints(), game.getMistakes()));
        winOverlay.setVisibility(View.VISIBLE);
        confetti.burst();
        winCard.setScaleX(0.7f);
        winCard.setScaleY(0.7f);
        winCard.setAlpha(0f);
        winCard.animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(520)
                .setInterpolator(new OvershootInterpolator(1.1f))
                .start();
        save();
    }

    private void hideWin() {
        winOverlay.setVisibility(View.GONE);
        confetti.setVisibility(View.GONE);
    }

    private void refreshAll() {
        board.invalidate();
        updateStatus();
        updateNumberPad();
        updateActionButtons();
    }

    private void updateStatus() {
        if (generating) {
            timerView.setText("···");
            return;
        }
        timerView.setText(formatTime(game.elapsedMs()));

        int mistakes = game.getMistakes();
        mistakesView.setText(game.isAutoCheck()
                ? "MISTAKES  " + mistakes : "MISTAKES  –");
        if (mistakes > lastMistakes) {
            flashMistakes();
        }
        lastMistakes = mistakes;

        boolean auto = game.isAutoCheck();
        checkToggle.setText(auto ? "AUTO-CHECK  ON" : "AUTO-CHECK  OFF");
        checkToggle.setTextColor(auto ? COLOR_ACCENT : COLOR_MUTED);
        GradientDrawable chip = new GradientDrawable();
        chip.setColor(auto ? COLOR_BTN_ON : COLOR_BTN);
        chip.setCornerRadius(dp(12));
        chip.setStroke(Math.max(1, dp(1)), auto ? COLOR_PRIMARY : COLOR_STROKE);
        checkToggle.setBackground(chip);

        int filled = game.getFilledCount();
        progressText.setText(filled + "/81");
        if (filled != lastProgress) {
            if (lastProgress < 0) {
                progressBar.setProgress(filled);
            } else {
                ObjectAnimator anim = ObjectAnimator.ofInt(progressBar, "progress",
                        lastProgress, filled);
                anim.setDuration(320);
                anim.setInterpolator(new DecelerateInterpolator(1.4f));
                anim.start();
            }
            lastProgress = filled;
        }
    }

    private void flashMistakes() {
        mistakesView.setTextColor(COLOR_WRONG);
        mistakesView.animate().scaleX(1.28f).scaleY(1.28f).setDuration(130)
                .withEndAction(() -> mistakesView.animate()
                        .scaleX(1f).scaleY(1f).setDuration(220).start())
                .start();
        handler.postDelayed(() -> mistakesView.setTextColor(COLOR_MUTED), 650);
    }

    private void updateNumberPad() {
        int[] rem = new int[9];
        for (int d = 1; d <= 9; d++) {
            rem[d - 1] = game.remaining(d);
        }
        int active = 0;
        int sel = game.getSelected();
        if (sel >= 0) {
            active = game.getValue(sel);
        }
        numberPad.setData(rem, active);
        numberPad.setEnabledState(!generating && game.hasPuzzle() && !game.isSolved());
    }

    private void updateActionButtons() {
        boolean notesOn = game.isNoteMode();
        for (int i = 0; i < actionButtons.length; i++) {
            styleAction(actionButtons[i], i == 2 && notesOn);
        }
        actionButtons[0].setAlpha(game.canUndo() ? 1f : 0.42f);
    }

    private void styleAction(TextView b, boolean active) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(active ? COLOR_BTN_ON : COLOR_BTN);
        d.setCornerRadius(dp(12));
        d.setStroke(Math.max(1, dp(1)), active ? COLOR_PRIMARY : COLOR_STROKE);
        b.setBackground(d);
        b.setTextColor(active ? COLOR_TEXT : COLOR_MUTED);
        tintTopIcon(b, active ? COLOR_ACCENT : COLOR_MUTED);
    }

    private void tintTopIcon(TextView b, int color) {
        Drawable[] ds = b.getCompoundDrawables();
        if (ds != null && ds.length > 1 && ds[1] != null) {
            Drawable top = ds[1].mutate();
            top.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            b.setCompoundDrawables(ds[0], top, ds[2], ds[3]);
        }
    }

    private void attachPressAnimation(View v) {
        v.setOnTouchListener((view, ev) -> {
            int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90).start();
            } else if (action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_CANCEL) {
                view.animate().scaleX(1f).scaleY(1f)
                        .setDuration(300)
                        .setInterpolator(new OvershootInterpolator(2f))
                        .start();
            }
            return false;
        });
    }

    private void setInputsEnabled(boolean enabled) {
        checkToggle.setEnabled(enabled);
        segmented.setEnabledState(enabled);
        board.setInteractive(enabled);
        for (TextView b : actionButtons) {
            b.setEnabled(enabled);
            b.setAlpha(enabled ? 1f : 0.45f);
        }
        numberPad.setEnabledState(enabled);
    }

    // ---- hardware keyboard -------------------------------------------------

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!generating && game.hasPuzzle() && !game.isSolved()) {
            if (keyCode >= KeyEvent.KEYCODE_1 && keyCode <= KeyEvent.KEYCODE_9) {
                onDigit(keyCode - KeyEvent.KEYCODE_1 + 1);
                return true;
            }
            if (keyCode >= KeyEvent.KEYCODE_NUMPAD_1
                    && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
                onDigit(keyCode - KeyEvent.KEYCODE_NUMPAD_1 + 1);
                return true;
            }
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    moveSelection(-1, 0);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    moveSelection(1, 0);
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    moveSelection(0, -1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    moveSelection(0, 1);
                    return true;
                case KeyEvent.KEYCODE_DEL:
                    doErase();
                    return true;
                case KeyEvent.KEYCODE_N:
                case KeyEvent.KEYCODE_SPACE:
                    actionButtons[2].performClick();
                    return true;
                case KeyEvent.KEYCODE_H:
                    doHint();
                    return true;
                case KeyEvent.KEYCODE_U:
                    doUndo();
                    return true;
                case KeyEvent.KEYCODE_Z:
                    if (event.isCtrlPressed()) {
                        doUndo();
                        return true;
                    }
                    break;
                default:
                    break;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void moveSelection(int dr, int dc) {
        int cur = game.getSelected();
        if (cur < 0) {
            game.select(40);
        } else {
            int r = (cur / 9 + dr + 9) % 9;
            int c = (cur % 9 + dc + 9) % 9;
            game.select(r * 9 + c);
        }
        board.invalidate();
        updateNumberPad();
        save();
    }

    // ---- persistence / helpers --------------------------------------------

    private void save() {
        if (game.hasPuzzle()) {
            prefs.edit().putString(KEY_STATE, game.serialize()).apply();
        }
    }

    private boolean restore() {
        return game.restore(prefs.getString(KEY_STATE, null));
    }

    private void toast(String message) {
        if (toast != null) {
            toast.cancel();
        }
        toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        toast.show();
    }

    private static String formatTime(long millis) {
        long total = millis / 1000;
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long seconds = total % 60;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private int dp(float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
