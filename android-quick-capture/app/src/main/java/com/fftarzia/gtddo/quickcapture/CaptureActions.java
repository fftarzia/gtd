package com.fftarzia.gtddo.quickcapture;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Space;

/** Keeps both actions readable with large fonts or a narrow floating window. */
public final class CaptureActions extends LinearLayout {
    private boolean stacked;
    public CaptureActions(Context context, AttributeSet attrs) { super(context, attrs); }
    @Override protected void onMeasure(int width, int height) {
        boolean shouldStack = getResources().getConfiguration().fontScale >= 1.3f
                || MeasureSpec.getSize(width) < 300 * getResources().getDisplayMetrics().density;
        if (shouldStack != stacked) {
            stacked = shouldStack;
            setOrientation(stacked ? VERTICAL : HORIZONTAL);
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child instanceof Space) child.setVisibility(stacked ? GONE : VISIBLE);
                else {
                    LayoutParams params = (LayoutParams) child.getLayoutParams();
                    params.width = stacked ? LayoutParams.MATCH_PARENT : LayoutParams.WRAP_CONTENT;
                    child.setLayoutParams(params);
                }
            }
        }
        super.onMeasure(width, height);
    }
}
