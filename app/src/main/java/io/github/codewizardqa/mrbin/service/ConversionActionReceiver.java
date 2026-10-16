package io.github.codewizardqa.mrbin.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import io.github.codewizardqa.mrbin.App;

public final class ConversionActionReceiver extends BroadcastReceiver {
    public static final String ACTION_TOGGLE_PAUSE = "io.github.codewizardqa.mrbin.TOGGLE_PAUSE";
    public static final String ACTION_CANCEL = "io.github.codewizardqa.mrbin.CANCEL";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        App app = (App) context.getApplicationContext();
        String act = intent.getAction();
        if (ACTION_TOGGLE_PAUSE.equals(act)) {
            boolean cur = app.engine.getProgress().paused;
            app.engine.setPaused(!cur);
        } else if (ACTION_CANCEL.equals(act)) {
            app.engine.cancelConversion();
        }
    }
}
