package com.workbuddy.sudoku;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * Interactive number keyboard. Each key springs on press, glows when its digit
 * is selected, dims once all nine copies are placed, and shows the remaining
 * count. Fires haptic feedback and reports digits to the game.
 */
public final class NumberPadView extends View {

    public interface OnDigitListener {
        void onDigit(int digit);
    }

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint digitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint countPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final int[] remaining = new int[9];
    private final long[] bounceStart = new long[9];
    private int activeDigit = 0;
    private int pressedIndex = -1;
    private boolean enabledState = true;
    private OnDigitListener listener;

    private static final int COLOR_KEY = 0xFF1B2444;
    private static final int COLOR_KEY_DONE = 0xFF131A31;
    private static final int COLOR_STROKE = 0xFF33416E;
    private static final int COLOR_TEXT = 0xFFEAF0FF;

    public NumberPadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        setClickable(true);
        for (int i = 0; i < 9; i++) {
            remaining[i] = 9;
            bounceStart[i] = -1;
        }
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(1));
        digitPaint.setTextAlign(Paint.Align.CENTER);
        digitPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        countPaint.setTextAlign(Paint.Align.CENTER);
        countPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        glowPaint.setStyle(Paint.Style.FILL);
    }

    public void setOnDigitListener(OnDigitListener l) {
        this.listener = l;
    }

    public void setData(int[] rem, int activeDigit) {
        System.arraycopy(rem, 0, remaining, 0, 9);
        this.activeDigit = activeDigit;
        invalidate();
    }

    public void setEnabledState(boolean enabled) {
        enabledState = enabled;
        setAlpha(enabled ? 1f : 0.45f);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        float gap = dp(5);
        float segW = w / 9f;
        float kw = segW - gap;
        float kh = h - dp(8);
        float kr = dp(11);
        boolean animating = false;

        for (int i = 0; i < 9; i++) {
            int digit = i + 1;
            int rem = remaining[i];
            boolean active = activeDigit == digit;
            boolean done = rem == 0;
            boolean pressed = pressedIndex == i;

            float scale = 1f;
            if (pressed) {
                scale = 0.86f;
            } else if (bounceStart[i] > 0) {
                float age = (now - bounceStart[i]) / 1000f;
                if (age < 0.42f) {
                    scale = 1f - 0.13f * (float) Math.exp(-age * 9f)
                            * (float) Math.cos(age * 23f);
                    animating = true;
                } else {
                    bounceStart[i] = -1;
                }
            }

            canvas.save();
            canvas.translate((i + 0.5f) * segW, h / 2f);
            canvas.scale(scale, scale);

            float halfW = kw / 2f;
            float halfH = kh / 2f;

            // Glow for the active digit.
            if (active && !done) {
                glowPaint.setColor(0x333D8BFF);
                canvas.drawRoundRect(-halfW - dp(3), -halfH - dp(3),
                        halfW + dp(3), halfH + dp(3), kr + dp(3), kr + dp(3),
                        glowPaint);
            }

            // Key background.
            if (active && !done) {
                bgPaint.setShader(new LinearGradient(-halfW, 0, halfW, 0,
                        new int[]{0xFF7C5CFF, 0xFF5F8BFF, 0xFF2DD4BF},
                        null, Shader.TileMode.CLAMP));
            } else {
                bgPaint.setShader(null);
                bgPaint.setColor(done ? COLOR_KEY_DONE : COLOR_KEY);
            }
            canvas.drawRoundRect(-halfW, -halfH, halfW, halfH, kr, kr, bgPaint);
            bgPaint.setShader(null);

            // Border.
            strokePaint.setColor(active && !done ? 0x66FFFFFF : COLOR_STROKE);
            canvas.drawRoundRect(-halfW, -halfH, halfW, halfH, kr, kr, strokePaint);

            // Big digit.
            digitPaint.setTextSize(sp(22));
            digitPaint.setColor(done ? 0xFF55618C : COLOR_TEXT);
            float baseline = -dp(2) - (digitPaint.ascent() + digitPaint.descent()) / 2f;
            canvas.drawText(String.valueOf(digit), 0, baseline, digitPaint);

            // Remaining count / check mark.
            countPaint.setTextSize(sp(9));
            countPaint.setColor(done ? 0xFF3F8F7E
                    : (active ? 0xFFEAF0FF : 0xFF8B98C2));
            String small = done ? "✓" : rem + " left";
            canvas.drawText(small, 0, halfH - dp(7), countPaint);

            canvas.restore();
        }

        if (animating) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (!enabledState) {
                return true;
            }
            int index = (int) (event.getX() / (getWidth() / 9f));
            if (index >= 0 && index < 9) {
                pressedIndex = index;
                bounceStart[index] = -1;
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (listener != null) {
                    listener.onDigit(index + 1);
                }
                invalidate();
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (pressedIndex >= 0) {
                int i = pressedIndex;
                pressedIndex = -1;
                bounceStart[i] = SystemClock.uptimeMillis();
                performClick();
                invalidate();
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                getResources().getDisplayMetrics());
    }
}
