package com.example.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.R;

public class DonutProgressView extends View {
    private static final float START_ANGLE = -90f;
    private static final int DEFAULT_SIZE_DP = 120;

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();

    private float progress = 0f;
    private float strokeWidth;
    private float textSize;

    public DonutProgressView(Context context) {
        super(context);
        init();
    }

    public DonutProgressView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DonutProgressView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        strokeWidth = dpToPx(12);
        textSize = spToPx(24);

        backgroundPaint.setStyle(Paint.Style.STROKE);
        backgroundPaint.setStrokeWidth(strokeWidth);
        backgroundPaint.setStrokeCap(Paint.Cap.ROUND);
        backgroundPaint.setColor(ContextCompat.getColor(getContext(), R.color.app_surface_subtle));

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(strokeWidth);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(ContextCompat.getColor(getContext(), R.color.app_primary));

        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setColor(ContextCompat.getColor(getContext(), R.color.app_text_primary));
        textPaint.setTextSize(textSize);
    }

    public void setProgress(int progress) {
        setProgress((float) progress);
    }

    public void setProgress(float progress) {
        float clampedProgress = Math.max(0f, Math.min(100f, progress));
        if (this.progress == clampedProgress) {
            return;
        }
        this.progress = clampedProgress;
        invalidate();
    }

    public float getProgress() {
        return progress;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredSize = (int) dpToPx(DEFAULT_SIZE_DP);
        int measuredWidth = resolveSize(desiredSize, widthMeasureSpec);
        int measuredHeight = resolveSize(desiredSize, heightMeasureSpec);
        int size = Math.min(measuredWidth, measuredHeight);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float halfStroke = strokeWidth / 2f;
        arcBounds.set(
                getPaddingLeft() + halfStroke,
                getPaddingTop() + halfStroke,
                getWidth() - getPaddingRight() - halfStroke,
                getHeight() - getPaddingBottom() - halfStroke
        );

        canvas.drawArc(arcBounds, 0f, 360f, false, backgroundPaint);
        canvas.drawArc(arcBounds, START_ANGLE, progress * 3.6f, false, progressPaint);

        String percentText = Math.round(progress) + "%";
        float availableTextWidth = Math.max(0f, getWidth() - getPaddingLeft() - getPaddingRight() - strokeWidth * 2f);
        float originalTextSize = textSize;
        float measuredTextWidth = textPaint.measureText(percentText);
        if (measuredTextWidth > availableTextWidth && availableTextWidth > 0f) {
            textPaint.setTextSize(originalTextSize * availableTextWidth / measuredTextWidth);
        }

        Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
        float centerY = getHeight() / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f;
        canvas.drawText(percentText, getWidth() / 2f, centerY, textPaint);
        textPaint.setTextSize(originalTextSize);
    }

    private float dpToPx(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    private float spToPx(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                value,
                getResources().getDisplayMetrics()
        );
    }
}
