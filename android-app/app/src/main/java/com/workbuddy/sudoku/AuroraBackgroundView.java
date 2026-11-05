package com.workbuddy.sudoku;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * A slowly drifting aurora gradient with soft light orbs and a faint star field.
 * Time based so it never allocates animator objects and can be paused cheaply
 * when the view is detached.
 */
public final class AuroraBackgroundView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[][] stars = new float[46][3];

    private LinearGradient baseGradient;
    private long origin;
    private boolean attached;
    private int w, h;

    private static final int[] ORB_COLORS = {
            Color.rgb(124, 92, 255),   // violet
            Color.rgb(45, 212, 191),   // teal
            Color.rgb(255, 107, 157)   // pink
    };
    private static final int[] ORB_ALPHA = {74, 58, 44};

    public AuroraBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        for (int i = 0; i < stars.length; i++) {
            stars[i][0] = (float) Math.random();
            stars[i][1] = (float) Math.random();
            stars[i][2] = 0.6f + (float) Math.random() * 1.5f;
        }
        starPaint.setColor(Color.argb(70, 190, 205, 255));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        origin = SystemClock.uptimeMillis();
        postInvalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) {
            postInvalidate();
        }
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldw, int oldh) {
        super.onSizeChanged(width, height, oldw, oldh);
        w = width;
        h = height;
        baseGradient = new LinearGradient(0, 0, 0, height,
                new int[]{0xFF0A0E1F, 0xFF0C1026, 0xFF070A16},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (baseGradient == null || w == 0 || h == 0) {
            return;
        }
        float t = (SystemClock.uptimeMillis() - origin) / 1000f;

        // Base vertical gradient.
        paint.setShader(baseGradient);
        paint.setAlpha(255);
        canvas.drawRect(0, 0, w, h, paint);

        // Star field.
        paint.setShader(null);
        for (float[] s : stars) {
            float twinkle = 0.45f + 0.55f * (float) Math.abs(Math.sin(t * 0.7f + s[0] * 30f));
            starPaint.setAlpha((int) (70 * twinkle));
            canvas.drawCircle(s[0] * w, s[1] * h, s[2], starPaint);
        }

        // Drifting light orbs.
        float[][] centers = {
                {0.18f + 0.10f * (float) Math.sin(t * 0.23f),
                        0.16f + 0.07f * (float) Math.cos(t * 0.19f)},
                {0.84f + 0.09f * (float) Math.cos(t * 0.17f + 1.3f),
                        0.42f + 0.10f * (float) Math.sin(t * 0.21f)},
                {0.34f + 0.12f * (float) Math.sin(t * 0.15f + 2.4f),
                        0.88f + 0.07f * (float) Math.cos(t * 0.26f)}
        };
        float[] radii = {Math.max(w, h) * 0.62f, Math.max(w, h) * 0.52f,
                Math.max(w, h) * 0.48f};

        for (int i = 0; i < 3; i++) {
            float cx = centers[i][0] * w;
            float cy = centers[i][1] * h;
            float r = radii[i];
            int c = ORB_COLORS[i];
            int a = ORB_ALPHA[i];
            int center = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c));
            RadialGradient glow = new RadialGradient(cx, cy, r,
                    center, Color.TRANSPARENT, Shader.TileMode.CLAMP);
            paint.setShader(glow);
            paint.setAlpha(255);
            canvas.drawRect(cx - r, cy - r, cx + r, cy + r, paint);
        }
        paint.setShader(null);

        if (attached && getWindowVisibility() == VISIBLE) {
            postInvalidateDelayed(40);   // ~25 fps, cheap enough for a gradient
        }
    }
}
