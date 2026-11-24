package com.fftarzia.gtddo.quickcapture;

import static org.junit.Assert.*;
import android.app.Application;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.EditText;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {35, 37})
public class AndroidEntryPointsTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();

    @Test public void sharesheetResolvesGtdDoForTextAndBrowserLinks() {
        Application app = RuntimeEnvironment.getApplication();
        Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "https://example.com");
        assertTrue(app.getPackageManager().queryIntentActivities(share, PackageManager.MATCH_DEFAULT_ONLY)
                .stream().anyMatch(info -> info.activityInfo.name.endsWith("QuickCaptureActivity")));
        assertEquals("GTDdo", app.getString(R.string.app_name));
    }
    @Test public void sharedSubjectIsPreservedWithoutRepeatingIt() {
        assertEquals("Un articolo\n\nhttps://example.com", SharedCapture.combine("Un articolo", "https://example.com"));
        assertEquals("Un articolo\nhttps://example.com", SharedCapture.combine("Un articolo", "Un articolo\nhttps://example.com"));
        assertEquals("  testo\noriginale  ", SharedCapture.combine(null, "  testo\noriginale  "));
    }
    @Test public void styledTextIsAcceptedAndAttachmentsAreNotMisrepresented() {
        Intent shared = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, new android.text.SpannableString("Un testo"));
        assertEquals("Un testo", SharedCapture.from(shared));
        assertNull(SharedCapture.from(new Intent(Intent.ACTION_SEND).setType("image/png")));
    }
    @Test public void nativeCaptureShowsSharedTextWithoutPlaintextSavedState() {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), QuickCaptureActivity.class)
                .setAction(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "  Nota condivisa\ncon seconda riga  ");
        try (var controller = Robolectric.buildActivity(QuickCaptureActivity.class, intent).setup()) {
            EditText editor = controller.get().findViewById(R.id.inputNote);
            assertEquals("  Nota condivisa\ncon seconda riga  ", editor.getText().toString());
            assertFalse(editor.isSaveEnabled());
            assertEquals("Salva nella Inbox", ((android.widget.TextView) controller.get().findViewById(R.id.buttonSaveNote)).getText().toString());
        }
    }
}
