package com.fftarzia.gtddo.quickcapture;

import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public final class MainActivity extends AppCompatActivity {
    private SecureSettings settings;
    private SettingsViewModel model;
    private EditText serverInput, tokenInput, idInput, secretInput;
    private MaterialSwitch useCloudflare;
    private final ActivityResultLauncher<String> exportFile = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/octet-stream"), uri -> model.writeExport(uri));
    private final ActivityResultLauncher<String[]> importFile = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> model.readImport(uri));

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_main);
        InsetUtil.apply(findViewById(R.id.root));
        settings = new SecureSettings(this);
        model = new ViewModelProvider(this).get(SettingsViewModel.class);
        serverInput = findViewById(R.id.inputServerUrl);
        tokenInput = findViewById(R.id.inputApiToken);
        idInput = findViewById(R.id.inputCfClientId);
        secretInput = findViewById(R.id.inputCfClientSecret);
        useCloudflare = findViewById(R.id.useCloudflare);
        useCloudflare.setOnCheckedChangeListener((button, checked) ->
                findViewById(R.id.cloudflareFields).setVisibility(checked ? View.VISIBLE : View.GONE));
        if (model.form == null) {
            serverInput.setText(settings.getServerUrl());
            useCloudflare.setChecked(settings.hasCloudflareCredentials());
        } else {
            serverInput.setText(model.form[0]); tokenInput.setText(model.form[1]);
            idInput.setText(model.form[2]); secretInput.setText(model.form[3]);
            useCloudflare.setChecked(Boolean.parseBoolean(model.form[4]));
        }
        findViewById(R.id.buttonOpenCapture).setOnClickListener(v -> startActivity(new Intent(this, QuickCaptureActivity.class)));
        findViewById(R.id.buttonConfigure).setOnClickListener(v -> {
            model.formExpanded = !model.formExpanded;
            findViewById(R.id.configurationForm).setVisibility(model.formExpanded ? View.VISIBLE : View.GONE);
        });
        findViewById(R.id.buttonSaveSettings).setOnClickListener(v -> saveConfiguration());
        findViewById(R.id.buttonTestConnection).setOnClickListener(v -> model.verify(null, false));
        findViewById(R.id.buttonAddTile).setOnClickListener(v -> requestTile());
        findViewById(R.id.buttonTileHelp).setOnClickListener(v -> tileHelp());
        findViewById(R.id.buttonExport).setOnClickListener(v -> passwordDialog(true));
        findViewById(R.id.buttonImport).setOnClickListener(v -> importFile.launch(new String[]{"*/*"}));
        model.state.observe(this, this::renderState);
        refreshConfiguration();
    }
    private void refreshConfiguration() {
        boolean configured = settings.isConfigured();
        ((TextView) findViewById(R.id.connectionState)).setText(configured ? R.string.configured : R.string.not_configured);
        ((TextView) findViewById(R.id.serverState)).setText(settings.getServerUrl());
        ((MaterialButton) findViewById(R.id.buttonConfigure)).setText(configured ? R.string.configure : R.string.configure_first);
        findViewById(R.id.configurationForm).setVisibility(model.formExpanded ? View.VISIBLE : View.GONE);
        findViewById(R.id.buttonTestConnection).setVisibility(configured ? View.VISIBLE : View.GONE);
        findViewById(R.id.buttonExport).setVisibility(configured ? View.VISIBLE : View.GONE);
    }
    private void renderState(SettingsViewModel.State state) {
        ((TextView) findViewById(R.id.statusText)).setText(state.message);
        for (int id : new int[]{R.id.buttonImport, R.id.buttonExport, R.id.buttonSaveSettings,
                R.id.buttonTestConnection, R.id.buttonConfigure, R.id.inputServerUrl, R.id.inputApiToken,
                R.id.inputCfClientId, R.id.inputCfClientSecret, R.id.useCloudflare}) findViewById(id).setEnabled(!model.busy);
        if (state.kind == SettingsViewModel.Kind.SAVED) {
            tokenInput.setText(""); idInput.setText(""); secretInput.setText("");
            serverInput.setText(settings.getServerUrl());
            useCloudflare.setChecked(settings.hasCloudflareCredentials());
            model.form = null;
            model.formExpanded = false;
            refreshConfiguration();
        } else if (state.kind == SettingsViewModel.Kind.EXPORT_READY && !model.exportPickerOpen) {
            model.exportPickerOpen = true;
            exportFile.launch("GTDdo-configurazione.gtddo");
        } else if (state.kind == SettingsViewModel.Kind.PASSWORD_NEEDED && !model.dialogOpen) passwordDialog(false);
        else if (state.kind == SettingsViewModel.Kind.IMPORT_READY && !model.dialogOpen) confirmImport();
    }
    private AppConfiguration candidate() throws Exception {
        String url = EndpointUrl.normalize(serverInput.getText().toString());
        boolean sameServer = url.equals(settings.getServerUrl());
        String token = tokenInput.getText().toString().trim();
        if (token.isEmpty()) {
            if (!sameServer || !settings.hasToken()) throw new IllegalArgumentException("Inserisci il token personale per questo server.");
            token = settings.readToken();
        }
        CloudflareCredentials cloudflare = new CloudflareCredentials("", "");
        if (useCloudflare.isChecked()) {
            String id = idInput.getText().toString();
            String secret = secretInput.getText().toString();
            if (id.trim().isEmpty() && secret.trim().isEmpty()) {
                if (!sameServer || !settings.hasCloudflareCredentials())
                    throw new IllegalArgumentException("Inserisci entrambe le credenziali Cloudflare per questo server.");
                cloudflare = settings.readCloudflareCredentials();
            } else cloudflare = new CloudflareCredentials(id, secret);
        }
        return new AppConfiguration(url, token, cloudflare);
    }
    private void saveConfiguration() {
        try {
            AppConfiguration config = candidate();
            if (!settings.getServerUrl().isEmpty() && !config.serverUrl.equals(settings.getServerUrl())) {
                new MaterialAlertDialogBuilder(this).setTitle(R.string.change_server_title)
                        .setMessage(getString(R.string.change_server_message, config.serverUrl))
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.continue_action, (d, w) -> model.verify(config, true)).show();
            } else if (settings.hasCloudflareCredentials() && !config.cloudflare.isConfigured()) {
                new MaterialAlertDialogBuilder(this).setTitle(R.string.disable_access_title)
                        .setMessage(R.string.disable_access_message).setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.continue_action, (d, w) -> model.verify(config, true)).show();
            } else model.verify(config, true);
        } catch (Exception error) {
            model.info(error instanceof IllegalArgumentException ? error.getMessage()
                    : "Credenziali salvate non leggibili. Reinseriscile senza disinstallare l’app.");
        }
    }
    private void passwordDialog(boolean exporting) {
        if (model.busy || model.dialogOpen) return;
        model.dialogOpen = true;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        form.setPadding(padding, padding / 2, padding, 0);
        TextInputEditText first = passwordField(form, R.string.backup_password);
        TextInputEditText second = exporting ? passwordField(form, R.string.backup_password_confirm) : null;
        TextView helper = new TextView(this);
        helper.setText(R.string.backup_password_help); form.addView(helper);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(exporting ? R.string.export_title : R.string.import_title).setView(form)
                .setNegativeButton(R.string.cancel, (d, w) -> { if (!exporting) model.cancelImport(); })
                .setPositiveButton(R.string.continue_action, null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String password = first.getText() == null ? "" : first.getText().toString();
            if (password.length() < 12 || password.length() > 256) { first.setError("Utilizza da 12 a 256 caratteri."); return; }
            if (exporting && (second.getText() == null || !password.equals(second.getText().toString()))) {
                second.setError("Le password non coincidono."); return;
            }
            char[] chars = password.toCharArray();
            first.setText(""); if (second != null) second.setText("");
            dialog.dismiss();
            if (exporting) model.prepareExport(chars); else model.decryptImport(chars);
        }));
        dialog.setOnCancelListener(d -> { if (!exporting) model.cancelImport(); });
        dialog.setOnDismissListener(d -> model.dialogOpen = false);
        dialog.show();
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
    private TextInputEditText passwordField(LinearLayout form, int hint) {
        TextInputLayout field = new TextInputLayout(this);
        field.setHint(hint); field.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        TextInputEditText input = new TextInputEditText(field.getContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setSingleLine(true); input.setSaveEnabled(false); input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        field.addView(input); form.addView(field);
        return input;
    }
    private void confirmImport() {
        AppConfiguration config = model.imported;
        if (config == null) return;
        model.dialogOpen = true;
        AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle(R.string.import_confirm_title)
                .setMessage(getString(R.string.import_confirm_message, config.serverUrl,
                        getString(config.cloudflare.isConfigured() ? R.string.enabled : R.string.disabled)))
                .setNegativeButton(R.string.cancel, (d, w) -> model.cancelImport())
                .setPositiveButton(R.string.import_confirm, (d, w) -> model.verify(config, true)).create();
        dialog.setOnDismissListener(d -> model.dialogOpen = false);
        dialog.setOnCancelListener(d -> model.cancelImport());
        dialog.show();
    }
    private void tileHelp() {
        new MaterialAlertDialogBuilder(this).setTitle(R.string.tile_help).setMessage(R.string.tile_help_message)
                .setPositiveButton(R.string.ok, null).show();
    }
    private void requestTile() {
        if (Build.VERSION.SDK_INT < 33) { tileHelp(); return; }
        StatusBarManager bar = getSystemService(StatusBarManager.class);
        if (bar == null) { tileHelp(); return; }
        try {
            bar.requestAddTileService(new ComponentName(this, QuickNoteTileService.class), getString(R.string.tile_label),
                    Icon.createWithResource(this, R.drawable.ic_quick_note), getMainExecutor(), result -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED
                                || result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                            model.info("GTDdo è disponibile nelle Impostazioni rapide. Usa Modifica per portarlo accanto a Wi-Fi e torcia.");
                        } else tileHelp();
                    });
        } catch (RuntimeException error) { tileHelp(); }
    }
    @Override protected void onPause() {
        model.form = new String[]{serverInput.getText().toString(), tokenInput.getText().toString(),
                idInput.getText().toString(), secretInput.getText().toString(), Boolean.toString(useCloudflare.isChecked())};
        super.onPause();
    }
    @Override protected void onDestroy() {
        if (isChangingConfigurations()) model.dialogOpen = false;
        super.onDestroy();
    }
}
