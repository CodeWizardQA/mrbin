package io.github.codewizardqa.mrbin.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import io.github.codewizardqa.mrbin.App;
import io.github.codewizardqa.mrbin.R;
import io.github.codewizardqa.mrbin.engine.PkgConverterEngine;
import io.github.codewizardqa.mrbin.engine.PkgModels.ActiveConversionProgress;
import io.github.codewizardqa.mrbin.engine.PkgModels.ConversionPhase;
import io.github.codewizardqa.mrbin.engine.PkgModels.ConversionResultItem;
import io.github.codewizardqa.mrbin.engine.PkgModels.DiscoveredPkgItem;
import io.github.codewizardqa.mrbin.engine.PkgModels.Formatters;
import io.github.codewizardqa.mrbin.engine.orbis.OrbisPkg;
import io.github.codewizardqa.mrbin.service.ConversionService;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Extracts a PS4 fake PKG (fPKG) into the game folder (eboot.bin, sce_sys, sce_module, …) with the engine ported
 * from shadPS4 and LibOrbisPkg. Two tabs (Extract, Results) and the log behind a small button at the top right.
 * The screen only shows what the engine really read or wrote. Every view is built in code; colors come from
 * {@link Palette} and every text from res/values*\/strings.xml.
 */
public final class MainActivity extends Activity implements PkgConverterEngine.Listener {

    private static final int REQ_SOURCE_TREE = 501;
    private static final int REQ_DEST_TREE = 502;

    private static final int TAB_EXTRACT = 0;
    private static final int TAB_RESULTS = 1;

    private App app;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private int currentTab = TAB_EXTRACT;
    /** The log is a page of its own on top of the current tab; back returns to the tab. */
    private boolean showingLog = false;
    private boolean storageGranted = false;
    private boolean batteryGranted = false;
    private boolean overlayGranted = false;

    private ScrollView contentScroll;
    private LinearLayout contentContainer;
    /** Bar fixed above the tabs with the Extract button, so it is in reach however long the list is. */
    private LinearLayout actionBarBox;
    private LinearLayout actionBar;
    private LinearLayout bottomNav;
    private View bottomNavLine;

    private LinearLayout liveDashboardBox;
    private TextView livePhaseTv;
    private TextView liveTitleTv;
    private TextView livePercentTv;
    private ProgressBar liveProgressBar;
    private TextView liveBytesTv;
    private TextView liveFilesTv;
    private TextView liveEtaTv;
    private TextView liveSpeedTv;
    private TextView liveElapsedTv;
    private TextView livePathLabelTv;
    private TextView livePathTv;
    private TextView liveDetailTv;
    private Button livePauseBtn;
    private boolean lastRunning;
    /** Finished extraction the user hid with "Hide"; the card comes back with the next extraction. */
    private ActiveConversionProgress dismissedProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = (App) getApplication();

