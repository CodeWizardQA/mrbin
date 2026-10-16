package io.github.codewizardqa.mrbin;

import android.app.Application;
import android.content.pm.PackageManager;

import io.github.codewizardqa.mrbin.engine.PkgConverterEngine;
import io.github.codewizardqa.mrbin.engine.SettingsStore;

public final class App extends Application {
    public SettingsStore settings;
    public PkgConverterEngine engine;
    /** versionName of the APK; scripts/build-apk.sh is the only place where it changes. */
    public String version = "?";

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        settings = new SettingsStore(this, getString(R.string.app_folder));
        engine = new PkgConverterEngine(this, settings, new java.io.File(getFilesDir(), "log.txt"), version);
    }
}
