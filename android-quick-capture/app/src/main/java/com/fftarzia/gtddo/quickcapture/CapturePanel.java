package com.fftarzia.gtddo.quickcapture;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

/** Bounds the capture surface to the space left by the keyboard. */
public final class CapturePanel extends LinearLayout {
    private int maximumHeight = Integer.MAX_VALUE;
    public CapturePanel(Context context, AttributeSet attrs) { super(context, attrs); }
    public void setMaximumHeight(int height) {
        if (maximumHeight != height) { maximumHeight = height; requestLayout(); }
    }
    @Override protected void onMeasure(int width, int height) {
        int limit = Math.min(maximumHeight, MeasureSpec.getMode(height) == MeasureSpec.UNSPECIFIED
                ? Integer.MAX_VALUE : MeasureSpec.getSize(height));
        super.onMeasure(width, MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST));
    }
}
