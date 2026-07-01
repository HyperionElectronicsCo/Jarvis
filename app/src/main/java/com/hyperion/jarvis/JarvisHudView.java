package com.hyperion.jarvis;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

public class JarvisHudView extends View {
    public static final int MODE_IDLE = 0;
    public static final int MODE_LISTENING = 1;
    public static final int MODE_SPEAKING = 2;

    private Paint paint;
    private Paint textPaint;
    private RectF arcBounds;
    private Random random;
    private int mode;
    private float audioLevel;
    private float density;
    private float[] particleX;
    private float[] particleY;
    private float[] particleSpeed;
    private float[] particleSize;
    private float[] particleDrift;
    private boolean particlesReady;

    public JarvisHudView(Context context) {
        super(context);
        init(context);
    }

    public JarvisHudView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arcBounds = new RectF();
        random = new Random();
        mode = MODE_IDLE;
        audioLevel = 0.0f;
        particleX = new float[110];
        particleY = new float[110];
        particleSpeed = new float[110];
        particleSize = new float[110];
        particleDrift = new float[110];
        particlesReady = false;
        setBackgroundColor(Color.rgb(2, 7, 11));
    }

    public void setMode(int newMode) {
        mode = newMode;
        invalidate();
    }

    public void setAudioLevel(float level) {
        if (level < 0.0f) {
            level = 0.0f;
        }
        if (level > 1.0f) {
            level = 1.0f;
        }
        audioLevel = level;
        invalidate();
    }

    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        buildParticles(width, height);
    }

    private void buildParticles(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        for (int i = 0; i < particleX.length; i++) {
            particleX[i] = random.nextInt(width);
            particleY[i] = random.nextInt(height);
            particleSpeed[i] = dp(0.35f + random.nextFloat() * 1.25f);
            particleSize[i] = dp(0.8f + random.nextFloat() * 2.0f);
            particleDrift[i] = random.nextFloat() * 6.28f;
        }
        particlesReady = true;
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        if (!particlesReady) {
            buildParticles(width, height);
        }

        float time = SystemClock.uptimeMillis() / 1000.0f;
        drawBackgroundGrid(canvas, width, height, time);
        drawParticles(canvas, width, height, time);
        drawScanLines(canvas, width, height, time);
        drawCore(canvas, width, height, time);
        drawSpectrum(canvas, width, height, time);
        drawCornerFrame(canvas, width, height, time);
        drawHudLabels(canvas, width, height, time);

        postInvalidateDelayed(16);
    }

    private void drawBackgroundGrid(Canvas canvas, int width, int height, float time) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(2, 7, 11));
        canvas.drawRect(0, 0, width, height, paint);

        float spacing = dp(38.0f);
        float offset = (time * dp(18.0f)) % spacing;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.0f));
        paint.setColor(Color.argb(34, 0, 216, 255));
        for (float x = -spacing + offset; x < width + spacing; x += spacing) {
            canvas.drawLine(x, 0, x, height, paint);
        }
        for (float y = -spacing + offset; y < height + spacing; y += spacing) {
            canvas.drawLine(0, y, width, y, paint);
        }

        paint.setStrokeWidth(dp(1.2f));
        paint.setColor(Color.argb(42, 255, 157, 46));
        for (int i = 0; i < 7; i++) {
            float yLine = (height * 0.18f) + i * dp(58.0f);
            canvas.drawLine(width * 0.08f, yLine, width * 0.92f, yLine + dp(18.0f), paint);
        }
    }

    private void drawParticles(Canvas canvas, int width, int height, float time) {
        if (!particlesReady) {
            return;
        }
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < particleX.length; i++) {
            particleY[i] += particleSpeed[i] + (audioLevel * dp(1.5f));
            float wobble = (float) Math.sin(time * 1.2f + particleDrift[i]) * dp(0.8f);
            if (particleY[i] > height + dp(8.0f)) {
                particleY[i] = -dp(8.0f);
                particleX[i] = random.nextInt(width);
            }
            int alpha = 36 + (int) (70.0f * audioLevel);
            if (i % 5 == 0) {
                paint.setColor(Color.argb(alpha, 255, 158, 43));
            } else {
                paint.setColor(Color.argb(alpha, 0, 216, 255));
            }
            canvas.drawCircle(particleX[i] + wobble, particleY[i], particleSize[i], paint);
        }
    }

    private void drawScanLines(Canvas canvas, int width, int height, float time) {
        float lineHeight = dp(2.0f);
        float y = (time * dp(145.0f)) % height;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(78, 0, 216, 255));
        canvas.drawRect(0, y, width, y + lineHeight, paint);
        paint.setColor(Color.argb(18, 0, 216, 255));
        canvas.drawRect(0, y + lineHeight, width, y + dp(44.0f), paint);

        paint.setColor(Color.argb(18, 255, 255, 255));
        for (float yy = 0; yy < height; yy += dp(4.0f)) {
            canvas.drawRect(0, yy, width, yy + 1, paint);
        }
    }

    private void drawCore(Canvas canvas, int width, int height, float time) {
        float cx = width / 2.0f;
        float cy = height * 0.46f;
        float base = Math.min(width, height) * 0.18f;
        float pulse = (float) Math.sin(time * 3.0f) * dp(4.0f) + audioLevel * dp(18.0f);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);

        for (int i = 0; i < 6; i++) {
            float radius = base + i * dp(18.0f) + pulse * (i * 0.12f);
            int alpha = 155 - i * 18;
            if (alpha < 35) {
                alpha = 35;
            }
            if (i % 2 == 0) {
                paint.setColor(Color.argb(alpha, 0, 216, 255));
            } else {
                paint.setColor(Color.argb(alpha, 255, 158, 43));
            }
            paint.setStrokeWidth(dp(1.7f + (i % 3)));
            arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius);
            canvas.drawCircle(cx, cy, radius, paint);

            float sign = i % 2 == 0 ? 1.0f : -1.0f;
            float start = (time * (28.0f + i * 11.0f) * sign) % 360.0f;
            float sweep = 54.0f + i * 22.0f;
            canvas.drawArc(arcBounds, start, sweep, false, paint);
            canvas.drawArc(arcBounds, start + 180.0f, sweep * 0.62f, false, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(34, 0, 216, 255));
        canvas.drawCircle(cx, cy, base * 0.72f + pulse, paint);
        paint.setColor(Color.argb(80, 0, 216, 255));
        canvas.drawCircle(cx, cy, base * 0.42f + pulse * 0.35f, paint);
        paint.setColor(Color.argb(180, 0, 216, 255));
        canvas.drawCircle(cx, cy, base * 0.13f + pulse * 0.22f, paint);

        drawRadialCircuit(canvas, cx, cy, base, time);
    }

    private void drawRadialCircuit(Canvas canvas, float cx, float cy, float base, float time) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.5f));
        for (int i = 0; i < 24; i++) {
            double angle = Math.toRadians(i * 15.0f + time * 10.0f);
            float inner = base * 0.55f;
            float outer = base * (1.08f + (i % 3) * 0.12f);
            float x1 = cx + (float) Math.cos(angle) * inner;
            float y1 = cy + (float) Math.sin(angle) * inner;
            float x2 = cx + (float) Math.cos(angle) * outer;
            float y2 = cy + (float) Math.sin(angle) * outer;
            if (i % 4 == 0) {
                paint.setColor(Color.argb(132, 255, 158, 43));
            } else {
                paint.setColor(Color.argb(102, 0, 216, 255));
            }
            canvas.drawLine(x1, y1, x2, y2, paint);
            if (i % 3 == 0) {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(x2, y2, dp(2.3f), paint);
                paint.setStyle(Paint.Style.STROKE);
            }
        }
    }

    private void drawSpectrum(Canvas canvas, int width, int height, float time) {
        float centerX = width / 2.0f;
        float bottom = height * 0.76f;
        float barWidth = dp(5.0f);
        float gap = dp(5.0f);
        int bars = 34;
        float total = bars * barWidth + (bars - 1) * gap;
        float startX = centerX - total / 2.0f;
        float strength = 0.18f;
        if (mode == MODE_LISTENING) {
            strength = 0.55f + audioLevel * 0.8f;
        } else if (mode == MODE_SPEAKING) {
            strength = 0.75f + ((float) Math.sin(time * 12.0f) + 1.0f) * 0.15f;
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(barWidth);
        for (int i = 0; i < bars; i++) {
            float wave = (float) Math.sin(time * 5.0f + i * 0.55f);
            float distance = Math.abs(i - bars / 2.0f) / (bars / 2.0f);
            float heightFactor = (1.0f - distance * 0.55f);
            float barHeight = dp(12.0f) + (wave + 1.0f) * dp(18.0f) * strength * heightFactor;
            float x = startX + i * (barWidth + gap) + barWidth / 2.0f;
            int alpha = 95 + (int) (130.0f * strength * heightFactor);
            paint.setColor(Color.argb(alpha, 0, 216, 255));
            canvas.drawLine(x, bottom - barHeight, x, bottom + barHeight * 0.28f, paint);
        }
    }

    private void drawCornerFrame(Canvas canvas, int width, int height, float time) {
        float margin = dp(18.0f);
        float length = dp(62.0f);
        float glow = 0.5f + ((float) Math.sin(time * 2.0f) + 1.0f) * 0.25f;
        int alpha = 120 + (int) (95.0f * glow);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2.0f));
        paint.setStrokeCap(Paint.Cap.SQUARE);
        paint.setColor(Color.argb(alpha, 0, 216, 255));

        canvas.drawLine(margin, margin, margin + length, margin, paint);
        canvas.drawLine(margin, margin, margin, margin + length, paint);
        canvas.drawLine(width - margin, margin, width - margin - length, margin, paint);
        canvas.drawLine(width - margin, margin, width - margin, margin + length, paint);
        canvas.drawLine(margin, height - margin, margin + length, height - margin, paint);
        canvas.drawLine(margin, height - margin, margin, height - margin - length, paint);
        canvas.drawLine(width - margin, height - margin, width - margin - length, height - margin, paint);
        canvas.drawLine(width - margin, height - margin, width - margin, height - margin - length, paint);
    }

    private void drawHudLabels(Canvas canvas, int width, int height, float time) {
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setTextSize(dp(20.0f));
        textPaint.setColor(Color.argb(210, 0, 216, 255));
        canvas.drawText("J.A.R.V.I.S.", width / 2.0f, height * 0.46f + dp(7.0f), textPaint);

        textPaint.setFakeBoldText(false);
        textPaint.setTextSize(dp(9.5f));
        textPaint.setColor(Color.argb(140, 255, 158, 43));
        canvas.drawText("VISUAL NEURAL INTERFACE", width / 2.0f, height * 0.46f + dp(25.0f), textPaint);

        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(dp(8.5f));
        textPaint.setColor(Color.argb(130, 0, 216, 255));
        canvas.drawText("ARC MATRIX", dp(24.0f), height - dp(36.0f), textPaint);
        canvas.drawText("VOICE CORE ONLINE", dp(24.0f), height - dp(22.0f), textPaint);

        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("NO LAMBDA JAVA HUD", width - dp(24.0f), height - dp(36.0f), textPaint);
        canvas.drawText("AIDE READY", width - dp(24.0f), height - dp(22.0f), textPaint);
    }

    private float dp(float value) {
        return value * density;
    }
}
