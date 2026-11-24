package com.fftarzia.gtddo.quickcapture;

import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

/** Keep content clear of Android 15+ edge-to-edge system bars. */
final class InsetUtil {
    private InsetUtil() {}

    static void apply(View root) {
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets system = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(system.left, system.top, system.right, system.bottom);
                return insets;
            });
            root.requestApplyInsets();
        }
    }
}
