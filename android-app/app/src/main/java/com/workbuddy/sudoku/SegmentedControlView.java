package com.workbuddy.sudoku;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Animated 3-way segmented control with a sliding gradient indicator. */
public final class SegmentedControlView extends View {

    public interface OnSegmentSelectedListener {
        void onSegmentSelected(int index);
    }

    private static final String[] LABELS = {"EASY", "MEDIUM", "HARD"};

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float indicatorPos = 0f;
    private int selected = 0;
    private ValueAnimator animator;
    private OnSegmentSelectedListener listener;
    private boolean enabledState = true;

    public SegmentedControlView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        setClickable(true);
        trackPaint.setColor(0xFF141B33);
        trackPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        indicatorPaint.setStyle(Paint.Style.FILL);
        glowPaint.setStyle(Paint.Style.FILL);
    }

    public void setOnSegmentSelectedListener(OnSegmentSelectedListener l) {
        this.listener = l;
    }

    public void setSelected(int index, boolean animate) {
        if (index < 0 || index > 2) {
            return;
        }
        selected = index;
        if (animator != null) {
            animator.cancel();
        }
        if (!animate) {
            indicatorPos = index;
            invalidate();
            return;
        }
        float from = indicatorPos;
        animator = ValueAnimator.ofFloat(from, index);
        animator.setDuration(320);
        animator.setInterpolator(new DecelerateInterpolator(1.8f));
        animator.addUpdateListener(a -> {
            indicatorPos = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public void setEnabledState(boolean enabled) {
        enabledState = enabled;
        setAlpha(enabled ? 1f : 0.5f);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float pad = dp(4);
        float radius = dp(12);

        // Track.
        trackPaint.setShader(null);
        canvas.drawRoundRect(0, 0, w, h, radius, radius, trackPaint);

        float segW = (w - 2 * pad) / 3f;
        float left = pad + indicatorPos * segW + dp(2);
        float right = pad + (indicatorPos + 1) * segW - dp(2);
        float top = pad + dp(2);
        float bottom = h - pad - dp(2);
        float ir = dp(9);

        // Soft glow behind the indicator.
        glowPaint.setColor(0x2A7C5CFF);
        canvas.drawRoundRect(left - dp(3), top - dp(3), right + dp(3),
                bottom + dp(3), ir + dp(3), ir + dp(3), glowPaint);

        // Gradient pill.
        indicatorPaint.setShader(new LinearGradient(left, 0, right, 0,
                new int[]{0xFF7C5CFF, 0xFF5F8BFF, 0xFF2DD4BF},
                null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(left, top, right, bottom, ir, ir, indicatorPaint);
        indicatorPaint.setShader(null);

        // Labels.
        float textSize = sp(11);
        textPaint.setTextSize(textSize);
        float baseline = h / 2f - (textPaint.ascent() + textPaint.descent()) / 2f;
        for (int i = 0; i < 3; i++) {
            float cx = pad + (i + 0.5f) * segW;
            boolean on = i == selected;
            textPaint.setColor(on ? 0xFFFFFFFF : 0xFF8B98C2);
            textPaint.setAlpha(on ? 255 : (int) (255 * getAlpha()));
            canvas.drawText(LABELS[i], cx, baseline, textPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && enabledState) {
            int index = (int) (event.getX() / (getWidth() / 3f));
            if (index >= 0 && index <= 2 && index != selected) {
                setSelected(index, true);
                if (listener != null) {
                    listener.onSegmentSelected(index);
                }
            }
            performClick();
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
