package com.workbuddy.sudoku;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/**
 * Modern custom-drawn 9x9 board: rounded cells, soft highlight layers,
 * animated selection outline, digit pop-in, wrong-entry shake, staggered
 * reveal for a new puzzle and a sweep of light when the puzzle is solved.
 */
public final class SudokuBoardView extends View {

    public interface OnCellSelectedListener {
        void onCellSelected(int cell);
    }

    public static final int EVENT_INPUT = 1;
    public static final int EVENT_HINT = 2;
    public static final int EVENT_NOTE = 3;
    public static final int EVENT_WRONG = 4;
    public static final int EVENT_ERASE = 5;
    public static final int EVENT_UNDO = 6;
    public static final int EVENT_SOLVED = 7;

    private static final int COLOR_BASE = 0xFF101731;
    private static final int COLOR_SELECTED = 0xFF33437F;
    private static final int COLOR_SAME = 0xFF244A72;
    private static final int COLOR_PEER = 0xFF1A2444;
    private static final int COLOR_CONFLICT = 0xFF4A2036;
    private static final int COLOR_LINE = 0xFF2A3560;
    private static final int COLOR_LINE_BOLD = 0xFF5266A8;
    private static final int COLOR_GIVEN = 0xFFE9EEFF;
    private static final int COLOR_USER = 0xFF63E6D2;
    private static final int COLOR_WRONG = 0xFFFF6B7A;
    private static final int COLOR_NOTE = 0xFF8492BC;
    private static final int COLOR_ACCENT = 0xFF7C5CFF;
    private static final int COLOR_ACCENT_2 = 0xFF2DD4BF;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint notePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final OvershootInterpolator overshoot = new OvershootInterpolator(1.5f);

    private SudokuGame game;
    private OnCellSelectedListener listener;
    private float cellSize;
    private float gridLeft, gridTop;
    private boolean interactive = true;

    private long revealStart = -1;
    private long popStart = -1;
    private int popCell = -1;
    private int popKind = EVENT_INPUT;
    private long shakeStart = -1;
    private int shakeCell = -1;
    private long selectStart = -1;
    private int lastSelected = -1;
    private long winStart = -1;

