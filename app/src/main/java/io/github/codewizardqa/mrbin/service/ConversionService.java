package io.github.codewizardqa.mrbin.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;

import io.github.codewizardqa.mrbin.App;
import io.github.codewizardqa.mrbin.R;
import io.github.codewizardqa.mrbin.engine.PkgModels.ActiveConversionProgress;
import io.github.codewizardqa.mrbin.engine.PkgModels.Formatters;
import io.github.codewizardqa.mrbin.ui.MainActivity;
import io.github.codewizardqa.mrbin.ui.Palette;
import io.github.codewizardqa.mrbin.ui.Texts;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Foreground service that keeps the PKG extraction running in the background, with a progress notification and,
 * when allowed, a capsule with % and time left next to the front camera (top center when there is no cutout).
 */
public final class ConversionService extends Service {
    public static final int FG_ID = 201;
    private static final String CH_PROGRESS = "progress";
    private static final String CH_DONE = "done";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;
    private NotchOverlay notchOverlay;
    private int idleTicks = 0;
    private boolean runningLoop = false;
    private final Map<Integer, Icon> iconCache = new HashMap<>();

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!runningLoop) return;
            App app = (App) getApplication();
            ActiveConversionProgress p = app.engine.getProgress();
            if (p.running) {
                idleTicks = 0;
                acquireWakeLock();
                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm != null) {
                    nm.notify(FG_ID, buildProgressNotification(p));
                }
                if (app.settings.isNotchCapsuleEnabled() && Settings.canDrawOverlays(ConversionService.this)) {
                    if (notchOverlay != null) notchOverlay.update(p);
                } else {
                    if (notchOverlay != null) notchOverlay.hide();
                }
            } else {
                releaseWakeLock();
                if (notchOverlay != null) notchOverlay.hide();
                idleTicks++;
                if (idleTicks >= 6) {
                    if (p.totalItems > 0) {
                        notifyFinished(p);
                    }
                    runningLoop = false;
                    stopForeground(STOP_FOREGROUND_REMOVE);
                    stopSelf();
                    return;
                }
            }
            handler.postDelayed(this, 500L);
        }
    };

    public static void ensureRunning(Context context) {
        try {
            context.startForegroundService(new Intent(context, ConversionService.class));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannels();
        int fgsType = Build.VERSION.SDK_INT >= 34
                ? (ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE | ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                : ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
        Notification initial = baseBuilder()
                .setContentTitle(getString(R.string.notif_starting_title, getString(R.string.app_name)))
                .setContentText(getString(R.string.notif_starting_text))
                .build();
        startForeground(FG_ID, initial, fgsType);
        notchOverlay = new NotchOverlay(this);
        runningLoop = true;
        handler.post(tickRunnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @SuppressLint("WakelockTimeout")
    private void acquireWakeLock() {
        if (wakeLock == null) {
            PowerManager pm = getSystemService(PowerManager.class);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getPackageName() + ":extract");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire();
            }
        }
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Throwable ignored) {
        }
        wakeLock = null;
    }

    @Override
    public void onDestroy() {
        runningLoop = false;
        handler.removeCallbacksAndMessages(null);
        if (notchOverlay != null) {
            notchOverlay.hide();
            notchOverlay = null;
        }
        releaseWakeLock();
        super.onDestroy();
    }

    private void ensureChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel prog = new NotificationChannel(
                CH_PROGRESS,
                getString(R.string.notif_channel_progress),
                NotificationManager.IMPORTANCE_LOW
        );
        prog.setDescription(getString(R.string.notif_channel_progress_desc));
        prog.setShowBadge(false);
        prog.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(prog);

        NotificationChannel done = new NotificationChannel(
                CH_DONE,
                getString(R.string.notif_channel_done),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        nm.createNotificationChannel(done);
    }

    private Notification.Builder baseBuilder() {
        return new Notification.Builder(this, CH_PROGRESS)
                .setSmallIcon(R.drawable.ic_stat_app)
                .setColor(Palette.ACCENT)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(openAppIntent());
    }

    private Notification buildProgressNotification(ActiveConversionProgress p) {
        int pct = p.batchPercentInt();
        String etaStr = Texts.eta(this, p.etaSeconds);
        String left = getString(R.string.notif_left, etaStr);
        String speedStr = Formatters.humanSpeed(p.currentSpeedBps);
        String chip = p.paused ? (pct + "% · " + getString(R.string.paused)) : (pct + "% · " + etaStr);
        String itemName = p.currentItem != null ? p.currentItem.displayName() : getString(R.string.notif_extracting);
        String title = p.currentIndex + "/" + p.totalItems + " · " + itemName;
        String detail = Formatters.humanBytes(p.batchBytesDone) + " / " + Formatters.humanBytes(p.batchBytesTotal)
                + " · " + speedStr + " · " + left;

        String pauseLabel = getString(p.paused ? R.string.resume : R.string.pause);
        Notification.Builder b = baseBuilder()
                .setSmallIcon(statusIcon(pct))
                .setContentTitle(title)
                .setContentText(detail)
                .setProgress(100, pct, false)
                .addAction(new Notification.Action.Builder(
                        null,
                        pauseLabel,
                        actionIntent(ConversionActionReceiver.ACTION_TOGGLE_PAUSE)
                ).build())
                .addAction(new Notification.Action.Builder(
                        null,
                        getString(R.string.cancel),
                        actionIntent(ConversionActionReceiver.ACTION_CANCEL)
                ).build());

        promoteToStatusChip(b, chip, pct + "% · " + left);
        return b.build();
    }

    private void notifyFinished(ActiveConversionProgress p) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        Notification n = new Notification.Builder(this, CH_DONE)
                .setSmallIcon(R.drawable.ic_stat_app)
                .setColor(Palette.ACCENT)
                .setContentTitle(Texts.phase(this, p.phase))
                .setContentText(p.statusDetail)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent())
                .build();
        nm.notify(202, n);
    }

    private Icon statusIcon(int pct) {
        int clamped = Math.max(0, Math.min(100, pct));
        Icon cached = iconCache.get(clamped);
        if (cached != null) return cached;

        int size = 64;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setColor(0x55FFFFFF);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(5f);

        Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arcPaint.setColor(Color.WHITE);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setStrokeWidth(5.5f);

        RectF oval = new RectF(4f, 4f, size - 4f, size - 4f);
        c.drawOval(oval, ringPaint);
        c.drawArc(oval, -90f, 360f * (clamped / 100f), false, arcPaint);

        String label = clamped < 100 ? (clamped + "%") : "100";
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(label.length() <= 2 ? 25f : 20f);

        float y = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f;
        c.drawText(label, size / 2f, y, textPaint);

        Icon icon = Icon.createWithBitmap(bmp);
        iconCache.put(clamped, icon);
        return icon;
    }

    /** Progress chip in the status bar: Android 16 (promoted notification) and HyperOS (focus ticker).
     *  Other systems ignore these extras and show the regular notification. */
    private void promoteToStatusChip(Notification.Builder builder, String chipText, String fullTicker) {
        Bundle extras = new Bundle();
        extras.putBoolean("android.requestPromotedOngoing", true);
        extras.putString("android.shortCriticalText", chipText);
        extras.putString("miui.focus.ticker", fullTicker);
        extras.putBoolean("miui.enableKeyguardTicker", true);
        builder.addExtras(extras);
        builder.setSubText(fullTicker);
        builder.setTicker(fullTicker);
        builder.setCategory(Notification.CATEGORY_PROGRESS);
        builder.setVisibility(Notification.VISIBILITY_PUBLIC);
    }

    private PendingIntent actionIntent(String action) {
        Intent i = new Intent(this, ConversionActionReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(
                this,
                action.hashCode(),
                i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private PendingIntent openAppIntent() {
        Intent i = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(
                this,
                0,
                i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    /**
     * Capsule at the top center that leaves the front-camera cutout free when there is one.
     */
    private static final class NotchOverlay {
        private final Context context;
        private final WindowManager wm;
        private NotchCapsuleView view;
        private boolean attached = false;

        NotchOverlay(Context context) {
            this.context = context;
            this.wm = context.getSystemService(WindowManager.class);
        }

        void update(ActiveConversionProgress p) {
            int pct = p.batchPercentInt();
            String left = pct + "%";
            String right;
            if (p.paused) {
                right = context.getString(R.string.capsule_paused);
            } else if (p.etaSeconds >= 0L) {
                right = Formatters.humanEta(p.etaSeconds);
            } else {
                right = Formatters.humanSpeed(p.currentSpeedBps);
            }
            ensureShown();
            if (view != null) {
                view.setData(left, right, p.batchProgressFloat(), p.paused);
            }
        }

        private void ensureShown() {
            if (view == null) {
                view = new NotchCapsuleView(context);
                view.setOnClickListener(v -> {
                    Intent i = new Intent(context, MainActivity.class)
                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    try {
                        context.startActivity(i);
                    } catch (Throwable ignored) {
                    }
                });
            }
            if (!attached && wm != null) {
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT
                );
                lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                lp.x = 0;
                lp.y = 0;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
                }
                try {
                    wm.addView(view, lp);
                    attached = true;
                } catch (Throwable ignored) {
                }
            }
        }

        void hide() {
            if (attached && view != null && wm != null) {
                try {
                    wm.removeView(view);
                } catch (Throwable ignored) {
                }
            }
            attached = false;
        }
    }

    private static final class NotchCapsuleView extends View {
        private String leftText = "0%";
        private String rightText = "ETA";
        private float progress = 0f;

        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint progPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint leftPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        NotchCapsuleView(Context context) {
            super(context);
            bgPaint.setColor(Color.BLACK);
            bgPaint.setStyle(Paint.Style.FILL);

            borderPaint.setColor((Palette.ACCENT & 0x00FFFFFF) | 0x55000000);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(dp(1f));

            trackPaint.setColor(0x33FFFFFF);
            trackPaint.setStyle(Paint.Style.STROKE);
            trackPaint.setStrokeCap(Paint.Cap.ROUND);
            trackPaint.setStrokeWidth(dp(2.2f));

            progPaint.setColor(Palette.ACCENT);
            progPaint.setStyle(Paint.Style.STROKE);
            progPaint.setStrokeCap(Paint.Cap.ROUND);
            progPaint.setStrokeWidth(dp(2.2f));

            leftPaint.setColor(Palette.HIGHLIGHT);
            leftPaint.setTextSize(sp(11.5f));
            leftPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            leftPaint.setTextAlign(Paint.Align.CENTER);

            rightPaint.setColor(Color.WHITE);
            rightPaint.setTextSize(sp(11f));
            rightPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            rightPaint.setTextAlign(Paint.Align.CENTER);
        }

        private float dp(float v) {
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
        }

        private float sp(float v) {
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
        }

        void setData(String left, String right, float prog, boolean paused) {
            boolean sizeChanged = left.length() != leftText.length() || right.length() != rightText.length();
            leftText = left;
            rightText = right;
            progress = prog;
            leftPaint.setColor(paused ? Palette.COOL : Palette.HIGHLIGHT);
            progPaint.setColor(paused ? Palette.COOL : Palette.ACCENT);
            if (sizeChanged) requestLayout();
            invalidate();
        }

        private int[] cutoutBounds() {
            WindowInsets insets = getRootWindowInsets();
            DisplayCutout cutout = insets != null ? insets.getDisplayCutout() : null;
            Rect topRect = null;
            if (cutout != null) {
                List<Rect> rects = cutout.getBoundingRects();
                for (Rect r : rects) {
                    if (r.top <= (int) dp(20f) && r.width() > 0) {
                        topRect = r;
                        break;
                    }
                }
            }
            int holeW = topRect != null
                    ? Math.max((int) dp(22f), Math.min((int) dp(48f), topRect.width()))
                    : (int) dp(30f);
            int holeH = topRect != null
                    ? Math.max((int) dp(26f), Math.min((int) dp(44f), topRect.bottom))
                    : (int) dp(30f);
            return new int[]{holeW, holeH};
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int[] bounds = cutoutBounds();
            int holeW = bounds[0];
            int holeH = bounds[1];
            float maxText = Math.max(leftPaint.measureText(leftText), rightPaint.measureText(rightText));
            int sideW = (int) Math.max(maxText, dp(50f)) + (int) dp(16f);
            int centerGap = holeW + (int) dp(10f);
            int totalW = sideW * 2 + centerGap;
            int totalH = Math.max(holeH, (int) dp(28f)) + (int) dp(4f);
            setMeasuredDimension(totalW, totalH);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float r = h / 2f;
            int[] bounds = cutoutBounds();
            float centerGap = bounds[0] + dp(10f);
            float sideW = (w - centerGap) / 2f;

            rect.set(0f, 0f, w, h);
            canvas.drawRoundRect(rect, r, r, bgPaint);

            rect.set(dp(0.5f), dp(0.5f), w - dp(0.5f), h - dp(0.5f));
            canvas.drawRoundRect(rect, r, r, borderPaint);

            float barLeft = r * 0.7f;
            float barRight = w - r * 0.7f;
            float barY = h - dp(2.2f);
            if (barRight > barLeft) {
                canvas.drawLine(barLeft, barY, barRight, barY, trackPaint);
                float clamped = Math.max(0f, Math.min(1f, progress));
                float fillRight = barLeft + (barRight - barLeft) * clamped;
                if (fillRight > barLeft) {
                    canvas.drawLine(barLeft, barY, fillRight, barY, progPaint);
                }
            }

            float textY = h / 2f - (leftPaint.descent() + leftPaint.ascent()) / 2f - dp(1f);
            canvas.drawText(leftText, sideW / 2f, textY, leftPaint);
            canvas.drawText(rightText, w - sideW / 2f, textY, rightPaint);
        }
    }
}
