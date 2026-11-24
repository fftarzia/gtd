package com.fftarzia.gtddo.quickcapture;

import static org.junit.Assert.*;
import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Render real Android Views with native Skia, without a browser approximation. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 37, qualifiers = "w390dp-h844dp-night-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CaptureScreenshotTest {
    @Before public void configuredWithoutRealCredentials() {
        Application app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("quick_capture_settings", Context.MODE_PRIVATE).edit().clear()
                .putString("server_url", "https://tasks.example.com")
                .putString("api_token_encrypted", "test-only-placeholder-not-a-secret").commit();
    }
    private void render(String filename, int width, int height, int maximumHeight) throws Exception {
        try (var controller = Robolectric.buildActivity(QuickCaptureActivity.class).setup()) {
            var activity = controller.get();
            CapturePanel capture = activity.findViewById(R.id.root);
            ViewGroup originalParent = (ViewGroup) capture.getParent();
            ViewGroup.LayoutParams originalParams = capture.getLayoutParams();
            originalParent.removeView(capture);
            capture.setMaximumHeight(maximumHeight);
            EditText editor = capture.findViewById(R.id.inputNote);
            editor.setText("Chiamare il commercialista per i documenti.");
            editor.setCursorVisible(false);
            FrameLayout frame = new FrameLayout(activity);
            frame.setBackgroundColor(activity.getColor(R.color.gtd_background));
            frame.addView(capture, new FrameLayout.LayoutParams(width - 32,
                    FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            frame.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            frame.layout(0, 0, width, height);
            if (maximumHeight < height) {
                editor.requestFocus();
                editor.bringPointIntoView(editor.length());
            }
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            frame.draw(new Canvas(bitmap));
            File directory = new File("build/reports/native-screenshots");
            assertTrue(directory.isDirectory() || directory.mkdirs());
            try (FileOutputStream output = new FileOutputStream(new File(directory, filename + ".png"))) {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
            }
            View save = capture.findViewById(R.id.buttonSaveNote);
            assertTrue("Primary action must remain readable", save.getWidth() > 100);
            assertTrue("Primary action must not extend beyond the content", save.getRight() <= ((View) save.getParent()).getWidth());
            assertFalse(editor.isSaveEnabled());
            int[] location = new int[2];
            save.getLocationInWindow(location);
            assertTrue("Primary action must remain inside the visible panel", location[1] + save.getHeight() <= height);
            frame.removeView(capture);
            originalParent.addView(capture, originalParams);
        }
    }
    @Test public void captureDark() throws Exception { render("capture-dark", 390, 844, 844); }
    @Test @Config(qualifiers = "w390dp-h844dp-notnight-mdpi")
    public void captureLight() throws Exception { render("capture-light", 390, 844, 844); }
    @Test public void captureLargeText() throws Exception {
        RuntimeEnvironment.setFontScale(1.5f);
        render("capture-large-text", 390, 844, 844);
    }
    @Test @Config(qualifiers = "w320dp-h480dp-night-mdpi")
    public void captureNarrowWithKeyboardSpace() throws Exception { render("capture-narrow", 320, 480, 320); }
}