    public SudokuBoardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_BASE);
        bg.setCornerRadius(dp(16));
        setBackground(bg);
        textPaint.setTextAlign(Paint.Align.CENTER);
        notePaint.setTextAlign(Paint.Align.CENTER);
        borderPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStyle(Paint.Style.STROKE);
        linePaint.setStyle(Paint.Style.STROKE);
        fillPaint.setStyle(Paint.Style.FILL);
        sweepPaint.setStyle(Paint.Style.FILL);
    }

    public void setGame(SudokuGame game) {
        this.game = game;
    }

    public void setOnCellSelectedListener(OnCellSelectedListener listener) {
        this.listener = listener;
    }

    public void setInteractive(boolean value) {
        interactive = value;
    }

    /** Starts the staggered reveal animation for a freshly generated puzzle. */
    public void reveal() {
        revealStart = SystemClock.uptimeMillis();
        popStart = -1;
        shakeStart = -1;
        winStart = -1;
        lastSelected = -1;
        invalidate();
    }

    public void onGameEvent(int cell, int kind) {
        long now = SystemClock.uptimeMillis();
        switch (kind) {
            case EVENT_INPUT:
            case EVENT_HINT:
            case EVENT_NOTE:
                popCell = cell;
                popStart = now;
                popKind = kind;
                break;
            case EVENT_WRONG:
                shakeCell = cell;
                shakeStart = now;
                break;
            case EVENT_ERASE:
            case EVENT_UNDO:
                popStart = -1;
                break;
            case EVENT_SOLVED:
                winStart = now;
                break;
            default:
                break;
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        int mode = MeasureSpec.getMode(heightMeasureSpec);
        int size = (mode == MeasureSpec.UNSPECIFIED || h == 0) ? w : Math.min(w, h);
        if (size <= 0) {
            size = w;
        }
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float pad = dp(3);
        cellSize = (Math.min(w, h) - 2 * pad) / 9f;
        gridLeft = (w - cellSize * 9f) / 2f;
        gridTop = (h - cellSize * 9f) / 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (cellSize <= 0) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        boolean needMore = false;

        if (game == null || !game.hasPuzzle()) {
            drawEmptyGrid(canvas);
            return;
        }

        int selected = game.getSelected();
        if (selected != lastSelected) {
            selectStart = now;
            lastSelected = selected;
        }
        int selectedValue = (selected >= 0) ? game.getValue(selected) : 0;

        // Cell highlight layers.
        float inset = dp(1.5f);
        float cr = dp(6);
        for (int i = 0; i < 81; i++) {
            int color = highlightFor(i, selected, selectedValue);
            if (color == 0) {
                continue;
            }
            float left = gridLeft + (i % 9) * cellSize;
            float top = gridTop + (i / 9) * cellSize;
            int alpha = 255;
            if (i == selected && selectStart > 0) {
                float age = (now - selectStart) / 1000f;
                if (age < 0.24f) {
                    alpha = (int) (255 * (age / 0.24f));
                    needMore = true;
                }
            }
            fillPaint.setColor(color);
            fillPaint.setAlpha(alpha);
            canvas.drawRoundRect(left + inset, top + inset,
                    left + cellSize - inset, top + cellSize - inset, cr, cr, fillPaint);
        }
        fillPaint.setAlpha(255);

        drawGridLines(canvas);

        // Digits & pencil marks.
        for (int i = 0; i < 81; i++) {
            float left = gridLeft + (i % 9) * cellSize;
            float top = gridTop + (i / 9) * cellSize;
            float cx = left + cellSize / 2f;
            float cy = top + cellSize / 2f;
            int value = game.getValue(i);

            float revealAlpha = revealAlpha(i, now);
            if (revealAlpha < 0f) {
                needMore = true;
                continue;   // not revealed yet
            }
            float revealScale = 0.72f + 0.28f * revealAlpha;

            float shakeX = shakeOffset(i, now);
            if (shakeX != 0f) {
                needMore = true;
            }

            if (value != 0) {
                float popScale = 1f;
                float popAlpha = 1f;
                if (i == popCell && popStart > 0) {
                    float age = (now - popStart) / 1000f;
                    float dur = popKind == EVENT_HINT ? 0.5f : 0.34f;
                    if (age < dur) {
                        float p = age / dur;
                        popScale = 0.3f + 0.7f * overshoot.getInterpolation(p);
                        popAlpha = Math.min(1f, p * 3.2f);
                        needMore = true;
                    } else {
                        popStart = -1;
                        popCell = -1;
                    }
                }
                boolean wrong = game.isWrong(i) || game.hasConflict(i);
                drawDigit(canvas, String.valueOf(value), cx + shakeX, cy,
                        cellSize * 0.54f,
                        wrong ? COLOR_WRONG
                                : (game.isGiven(i) ? COLOR_GIVEN : COLOR_USER),
                        game.isGiven(i),
                        revealScale * popScale, revealAlpha * popAlpha);
            } else {
                drawNotes(canvas, game.getNotes(i), left + shakeX, top,
                        revealAlpha);
            }
        }

        // Selection glow ring (expands and fades on selection change).
        if (selected >= 0 && selectStart > 0) {
            float age = (now - selectStart) / 1000f;
            if (age < 0.5f) {
                float p = age / 0.5f;
                float left = gridLeft + (selected % 9) * cellSize;
                float top = gridTop + (selected / 9) * cellSize;
                float grow = dp(2) + p * dp(9);
                glowPaint.setColor(COLOR_ACCENT_2);
                glowPaint.setAlpha((int) (150 * (1f - p)));
                glowPaint.setStrokeWidth(dp(2));
                canvas.drawRoundRect(left - grow, top - grow,
                        left + cellSize + grow, top + cellSize + grow,
                        dp(8) + grow, dp(8) + grow, glowPaint);
                needMore = true;
            }
        }

        // Selection outline with a violet -> teal gradient stroke.
        if (selected >= 0) {
            float left = gridLeft + (selected % 9) * cellSize;
            float top = gridTop + (selected / 9) * cellSize;
            borderPaint.setShader(new LinearGradient(left, top,
                    left + cellSize, top + cellSize,
                    COLOR_ACCENT, COLOR_ACCENT_2, Shader.TileMode.CLAMP));
            borderPaint.setStrokeWidth(dp(2.6f));
            borderPaint.setAlpha(255);
            canvas.drawRoundRect(left + dp(2), top + dp(2),
                    left + cellSize - dp(2), top + cellSize - dp(2),
                    dp(7), dp(7), borderPaint);
            borderPaint.setShader(null);
        }

        // Win: a sweeping band of light plus a pulsing border (settles after 3s).
        if (winStart > 0) {
            float age = (now - winStart) / 1000f;
            if (age < 1.6f) {
                float p = age / 1.6f;
                float y = gridTop + p * cellSize * 9f;
                sweepPaint.setShader(new LinearGradient(0, y - dp(26), 0, y + dp(26),
                        new int[]{0x00000000, 0x552DD4BF, 0x00000000},
                        new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRect(gridLeft, y - dp(26), gridLeft + cellSize * 9f,
                        y + dp(26), sweepPaint);
                sweepPaint.setShader(null);
                needMore = true;
            }
            float pulse = 0.45f + 0.4f * (float) Math.abs(Math.sin(age * 3.2f));
            glowPaint.setColor(COLOR_ACCENT_2);
            glowPaint.setAlpha((int) (255 * pulse));
            glowPaint.setStrokeWidth(dp(3));
            canvas.drawRoundRect(gridLeft, gridTop,
                    gridLeft + cellSize * 9f, gridTop + cellSize * 9f,
                    dp(14), dp(14), glowPaint);
            if (age < 3f) {
                needMore = true;
            } else {
                winStart = -1;   // stop continuous redraw, keep the last frame
            }
        }

        if (needMore) {
            postInvalidateOnAnimation();
        }
    }

    private void drawEmptyGrid(Canvas canvas) {
        drawGridLines(canvas);
    }

    private void drawGridLines(Canvas canvas) {
        float size = cellSize * 9f;
        for (int i = 0; i <= 9; i++) {
            boolean bold = (i % 3 == 0);
            linePaint.setColor(bold ? COLOR_LINE_BOLD : COLOR_LINE);
            linePaint.setStrokeWidth(bold ? dp(2f) : dp(1f));
            float p = i * cellSize;
            canvas.drawLine(gridLeft + p, gridTop, gridLeft + p, gridTop + size,
                    linePaint);
            canvas.drawLine(gridLeft, gridTop + p, gridLeft + size, gridTop + p,
                    linePaint);
        }
    }

    private float revealAlpha(int cell, long now) {
        if (revealStart < 0) {
            return 1f;
        }
        float age = (now - revealStart) / 1000f;
        float delay = ((cell / 9) + (cell % 9)) * 0.022f;
        float p = (age - delay) / 0.3f;
        if (p <= 0f) {
            return -1f;
        }
        return Math.min(1f, p);
    }

    private float shakeOffset(int cell, long now) {
        if (shakeStart < 0 || cell != shakeCell) {
            return 0f;
        }
        float age = (now - shakeStart) / 1000f;
        if (age > 0.42f) {
            shakeStart = -1;
            shakeCell = -1;
            return 0f;
        }
        return (float) Math.sin(age * 48f) * dp(6) * (1f - age / 0.42f);
    }

    private void drawDigit(Canvas canvas, String text, float cx, float cy,
                           float textSize, int color, boolean bold,
                           float scale, float alpha) {
        if (alpha <= 0f) {
            return;
        }
        textPaint.setTextSize(textSize);
        textPaint.setColor(color);
        textPaint.setAlpha((int) (255 * Math.min(1f, alpha)));
        textPaint.setTypeface(bold
                ? Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                : Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        float baseline = cy - (textPaint.ascent() + textPaint.descent()) / 2f;
        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(scale, scale);
        canvas.drawText(text, 0, baseline - cy, textPaint);
        canvas.restore();
        textPaint.setAlpha(255);
    }

    private void drawNotes(Canvas canvas, int mask, float left, float top,
                           float alpha) {
        if (mask == 0 || alpha <= 0f) {
            return;
        }
        notePaint.setTextSize(cellSize * 0.225f);
        notePaint.setColor(COLOR_NOTE);
        notePaint.setAlpha((int) (255 * Math.min(1f, alpha)));
        notePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        float third = cellSize / 3f;
        float offset = -(notePaint.ascent() + notePaint.descent()) / 2f;
        for (int v = 1; v <= 9; v++) {
            if ((mask & (1 << (v - 1))) == 0) {
                continue;
            }
            int r = (v - 1) / 3;
            int c = (v - 1) % 3;
            float x = left + (c + 0.5f) * third;
            float y = top + (r + 0.5f) * third + offset;
            canvas.drawText(String.valueOf(v), x, y, notePaint);
        }
        notePaint.setAlpha(255);
    }

    private int highlightFor(int cell, int selected, int selectedValue) {
        if (game.hasConflict(cell) || game.isWrong(cell)) {
            return COLOR_CONFLICT;
        }
        if (cell == selected) {
            return COLOR_SELECTED;
        }
        if (selected >= 0) {
            if (selectedValue != 0 && game.getValue(cell) == selectedValue) {
                return COLOR_SAME;
            }
            if (sameGroup(cell, selected)) {
                return COLOR_PEER;
            }
        }
        return 0;
    }

    private static boolean sameGroup(int a, int b) {
        int ra = a / 9;
        int ca = a % 9;
        int rb = b / 9;
        int cb = b % 9;
        return ra == rb || ca == cb || (ra / 3 == rb / 3 && ca / 3 == cb / 3);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && interactive
                && game != null && game.hasPuzzle()) {
            int col = (int) ((event.getX() - gridLeft) / cellSize);
            int row = (int) ((event.getY() - gridTop) / cellSize);
            if (col >= 0 && col < 9 && row >= 0 && row < 9) {
                if (listener != null) {
                    listener.onCellSelected(row * 9 + col);
                }
                performClick();
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
