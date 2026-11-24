package com.fftarzia.gtddo.quickcapture;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** A user-installable Quick Settings tile (not a PWA shortcut). */
public final class QuickNoteTileService extends TileService {
    @Override
    public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();
        if (tile != null) {
            // This is an action, not a toggle permanently switched on.
            tile.setState(Tile.STATE_INACTIVE);
            tile.setLabel(getString(R.string.tile_label));
            tile.setContentDescription(getString(R.string.open_capture));
            if (Build.VERSION.SDK_INT >= 29) tile.setSubtitle(getString(R.string.quick_note_title));
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_quick_note));
            tile.updateTile();
        }
    }

    @Override
    public void onClick() {
        super.onClick();
        if (isLocked()) {
            unlockAndRun(this::openCapture);
        } else {
            openCapture();
        }
    }

    private void openCapture() {
        Intent intent = new Intent(this, QuickCaptureActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 1001, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            startActivityAndCollapse(pendingIntent);
        } else {
            // Legacy overload works on Android 8-13 only.
            openLegacyCapture(intent);
        }
    }

    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    private void openLegacyCapture(Intent intent) {
        // Called only on API 26–33, where the PendingIntent overload does not exist.
        // Android 14+ always takes the modern branch above.
        startActivityAndCollapse(intent);
    }
}