        Window w = getWindow();
        if (w != null) {
            w.setStatusBarColor(Palette.BG);
            w.setNavigationBarColor(Palette.SURFACE);
        }

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }

        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(Palette.BG);

        contentScroll = new ScrollView(this);
        contentScroll.setFillViewport(true);
        contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setPadding(dp(18), dp(14), dp(18), dp(18));
        contentScroll.addView(contentContainer);
        rootLayout.addView(contentScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        actionBarBox = new LinearLayout(this);
        actionBarBox.setOrientation(LinearLayout.VERTICAL);
        actionBarBox.setBackgroundColor(Palette.SURFACE);
        View actionBarLine = new View(this);
        actionBarLine.setBackgroundColor(Palette.BORDER);
        actionBarBox.addView(actionBarLine, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        actionBar = new LinearLayout(this);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        actionBar.setGravity(Gravity.CENTER_VERTICAL);
        actionBar.setPadding(dp(18), dp(10), dp(16), dp(10));
        actionBarBox.addView(actionBar,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        actionBarBox.setVisibility(View.GONE);
        rootLayout.addView(actionBarBox,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bottomNavLine = new View(this);
        bottomNavLine.setBackgroundColor(Palette.BORDER);
        rootLayout.addView(bottomNavLine, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setBackgroundColor(Palette.SURFACE);
        bottomNav.setPadding(dp(12), dp(8), dp(12), dp(8));
        rootLayout.addView(bottomNav,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(rootLayout);
    }

    @Override
    protected void onResume() {
        super.onResume();
        app.engine.addListener(this);
        refreshPermissionsState();
        if (storageGranted && !app.engine.getProgress().running) {
            app.engine.scanSourceFolder(null);
        }
        rebuildScreen();
    }

    @Override
    protected void onPause() {
        app.engine.removeListener(this);
        super.onPause();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (showingLog) {
            closeLog();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onStateChanged() {
        uiHandler.post(this::updateOrRebuildUi);
    }

    @Override
    public void onMessage(String message) {
        uiHandler.post(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
    }

    private void refreshPermissionsState() {
        storageGranted = Environment.isExternalStorageManager();
        PowerManager pm = getSystemService(PowerManager.class);
        batteryGranted = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        overlayGranted = Settings.canDrawOverlays(this);
    }

    private void updateOrRebuildUi() {
        // The log page is a snapshot taken when it opens; the engine does not redraw it under the user's finger.
        if (showingLog) return;
        ActiveConversionProgress p = app.engine.getProgress();
        if (storageGranted && currentTab == TAB_EXTRACT && liveDashboardBox != null && p.running && lastRunning) {
            bindLiveProgressViews(p);
        } else {
            rebuildScreen();
        }
    }

    private void openLog() {
        showingLog = true;
        rebuildScreen();
    }

    private void closeLog() {
        showingLog = false;
        rebuildScreen();
    }

    private void rebuildScreen() {
        contentContainer.removeAllViews();
        bottomNav.removeAllViews();
        actionBar.removeAllViews();
        actionBarBox.setVisibility(View.GONE);
        liveDashboardBox = null;
        livePauseBtn = null;
        lastRunning = app.engine.getProgress().running;

        if (!storageGranted) {
            showBottomNav(false);
            buildOnboardingView();
            return;
        }
        if (showingLog) {
            showBottomNav(false);
            buildLogPage();
            return;
        }

        showBottomNav(true);
        int resCount = app.engine.getResults().size();
        addNavTab(TAB_EXTRACT, getString(R.string.tab_extract), Icons.MR_BIN);
        addNavTab(TAB_RESULTS, resCount > 0 ? getString(R.string.tab_results_n, resCount) : getString(R.string.tab_results),
                Icons.CHECK_VERIFIED);

        if (currentTab == TAB_EXTRACT) {
            buildExtractTab();
        } else {
            buildResultsTab();
        }
    }

    private void showBottomNav(boolean show) {
        bottomNav.setVisibility(show ? View.VISIBLE : View.GONE);
        bottomNavLine.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void addNavTab(int tab, String label, int iconType) {
        boolean selected = currentTab == tab;
        LinearLayout tabBox = new LinearLayout(this);
        tabBox.setOrientation(LinearLayout.HORIZONTAL);
        tabBox.setGravity(Gravity.CENTER);
        tabBox.setPadding(dp(10), dp(8), dp(10), dp(8));
        tabBox.setBackground(ripple(rounded(selected ? Palette.SURFACE_2 : Palette.SURFACE, 14,
                selected ? Palette.GLOW_BORDER : 0)));

        ImageView iv = Icons.imageView(this, iconType, 18);
        iv.setAlpha(selected ? 1f : 0.5f);
        tabBox.addView(iv, new LinearLayout.LayoutParams(dp(18), dp(18)));

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTypeface(selected ? Typeface.DEFAULT_BOLD : medium());
        tv.setTextColor(selected ? Palette.ACCENT : Palette.TEXT_3);
        tv.setPadding(dp(8), 0, 0, 0);
        tabBox.addView(tv);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        lp.leftMargin = dp(4);
        lp.rightMargin = dp(4);
        tabBox.setOnClickListener(v -> {
            currentTab = tab;
            rebuildScreen();
        });
        bottomNav.addView(tabBox, lp);
    }

    // ------------------------------------------------------------------------------------------- header

    /** App icon, a title and a line under it; the small log button sits at the right when {@code withLog}. */
    private void addAppHeader(String title, String subtitle, boolean withLog) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(16));

        ImageView iv = new ImageView(this);
        iv.setImageDrawable(getDrawable(R.mipmap.ic_launcher));
        row.addView(iv, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), 0, dp(8), 0);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Palette.TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(t);

        TextView s = new TextView(this);
        s.setText(subtitle);
        s.setTextColor(Palette.TEXT_3);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        col.addView(s);
        row.addView(col);

        if (withLog) {
            View logBtn = iconButton(Icons.LOG_LIST, getString(R.string.log_title));
            logBtn.setOnClickListener(v -> openLog());
            row.addView(logBtn, new LinearLayout.LayoutParams(dp(40), dp(40)));
        }
        contentContainer.addView(row);
    }

    // --------------------------------------------------------------------------------------- onboarding

    private void buildOnboardingView() {
        String appName = getString(R.string.app_name);
        addAppHeader(appName, getString(R.string.onb_subtitle), false);

        LinearLayout card1 = createCard(Palette.GLOW, Palette.GLOW_BORDER);
        addCardTitleWithIcon(card1, getString(R.string.onb_storage_title), Icons.FOLDER_IN);
        addBodyText(card1, getString(R.string.onb_storage_body, appName));
        Button btnStorage = createPrimaryButton(getString(R.string.onb_storage_btn));
        btnStorage.setOnClickListener(v -> requestAllFilesAccess());
        card1.addView(btnStorage, topMarginLp(14));
        contentContainer.addView(card1, bottomMarginLp(12));

        LinearLayout card2 = createCard(Palette.SURFACE, Palette.BORDER);
        addCardTitleWithIcon(card2, getString(R.string.onb_battery_title), Icons.BOLT_TURBO);
        addBodyText(card2, getString(R.string.onb_battery_body));
        Button btnBatt = createSecondaryButton(
                getString(batteryGranted ? R.string.onb_battery_done : R.string.onb_battery_btn),
                batteryGranted ? Icons.CHECK_VERIFIED : 0);
        setEnabledLook(btnBatt, !batteryGranted);
        btnBatt.setOnClickListener(v -> requestIgnoreBatteryOptimizations());
        card2.addView(btnBatt, topMarginLp(12));
        contentContainer.addView(card2, bottomMarginLp(12));

        LinearLayout card3 = createCard(Palette.SURFACE, Palette.BORDER);
        addCardTitleWithIcon(card3, getString(R.string.onb_overlay_title), Icons.CPU_CHIP);
        addBodyText(card3, getString(R.string.onb_overlay_body));
        Button btnOverlay = createSecondaryButton(
                getString(overlayGranted ? R.string.onb_overlay_done : R.string.onb_overlay_btn),
                overlayGranted ? Icons.CHECK_VERIFIED : 0);
        setEnabledLook(btnOverlay, !overlayGranted);
        btnOverlay.setOnClickListener(v -> requestOverlayPermission());
        card3.addView(btnOverlay, topMarginLp(12));
        contentContainer.addView(card3, bottomMarginLp(12));
    }

    // -------------------------------------------------------------------------------------- Extract tab

    private void buildExtractTab() {
        List<DiscoveredPkgItem> items = app.engine.getDiscovered();
        ActiveConversionProgress progress = app.engine.getProgress();
        boolean scanning = app.engine.isScanning();
        boolean idle = !progress.running && !scanning;
        String srcPath = app.settings.getSourceDirPath();
        String dstPath = app.settings.getDestDirPath();

        addAppHeader(getString(R.string.app_name), getString(R.string.header_subtitle, app.version), true);

        // An extraction that runs or just ended goes first: it is what the user opened the app to look at.
        buildLiveConversionCard(progress);

        LinearLayout foldersCard = createCard(Palette.SURFACE, Palette.BORDER);
        addFolderRow(foldersCard, Icons.FOLDER_IN, getString(R.string.source_label), srcPath,
                getString(R.string.source_hint), null, idle,
                () -> FolderPickerDialogs.show(MainActivity.this, getString(R.string.pick_source_title),
                        app.settings.getSourceDirPath(), false, () -> launchSystemTreePicker(REQ_SOURCE_TREE), chosen -> {
                            app.settings.setSourceDirPath(chosen);
                            app.engine.scanSourceFolder(new File(chosen));
                        }));
        addDivider(foldersCard);
        long freeDest = usableSpace(new File(dstPath));
        addFolderRow(foldersCard, Icons.FOLDER_SHIELD, getString(R.string.dest_label), dstPath,
                getString(R.string.dest_hint),
                freeDest >= 0 ? getString(R.string.free_space, Formatters.humanBytes(freeDest)) : null,
                idle,
                () -> FolderPickerDialogs.show(MainActivity.this, getString(R.string.pick_dest_title),
                        app.settings.getDestDirPath(), true, () -> launchSystemTreePicker(REQ_DEST_TREE), chosen -> {
                            app.settings.setDestDirPath(chosen);
                            rebuildScreen();
                        }));
        contentContainer.addView(foldersCard, bottomMarginLp(22));

        addListHeader(scanning ? getString(R.string.list_scanning) : getString(R.string.list_title, items.size()),
                getString(scanning ? R.string.scanning : R.string.scan_again), idle,
                () -> app.engine.scanSourceFolder(new File(app.settings.getSourceDirPath())));

        if (items.isEmpty() && !scanning) {
            LinearLayout emptyCard = createCard(Palette.SURFACE, Palette.BORDER);
            addCardTitleWithIcon(emptyCard, getString(R.string.empty_title), Icons.ALERT_WARN);
            addBodyText(emptyCard, getString(R.string.empty_body));
            contentContainer.addView(emptyCard, bottomMarginLp(12));
        } else {
            for (DiscoveredPkgItem item : items) {
                contentContainer.addView(buildPkgCard(item, idle), bottomMarginLp(10));
            }
        }

        buildActionBar(items, progress, scanning, dstPath);
    }

    /** What is checked and where it goes, next to the Extract button. Hidden while extracting: the card has its own. */
    private void buildActionBar(List<DiscoveredPkgItem> items, ActiveConversionProgress progress, boolean scanning,
                                String dstPath) {
        if (progress.running || items.isEmpty()) return;
        int selectedCount = 0;
        long selectedBytes = 0L;
        for (DiscoveredPkgItem it : items) {
            if (it.selected && it.info.canExtract()) {
                selectedCount++;
                selectedBytes += Math.max(0, it.info.contentBytes);
            }
        }

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, dp(12), 0);
        TextView t = new TextView(this);
        t.setText(selectedCount == 0
                ? getString(R.string.ready_none)
                : getResources().getQuantityString(R.plurals.ready_title, selectedCount, selectedCount));
        t.setTextColor(Palette.TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(t);
        TextView s = new TextView(this);
        s.setText(selectedCount == 0
                ? getString(R.string.ready_hint_none)
                : getString(R.string.ready_hint, Formatters.humanBytes(selectedBytes), Texts.shortPath(this, dstPath)));
        s.setTextColor(Palette.TEXT_3);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        s.setSingleLine(true);
        s.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        s.setPadding(0, dp(2), 0, 0);
        col.addView(s);
        actionBar.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button go = createPrimaryButton(getString(R.string.extract));
        go.setMinWidth(dp(124));
        go.setMinimumWidth(dp(124));
        go.setMinHeight(dp(48));
        go.setMinimumHeight(dp(48));
        go.setPadding(dp(20), dp(8), dp(20), dp(8));
        setEnabledLook(go, !scanning && selectedCount > 0);
        go.setOnClickListener(v -> {
            app.engine.startBatchConversion(() -> ConversionService.ensureRunning(MainActivity.this));
            // The progress card is at the top; bring it into view after the screen is rebuilt.
            contentScroll.post(() -> contentScroll.smoothScrollTo(0, 0));
        });
        actionBar.addView(go, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        actionBarBox.setVisibility(View.VISIBLE);
    }

    /** Section title with a small text button at the right. */
    private void addListHeader(String title, String actionText, boolean actionEnabled, Runnable onAction) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(Palette.TEXT);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(dp(2), 0, dp(8), 0);
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button b = createSmallActionButton(actionText, onAction);
        b.setCompoundDrawablesRelativeWithIntrinsicBounds(Icons.drawable(this, Icons.RADAR_SCAN, 14), null, null, null);
        b.setCompoundDrawablePadding(dp(6));
        setEnabledLook(b, actionEnabled);
        row.addView(b);
        contentContainer.addView(row, bottomMarginLp(10));
    }

    private static long usableSpace(File dir) {
        File d = dir;
        while (d != null && !d.exists()) d = d.getParentFile();
        return d == null ? -1 : d.getUsableSpace();
    }

    private View buildPkgCard(DiscoveredPkgItem item, boolean enabled) {
        OrbisPkg.Info info = item.info;
        boolean ok = info.canExtract();
        boolean marked = ok && item.selected;
        GradientDrawable cardBg = rounded(Palette.SURFACE, 18, marked ? Palette.ACCENT : Palette.BORDER);
        LinearLayout card = createCard(cardBg);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);

        CheckBox cb = new CheckBox(this);
        cb.setChecked(item.selected);
        cb.setEnabled(enabled && ok);
        cb.setButtonTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{Palette.ACCENT, Palette.TEXT_3}));
        if (!ok) cb.setAlpha(0.35f);
        cb.setOnClickListener(v -> app.engine.toggleSelection(item.id));
        row.addView(cb);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(6), dp(2), 0, 0);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout topLine = new LinearLayout(this);
        topLine.setOrientation(LinearLayout.HORIZONTAL);
        topLine.setGravity(Gravity.TOP);
        TextView nameTv = new TextView(this);
        nameTv.setText(item.displayName());
        nameTv.setTextColor(Palette.TEXT);
        nameTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        nameTv.setTypeface(Typeface.DEFAULT_BOLD);
        nameTv.setMaxLines(2);
        nameTv.setEllipsize(TextUtils.TruncateAt.END);
        nameTv.setPadding(0, 0, dp(8), 0);
        nameTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        topLine.addView(nameTv);
        topLine.addView(createChip(Texts.kindBadge(this, info.kind),
                ok ? Palette.SUCCESS : Palette.DANGER, ok ? Palette.SUCCESS_BG : Palette.DANGER_BG));
        col.addView(topLine);

        // Release names are long ("Game.of.the.Year.Edition.PS4-GROUP.pkg"): one line, cut in the middle.
        TextView fileTv = new TextView(this);
        fileTv.setText(item.pkgFile.getName());
        fileTv.setTextColor(Palette.TEXT_3);
        fileTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        fileTv.setSingleLine(true);
        fileTv.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        fileTv.setPadding(0, dp(4), 0, 0);
        col.addView(fileTv);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, dp(8), 0, 0);
        if (!info.category.isEmpty()) addMetaChip(chips, Texts.category(this, info.category));
        if (!info.appVersion.isEmpty()) addMetaChip(chips, getString(R.string.meta_version, info.appVersion));
        addMetaChip(chips, Formatters.humanBytes(info.pkgSize));
        col.addView(chips);

        if (!info.contentId.isEmpty()) {
            TextView idTv = new TextView(this);
            idTv.setText(info.contentId);
            idTv.setTextColor(Palette.TEXT_3);
            idTv.setTypeface(Typeface.MONOSPACE);
            idTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            idTv.setSingleLine(true);
            idTv.setEllipsize(TextUtils.TruncateAt.END);
            idTv.setPadding(0, dp(8), 0, 0);
            col.addView(idTv);
        }

        if (ok && info.fileCount >= 0) {
            TextView writeTv = new TextView(this);
            writeTv.setText(getString(R.string.meta_will_write, Texts.count(info.fileCount),
                    Formatters.humanBytes(info.contentBytes), info.folderName()));
            writeTv.setTextColor(Palette.TEXT_2);
            writeTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            writeTv.setPadding(0, dp(4), 0, 0);
            col.addView(writeTv);
        }

        // The fPKG chip already says it can be extracted; only a PKG that cannot gets an explanation.
        if (!ok) {
            TextView reasonTv = new TextView(this);
            reasonTv.setText(Texts.reason(this, info)
                    + (info.detail.isEmpty() ? "" : "\n" + getString(R.string.detail_prefix, info.detail)));
            reasonTv.setTextColor(Palette.DANGER);
            reasonTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            reasonTv.setTypeface(medium());
            reasonTv.setLineSpacing(dp(2), 1f);
            reasonTv.setPadding(0, dp(8), 0, 0);
            col.addView(reasonTv);
        }

        row.addView(col);
        card.addView(row);
        if (enabled && ok) {
            card.setBackground(ripple(cardBg));
            card.setOnClickListener(v -> app.engine.toggleSelection(item.id));
        }
        return card;
    }

    private void addMetaChip(LinearLayout chips, String text) {
        TextView chip = createChip(text, Palette.TEXT_2, Palette.SURFACE_2);
        chip.setPadding(dp(8), dp(3), dp(8), dp(3));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(6);
        chips.addView(chip, lp);
    }

    private void buildLiveConversionCard(ActiveConversionProgress p) {
        liveDashboardBox = createCard(Palette.SURFACE, Palette.GLOW_BORDER);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.TOP);
        LinearLayout leftCol = new LinearLayout(this);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        leftCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        livePhaseTv = overline("", Palette.HIGHLIGHT);
        leftCol.addView(livePhaseTv);
        liveTitleTv = new TextView(this);
        liveTitleTv.setTextColor(Palette.TEXT);
        liveTitleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        liveTitleTv.setTypeface(Typeface.DEFAULT_BOLD);
        liveTitleTv.setMaxLines(2);
        liveTitleTv.setEllipsize(TextUtils.TruncateAt.END);
        liveTitleTv.setPadding(0, dp(4), dp(10), 0);
        leftCol.addView(liveTitleTv);
        topRow.addView(leftCol);
        livePercentTv = new TextView(this);
        livePercentTv.setTextColor(Palette.HIGHLIGHT);
        livePercentTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        livePercentTv.setTypeface(Typeface.DEFAULT_BOLD);
        livePercentTv.setIncludeFontPadding(false);
        topRow.addView(livePercentTv);
        liveDashboardBox.addView(topRow);

        liveProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        liveProgressBar.setMax(1000);
        liveProgressBar.setProgressDrawable(roundedBar());
        liveProgressBar.setMinHeight(dp(10));
        liveProgressBar.setMaxHeight(dp(10));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10));
        barLp.topMargin = dp(14);
        liveDashboardBox.addView(liveProgressBar, barLp);

        // Under the bar: how much of the batch is written and, for the PKG at hand, how many files.
        LinearLayout underBar = new LinearLayout(this);
        underBar.setOrientation(LinearLayout.HORIZONTAL);
        underBar.setPadding(0, dp(8), 0, 0);
        liveBytesTv = smallText(Palette.TEXT_2);
        liveBytesTv.setTypeface(medium());
        underBar.addView(liveBytesTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        liveFilesTv = smallText(Palette.TEXT_3);
        liveFilesTv.setGravity(Gravity.END);
        underBar.addView(liveFilesTv);
        liveDashboardBox.addView(underBar);

        LinearLayout metricsRow = new LinearLayout(this);
        metricsRow.setOrientation(LinearLayout.HORIZONTAL);
        metricsRow.setPadding(0, dp(12), 0, 0);
        liveEtaTv = addMetricBox(metricsRow, getString(R.string.metric_left), true);
        liveSpeedTv = addMetricBox(metricsRow, getString(R.string.metric_speed), false);
        liveElapsedTv = addMetricBox(metricsRow, getString(R.string.metric_elapsed), false);
        liveDashboardBox.addView(metricsRow);

        livePathLabelTv = overline(getString(R.string.current_file), Palette.TEXT_3);
        livePathLabelTv.setPadding(0, dp(12), 0, 0);
        liveDashboardBox.addView(livePathLabelTv);
        // Paths inside a game are long; the end (the file name) and the start (the folder) say the most.
        livePathTv = smallText(Palette.TEXT_2);
        livePathTv.setTypeface(Typeface.MONOSPACE);
        livePathTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        livePathTv.setSingleLine(true);
        livePathTv.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        livePathTv.setPadding(0, dp(3), 0, 0);
        liveDashboardBox.addView(livePathTv);
        liveDetailTv = smallText(Palette.TEXT_2);
        liveDetailTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        liveDetailTv.setLineSpacing(dp(2), 1f);
        liveDetailTv.setPadding(0, dp(12), 0, 0);
        liveDashboardBox.addView(liveDetailTv);

        LinearLayout ctrlRow = new LinearLayout(this);
        ctrlRow.setOrientation(LinearLayout.HORIZONTAL);
        ctrlRow.setPadding(0, dp(14), 0, 0);
        if (p.running) {
            livePauseBtn = createSecondaryButton(getString(R.string.pause), Icons.PAUSE_HOLD);
            livePauseBtn.setOnClickListener(v -> app.engine.setPaused(!app.engine.getProgress().paused));
            ctrlRow.addView(livePauseBtn, halfLp(true));
            Button cancelBtn = createDangerButton(getString(R.string.cancel));
            cancelBtn.setOnClickListener(v -> app.engine.cancelConversion());
            ctrlRow.addView(cancelBtn, halfLp(false));
        } else {
            boolean hasResults = p.phase == ConversionPhase.DONE || p.phase == ConversionPhase.ERROR;
            if (hasResults) {
                Button resultsBtn = createSecondaryButton(getString(R.string.view_results), Icons.CHECK_VERIFIED);
                resultsBtn.setOnClickListener(v -> {
                    currentTab = TAB_RESULTS;
                    rebuildScreen();
                });
                ctrlRow.addView(resultsBtn, halfLp(true));
            }
            Button hideBtn = createSecondaryButton(getString(R.string.hide), 0);
            hideBtn.setOnClickListener(v -> {
                dismissedProgress = app.engine.getProgress();
                rebuildScreen();
            });
            ctrlRow.addView(hideBtn, hasResults ? halfLp(false)
                    : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        liveDashboardBox.addView(ctrlRow);

        bindLiveProgressViews(p);
        contentContainer.addView(liveDashboardBox, bottomMarginLp(14));
    }

    private void bindLiveProgressViews(ActiveConversionProgress p) {
        if (liveDashboardBox == null) return;
        boolean visible = p.running || (p.phase != ConversionPhase.IDLE && p != dismissedProgress);
        liveDashboardBox.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) return;

        int accent = p.paused ? Palette.COOL
                : p.phase == ConversionPhase.ERROR ? Palette.DANGER
                : p.phase == ConversionPhase.DONE ? Palette.SUCCESS
                : Palette.HIGHLIGHT;
        String phase = p.paused ? getString(R.string.paused) : Texts.phase(this, p.phase);
        livePhaseTv.setText(phase.toUpperCase(Locale.getDefault())
                + (p.totalItems > 1 ? " · " + getString(R.string.item_of, p.currentIndex, p.totalItems) : ""));
        livePhaseTv.setTextColor(accent);
        liveTitleTv.setText(p.currentItem != null ? p.currentItem.displayName() : "");
        livePercentTv.setText(p.batchPercentInt() + "%");
        livePercentTv.setTextColor(accent);
        liveProgressBar.setProgress(Math.round(p.batchProgressFloat() * 1000f));
        // No tint keeps the molten gradient of roundedBar(); a tint paints the whole bar in one color.
        liveProgressBar.setProgressTintList(p.paused ? ColorStateList.valueOf(Palette.COOL)
                : p.phase == ConversionPhase.ERROR ? ColorStateList.valueOf(Palette.DANGER)
                : p.phase == ConversionPhase.DONE ? ColorStateList.valueOf(Palette.SUCCESS)
                : null);

        liveBytesTv.setText(getString(R.string.bytes_of,
                Formatters.humanBytes(p.batchBytesDone), Formatters.humanBytes(p.batchBytesTotal)));
        // The file count belongs to one PKG; after a batch of several it would describe only the last one.
        boolean showFiles = p.filesTotal > 0 && (p.running || p.totalItems <= 1);
        liveFilesTv.setText(showFiles
                ? getString(R.string.files_of, Texts.count(Math.max(0, p.filesDone)), Texts.count(p.filesTotal)) : "");
        liveFilesTv.setVisibility(showFiles ? View.VISIBLE : View.GONE);

        liveEtaTv.setText(p.running ? Texts.eta(this, p.etaSeconds) : "—");
        liveSpeedTv.setText(p.running ? Formatters.humanSpeed(p.currentSpeedBps) : "—");
        liveElapsedTv.setText(p.elapsedMillis > 0 ? Formatters.humanDuration(p.elapsedMillis) : "—");

        boolean showPath = p.running && p.phase == ConversionPhase.EXTRACTING && !p.currentPath.isEmpty();
        livePathLabelTv.setVisibility(showPath ? View.VISIBLE : View.GONE);
        livePathTv.setVisibility(showPath ? View.VISIBLE : View.GONE);
        livePathTv.setText(p.currentPath);
        liveDetailTv.setVisibility(showPath || p.statusDetail.isEmpty() ? View.GONE : View.VISIBLE);
        liveDetailTv.setText(p.statusDetail);

        if (livePauseBtn != null) {
            livePauseBtn.setText(getString(p.paused ? R.string.resume : R.string.pause));
            livePauseBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    Icons.drawable(this, p.paused ? Icons.PLAY_RESUME : Icons.PAUSE_HOLD, 16), null, null, null);
            setEnabledLook(livePauseBtn, p.running);
        }
    }

    /** Progress bar with round ends: dark track and a molten gradient that the progress clips from the left. */
    private Drawable roundedBar() {
        GradientDrawable track = new GradientDrawable();
        track.setColor(Palette.SURFACE_2);
        track.setCornerRadius(dp(5));
        GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Palette.HIGHLIGHT, Palette.ACCENT});
        fill.setCornerRadius(dp(5));
        LayerDrawable bar = new LayerDrawable(new Drawable[]{
                track, new ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL)});
        bar.setId(0, android.R.id.background);
        bar.setId(1, android.R.id.progress);
        return bar;
    }

    private TextView smallText(int color) {
        TextView tv = new TextView(this);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        return tv;
    }

    private LinearLayout.LayoutParams halfLp(boolean left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (left) lp.rightMargin = dp(5);
        else lp.leftMargin = dp(5);
        return lp;
    }

    private TextView addMetricBox(LinearLayout parentRow, String label, boolean first) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(8), dp(8), dp(9));
        box.setBackground(rounded(Palette.SURFACE_2, 10, 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = first ? 0 : dp(8);
        TextView l = overline(label, Palette.TEXT_3);
        l.setSingleLine(true);
        l.setEllipsize(TextUtils.TruncateAt.END);
        box.addView(l);
        TextView val = new TextView(this);
        val.setText("—");
        val.setTextColor(Palette.TEXT);
        val.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        val.setTypeface(Typeface.DEFAULT_BOLD);
        val.setSingleLine(true);
        val.setEllipsize(TextUtils.TruncateAt.END);
        val.setPadding(0, dp(3), 0, 0);
        box.addView(val);
        parentRow.addView(box, lp);
        return val;
    }

    // -------------------------------------------------------------------------------------- Results tab

    private void buildResultsTab() {
        List<ConversionResultItem> results = app.engine.getResults();
        addAppHeader(getString(R.string.tab_results),
                getString(R.string.results_subtitle, getString(R.string.app_name)), true);

        if (results.isEmpty()) {
            LinearLayout empty = createCard(Palette.SURFACE, Palette.BORDER);
            addCardTitleWithIcon(empty, getString(R.string.results_empty_title), Icons.BINARY_DISC);
            addBodyText(empty, getString(R.string.results_empty_body));
            contentContainer.addView(empty, bottomMarginLp(12));
            return;
        }

        for (ConversionResultItem r : results) {
            LinearLayout card = createCard(Palette.SURFACE, r.success ? Palette.BORDER : Palette.DANGER);

            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.TOP);
            TextView title = new TextView(this);
            title.setText(r.title);
            title.setTextColor(Palette.TEXT);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setPadding(0, 0, dp(8), 0);
            title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            top.addView(title);
            top.addView(r.success
                    ? createChip(getString(R.string.result_ok_chip), Palette.SUCCESS, Palette.SUCCESS_BG)
                    : createChip(getString(R.string.result_fail_chip), Palette.DANGER, Palette.DANGER_BG));
            card.addView(top);

            if (r.success) {
                StringBuilder sb = new StringBuilder();
                sb.append(getString(R.string.result_game_folder, Texts.shortPath(this, r.gameDirPath))).append('\n');
                sb.append(getString(R.string.result_stats, r.files, Formatters.humanBytes(r.bytes),
                        Formatters.humanDuration(r.durationMillis))).append('\n');
                if (r.ebootSize >= 0) {
                    sb.append(r.ebootIsSelf()
                            ? getString(R.string.result_eboot_self, Formatters.humanBytes(r.ebootSize))
                            : getString(R.string.result_eboot_other, Formatters.humanBytes(r.ebootSize), r.ebootHead));
                } else {
                    sb.append(getString(R.string.result_no_eboot));
                }
                addBodyText(card, sb.toString());
                if (!r.skipped.isEmpty()) {
                    addBodyText(card, getString(R.string.result_skipped, TextUtils.join(", ", r.skipped)));
                }
            } else {
                addBodyText(card, getString(R.string.result_error,
                        r.errorMessage != null ? r.errorMessage : getString(R.string.result_unknown_error)));
                addBodyText(card, getString(R.string.result_fail_help));
                Button openLogBtn = createSecondaryButton(getString(R.string.open_log), Icons.LOG_LIST);
                openLogBtn.setOnClickListener(v -> openLog());
                card.addView(openLogBtn, topMarginLp(12));
            }
            TextView safe = new TextView(this);
            safe.setText(getString(R.string.result_source_safe, Texts.shortPath(this, r.sourcePkgPath)));
            safe.setTextColor(Palette.TEXT_3);
            safe.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            safe.setPadding(0, dp(10), 0, 0);
            card.addView(safe);
            contentContainer.addView(card, bottomMarginLp(10));
        }
    }

    // ----------------------------------------------------------------------------------------- Log page

    private void buildLogPage() {
        String appName = getString(R.string.app_name);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(16));
        View back = iconButton(Icons.BACK, getString(R.string.back));
        back.setOnClickListener(v -> closeLog());
        row.addView(back, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), 0, 0, 0);
        TextView t = new TextView(this);
        t.setText(R.string.log_title);
        t.setTextColor(Palette.TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(t);
        TextView s = new TextView(this);
        s.setText(getString(R.string.log_subtitle, appName));
        s.setTextColor(Palette.TEXT_3);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        col.addView(s);
        row.addView(col);
        contentContainer.addView(row);

        String log = app.engine.readLogTail(60_000);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button copyBtn = createSecondaryButton(getString(R.string.log_copy), Icons.COPY);
        copyBtn.setOnClickListener(v -> {
            ClipboardManager cm = getSystemService(ClipboardManager.class);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.log_clip_label, appName), log));
                Toast.makeText(this, R.string.log_copied, Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams a1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        a1.rightMargin = dp(5);
        actions.addView(copyBtn, a1);
        Button shareBtn = createSecondaryButton(getString(R.string.log_share), Icons.SHARE);
        shareBtn.setOnClickListener(v -> {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.log_share_subject, appName, app.version));
            send.putExtra(Intent.EXTRA_TEXT, log);
            startActivity(Intent.createChooser(send, getString(R.string.log_share_chooser)));
        });
        LinearLayout.LayoutParams a2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        a2.leftMargin = dp(5);
        actions.addView(shareBtn, a2);
        contentContainer.addView(actions, bottomMarginLp(12));

        LinearLayout card = createCard(Palette.SURFACE, Palette.BORDER);
        TextView tv = new TextView(this);
        tv.setText(log);
        tv.setTextColor(Palette.TEXT_2);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        tv.setLineSpacing(dp(1), 1f);
        tv.setTextIsSelectable(true);
        card.addView(tv);
        contentContainer.addView(card, bottomMarginLp(12));
    }

    // ------------------------------------------------------------------------------------------ helpers

    private void addFolderRow(
            LinearLayout parent,
            int iconType,
            String label,
            String path,
            String subtitle,
            String extra,
            boolean enabled,
            Runnable onClick
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);

        FrameLayout iconBox = new FrameLayout(this);
        iconBox.setBackground(rounded(Palette.SURFACE_2, 12, 0));
        iconBox.addView(Icons.imageView(this, iconType, 24), new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        row.addView(iconBox, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), 0, dp(10), 0);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        col.addView(overline(label, Palette.HIGHLIGHT));

        TextView pTv = new TextView(this);
        pTv.setText(Texts.shortPath(this, path));
        pTv.setTextColor(Palette.TEXT);
        pTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        pTv.setTypeface(Typeface.DEFAULT_BOLD);
        pTv.setPadding(0, dp(3), 0, dp(2));
        col.addView(pTv);

        TextView sTv = new TextView(this);
        sTv.setText(subtitle);
        sTv.setTextColor(Palette.TEXT_3);
        sTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        sTv.setLineSpacing(dp(1), 1f);
        col.addView(sTv);
        if (extra != null) {
            TextView eTv = new TextView(this);
            eTv.setText(extra);
            eTv.setTextColor(Palette.TEXT_2);
            eTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            eTv.setTypeface(medium());
            eTv.setPadding(0, dp(3), 0, 0);
            col.addView(eTv);
        }
        row.addView(col);

        Button btn = createSmallActionButton(getString(R.string.change), onClick);
        setEnabledLook(btn, enabled);
        row.addView(btn);
        parent.addView(row);
    }

    private TextView overline(String text, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setLetterSpacing(0.08f);
        tv.setAllCaps(true);
        return tv;
    }

    private LinearLayout createCard(int bgColor, int strokeColor) {
        return createCard(rounded(bgColor, 18, strokeColor));
    }

    private LinearLayout createCard(Drawable background) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        box.setPadding(p, p, p, p);
        box.setBackground(background);
        return box;
    }

    private void addCardTitleWithIcon(LinearLayout card, String text, int iconType) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView iv = Icons.imageView(this, iconType, 20);
        row.addView(iv, new LinearLayout.LayoutParams(dp(20), dp(20)));

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Palette.TEXT);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(dp(10), 0, 0, 0);
        row.addView(tv);

        card.addView(row);
    }

    private void addBodyText(LinearLayout card, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Palette.TEXT_2);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setLineSpacing(dp(2), 1f);
        tv.setPadding(0, dp(8), 0, 0);
        card.addView(tv);
    }

    private void addDivider(LinearLayout card) {
        View v = new View(this);
        v.setBackgroundColor(Palette.BORDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.topMargin = dp(14);
        lp.bottomMargin = dp(14);
        card.addView(v, lp);
    }

    /** The one call to action of a screen: molten gradient, dark text. */
    private Button createPrimaryButton(String text) {
        Button b = baseButton(text, Palette.ON_ACCENT, 15);
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Palette.HIGHLIGHT, Palette.ACCENT});
        gd.setCornerRadius(dp(14));
        b.setBackground(ripple(gd));
        b.setMinHeight(dp(52));
        b.setMinimumHeight(dp(52));
        return b;
    }

    private Button createSecondaryButton(String text, int iconType) {
        Button b = baseButton(text, Palette.TEXT, 13);
        if (iconType != 0) {
            b.setCompoundDrawablesRelativeWithIntrinsicBounds(Icons.drawable(this, iconType, 16), null, null, null);
            b.setCompoundDrawablePadding(dp(8));
        }
        b.setBackground(ripple(rounded(Palette.SURFACE_2, 12, Palette.BORDER)));
        return b;
    }

    private Button createDangerButton(String text) {
        Button b = baseButton(text, Palette.DANGER, 13);
        b.setCompoundDrawablesRelativeWithIntrinsicBounds(Icons.drawable(this, Icons.STOP_SAFE, 16), null, null, null);
        b.setCompoundDrawablePadding(dp(8));
        b.setBackground(ripple(rounded(Palette.DANGER_BG, 12, (Palette.DANGER & 0x00FFFFFF) | 0x66000000)));
        return b;
    }

    private Button createSmallActionButton(String text, Runnable onClick) {
        Button b = baseButton(text, Palette.ACCENT, 12);
        b.setMinHeight(dp(36));
        b.setMinimumHeight(dp(36));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(14), dp(6), dp(14), dp(6));
        b.setBackground(ripple(rounded(Palette.GLOW, 10, Palette.GLOW_BORDER)));
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    private Button baseButton(String text, int textColor, float sizeSp) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(textColor);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        b.setPadding(dp(16), dp(10), dp(16), dp(10));
        b.setStateListAnimator(null);
        b.setElevation(0f);
        return b;
    }

    /** Small square button with only an icon, for secondary places (the log, back). */
    private View iconButton(int iconType, String description) {
        FrameLayout box = new FrameLayout(this);
        box.setBackground(ripple(rounded(Palette.SURFACE_2, 12, Palette.BORDER)));
        box.setContentDescription(description);
        box.setClickable(true);
        box.setFocusable(true);
        box.addView(Icons.imageView(this, iconType, 18), new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        return box;
    }

    private TextView createChip(String text, int textColor, int bgColor) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(textColor);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setLetterSpacing(0.04f);
        tv.setPadding(dp(10), dp(4), dp(10), dp(4));
        tv.setBackground(rounded(bgColor, 99, 0));
        return tv;
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeColor) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) gd.setStroke(dp(1), strokeColor);
        return gd;
    }

    private static Drawable ripple(Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), content, null);
    }

    /** Disabled views also look disabled: the flat drawables have no disabled state of their own. */
    private static void setEnabledLook(View v, boolean enabled) {
        v.setEnabled(enabled);
        v.setAlpha(enabled ? 1f : 0.45f);
    }

    private static Typeface medium() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    private LinearLayout.LayoutParams topMarginLp(int dpVal) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.topMargin = dp(dpVal);
        return lp;
    }

    private LinearLayout.LayoutParams bottomMarginLp(int dpVal) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(dpVal);
        return lp;
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                v,
                getResources().getDisplayMetrics()
        );
    }

    private void launchSystemTreePicker(int requestCode) {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(intent, requestCode);
        } catch (Throwable e) {
            Toast.makeText(this, getString(R.string.system_picker_unavailable, String.valueOf(e.getMessage())),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String resolved = FolderPickerDialogs.resolveTreeUriToFilePath(uri);
        if (resolved == null) return;

        if (requestCode == REQ_SOURCE_TREE) {
            app.settings.setSourceDirPath(resolved);
            app.engine.scanSourceFolder(new File(resolved));
        } else if (requestCode == REQ_DEST_TREE) {
            app.settings.setDestDirPath(resolved);
            rebuildScreen();
        }
    }

    private void requestAllFilesAccess() {
        try {
            startActivity(new Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            ));
        } catch (Throwable e) {
            startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        }
    }

    @SuppressLint("BatteryLife")
    private void requestIgnoreBatteryOptimizations() {
        try {
            startActivity(new Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())
            ));
        } catch (Throwable ignored) {
        }
    }

    private void requestOverlayPermission() {
        try {
            startActivity(new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            ));
        } catch (Throwable ignored) {
        }
    }
}
