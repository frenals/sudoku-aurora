package com.workbuddy.sudoku;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import java.util.ArrayList;
import java.util.Random;

/** One-shot confetti particle burst used on the win screen. */
public final class ConfettiView extends View {

    private static final int[] PALETTE = {
            0xFF7C5CFF, 0xFF2DD4BF, 0xFFFF6B9D,
            0xFFFFD166, 0xFF5F8BFF, 0xFFEAF0FF
    };
    private static final long DURATION_MS = 3600;

    private static final class Particle {
        float x, y, vx, vy, rot, vrot, size, delay;
        int color, shape;
        boolean launched;
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<Particle> particles = new ArrayList<>();
    private final Random random = new Random();
    private boolean active;
    private long start;
    private long lastFrame;

    public ConfettiView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
    }

    public void burst() {
        particles.clear();
        int w = getWidth();
        int h = getHeight();
        for (int i = 0; i < 140; i++) {
            Particle p = new Particle();
            p.x = random.nextFloat() * w;
            p.y = -dp(24) - random.nextFloat() * h * 0.7f;
            p.vx = (random.nextFloat() - 0.5f) * dp(110);
            p.vy = dp(160) + random.nextFloat() * dp(260);
            p.size = dp(4) + random.nextFloat() * dp(7);
            p.rot = random.nextFloat() * 360f;
            p.vrot = (random.nextFloat() - 0.5f) * 520f;
            p.color = PALETTE[random.nextInt(PALETTE.length)];
            p.shape = random.nextInt(3);
            p.delay = random.nextFloat() * 0.55f;
            particles.add(p);
        }
        start = SystemClock.uptimeMillis();
        lastFrame = 0;
        active = true;
        setVisibility(VISIBLE);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!active) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float elapsed = (now - start) / 1000f;

        for (Particle p : particles) {
            float age = elapsed - p.delay;
            if (age < 0) {
                continue;
            }
            if (!p.launched) {
                p.launched = true;
            }
            p.vy += dp(300) * dt;                 // gravity
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.rot += p.vrot * dt;
            p.vx *= (1f - 0.35f * dt);            // gentle air drag

            int alpha = 255;
            if (age > 2.3f) {
                alpha = (int) (255 * Math.max(0f, 1f - (age - 2.3f) / 1.2f));
            }
            if (alpha <= 0 || p.y > getHeight() + dp(30)) {
                continue;
            }
            paint.setColor(Color.argb(alpha, Color.red(p.color),
                    Color.green(p.color), Color.blue(p.color)));
            canvas.save();
            canvas.translate(p.x, p.y);
            canvas.rotate(p.rot);
            if (p.shape == 0) {
                canvas.drawRect(-p.size / 2f, -p.size / 4f,
                        p.size / 2f, p.size / 4f, paint);
            } else if (p.shape == 1) {
                canvas.drawCircle(0, 0, p.size / 2f, paint);
            } else {
                canvas.drawRect(-p.size / 4f, -p.size / 2f,
                        p.size / 4f, p.size / 2f, paint);
            }
            canvas.restore();
        }

        if (now - start < DURATION_MS) {
            postInvalidateOnAnimation();
        } else {
            active = false;
            setVisibility(GONE);
        }
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        return false;   // let touches pass through to the game
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
