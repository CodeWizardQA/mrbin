package io.github.codewizardqa.mrbin.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import java.io.File;

public final class SettingsStore {
    private final SharedPreferences prefs;

    /** Folder created in Download for the games by default (R.string.app_folder). */
    private final String defaultDestName;

    public SettingsStore(Context context, String defaultDestName) {
        this.prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        this.defaultDestName = defaultDestName;
    }

    private File getDefaultRoot() {
        try {
            File ext = Environment.getExternalStorageDirectory();
            if (ext != null) return ext;
        } catch (Throwable ignored) {
        }
        return new File("/storage/emulated/0");
    }

    public String getSourceDirPath() {
        String fallback = new File(getDefaultRoot(), "Download").getAbsolutePath();
        return prefs.getString("source_dir", fallback);
    }

    public void setSourceDirPath(String path) {
        prefs.edit().putString("source_dir", path).apply();
    }

    public String getDestDirPath() {
        String fallback = new File(new File(getDefaultRoot(), "Download"), defaultDestName).getAbsolutePath();
        return prefs.getString("dest_dir", fallback);
    }

    public void setDestDirPath(String path) {
        prefs.edit().putString("dest_dir", path).apply();
    }

    /** The permissions screen of the first start stays until the user taps Continue or grants all three. */
    public boolean isSetupDone() {
        return prefs.getBoolean("setup_done", false);
    }

    public void setSetupDone(boolean done) {
        prefs.edit().putBoolean("setup_done", done).apply();
    }

    public boolean isNotchCapsuleEnabled() {
        return prefs.getBoolean("notch_capsule", true);
    }

    public void setNotchCapsuleEnabled(boolean on) {
        prefs.edit().putBoolean("notch_capsule", on).apply();
    }
}
