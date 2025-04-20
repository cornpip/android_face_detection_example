package com.example.face_detection;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.*;

public class FaceBoxOverlayView extends View {
    private final Paint boxPaint;
    private final List<RectF> faceBoundingBoxes = new ArrayList<>();  // RectF로 변경

    public FaceBoxOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);

        boxPaint = new Paint();
        boxPaint.setColor(Color.GREEN);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(5f);
    }

    public void setFaceBoundingBoxes(List<RectF> boxes) {  // RectF 리스트로 변경
        faceBoundingBoxes.clear();
        faceBoundingBoxes.addAll(boxes);
        invalidate(); // 다시 그리기 요청
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (RectF rect : faceBoundingBoxes) {
            canvas.drawRect(rect, boxPaint);  // RectF로 변경된 부분
        }
    }
}