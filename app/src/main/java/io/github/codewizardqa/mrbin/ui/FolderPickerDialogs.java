package io.github.codewizardqa.mrbin.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import io.github.codewizardqa.mrbin.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * In-app folder picker: shortcuts, the subfolders of the current folder (with how many .pkg each holds), up one
 * level, new folder, and a way out to Android's own picker.
 */
public final class FolderPickerDialogs {

    public interface OnFolderSelected {
        void onSelected(String path);
    }

    private FolderPickerDialogs() {}

    public static void show(
            Activity activity,
            String title,
            String initialPath,
            boolean allowCreateFolder,
            Runnable onLaunchSystemPicker,
            OnFolderSelected callback
    ) {
        final File storageRoot = getStorageRoot();
        File start = new File(initialPath);
        if (!start.exists() || !start.isDirectory()) {
            File parent = start.getParentFile();
            start = (parent != null && parent.exists() && parent.isDirectory()) ? parent : storageRoot;
        }
        final File[] currentDir = new File[]{start};

        ScrollView rootScroll = new ScrollView(activity);
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 16);
        container.setPadding(pad, pad, pad, pad);
        container.setBackgroundColor(Palette.SURFACE);
        rootScroll.addView(container);

        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView headerIcon = Icons.imageView(
                activity,
                allowCreateFolder ? Icons.FOLDER_SHIELD : Icons.FOLDER_IN,
                26
        );
        headerRow.addView(headerIcon, new LinearLayout.LayoutParams(dp(activity, 26), dp(activity, 26)));

        TextView titleView = new TextView(activity);
        titleView.setText(title);
        titleView.setTextColor(Palette.TEXT);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setPadding(dp(activity, 10), 0, 0, 0);
        headerRow.addView(titleView);
        container.addView(headerRow);

        final TextView currentPathLabel = new TextView(activity);
        currentPathLabel.setTextColor(Palette.HIGHLIGHT);
        currentPathLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        currentPathLabel.setPadding(0, dp(activity, 6), 0, dp(activity, 10));
        container.addView(currentPathLabel);

        TextView quickLabel = new TextView(activity);
        quickLabel.setText(R.string.fp_shortcuts);
        quickLabel.setTextColor(Palette.TEXT_3);
        quickLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        container.addView(quickLabel);

        HorizontalScrollView chipScroll = new HorizontalScrollView(activity);
        chipScroll.setPadding(0, dp(activity, 6), 0, dp(activity, 8));
        LinearLayout chipRow = new LinearLayout(activity);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipScroll.addView(chipRow);
        container.addView(chipScroll);

        LinearLayout actionsRow = new LinearLayout(activity);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setPadding(0, 0, 0, dp(activity, 8));
        container.addView(actionsRow);

        final LinearLayout newFolderBox = new LinearLayout(activity);
        newFolderBox.setOrientation(LinearLayout.HORIZONTAL);
        newFolderBox.setVisibility(View.GONE);
        newFolderBox.setPadding(0, 0, 0, dp(activity, 8));
        final EditText newFolderInput = new EditText(activity);
        newFolderInput.setHint(R.string.fp_new_folder_hint);
        newFolderInput.setHintTextColor(Palette.TEXT_3);
        newFolderInput.setTextColor(Palette.TEXT);
        newFolderInput.setBackgroundTintList(ColorStateList.valueOf(Palette.ACCENT));
        newFolderInput.setSingleLine(true);
        LinearLayout.LayoutParams nfLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        newFolderBox.addView(newFolderInput, nfLp);
        Button createFolderBtn = smallButton(activity, activity.getString(R.string.fp_create), Palette.ACCENT,
                Palette.ON_ACCENT, 0);
        newFolderBox.addView(createFolderBtn);
        container.addView(newFolderBox);

        final LinearLayout subDirsContainer = new LinearLayout(activity);
        subDirsContainer.setOrientation(LinearLayout.VERTICAL);
        container.addView(subDirsContainer);

        TextView manualLabel = new TextView(activity);
        manualLabel.setText(R.string.fp_selected);
        manualLabel.setTextColor(Palette.TEXT_3);
        manualLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        manualLabel.setPadding(0, dp(activity, 10), 0, dp(activity, 2));
        container.addView(manualLabel);

        final EditText pathInput = new EditText(activity);
        pathInput.setTextColor(Palette.TEXT);
        pathInput.setBackgroundTintList(ColorStateList.valueOf(Palette.ACCENT));
        pathInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        pathInput.setSingleLine(true);
        container.addView(pathInput);

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(rootScroll)
                .setPositiveButton(R.string.fp_use, (d, w) -> {
                    String chosen = pathInput.getText().toString().trim();
                    if (chosen.isEmpty()) chosen = currentDir[0].getAbsolutePath();
                    callback.onSelected(chosen);
                })
                .setNegativeButton(R.string.cancel, null)
                .create();

        final Runnable[] refreshUi = new Runnable[1];
        refreshUi[0] = () -> {
            File dir = currentDir[0];
            int pkgHere = countPkgsInDir(dir);
            String badge = pkgHere > 0
                    ? " · " + activity.getResources().getQuantityString(R.plurals.fp_pkgs_here, pkgHere, pkgHere) : "";
            currentPathLabel.setText(dir.getAbsolutePath() + badge);
            pathInput.setText(dir.getAbsolutePath());

            subDirsContainer.removeAllViews();
            File[] children = dir.listFiles();
            List<File> subDirs = new ArrayList<>();
            if (children != null) {
                for (File c : children) {
                    if (c.isDirectory() && !c.getName().startsWith(".")) {
                        subDirs.add(c);
                    }
                }
            }
            subDirs.sort(Comparator.comparing(f -> f.getName().toLowerCase(Locale.ROOT)));

            if (subDirs.isEmpty()) {
                TextView empty = new TextView(activity);
                empty.setText(R.string.fp_no_subfolders);
                empty.setTextColor(Palette.TEXT_3);
                empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                empty.setPadding(0, dp(activity, 6), 0, dp(activity, 6));
                subDirsContainer.addView(empty);
            } else {
                for (File sub : subDirs) {
                    LinearLayout row = new LinearLayout(activity);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
                    GradientDrawable bg = new GradientDrawable();
                    bg.setColor(Palette.SURFACE_2);
                    bg.setCornerRadius(dp(activity, 10));
                    bg.setStroke(dp(activity, 1), Palette.BORDER);
                    row.setBackground(bg);

                    LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
                    rlp.bottomMargin = dp(activity, 6);

                    ImageView folderIv = Icons.imageView(activity, Icons.FOLDER_IN, 18);
                    row.addView(folderIv, new LinearLayout.LayoutParams(dp(activity, 18), dp(activity, 18)));

                    TextView nameTv = new TextView(activity);
                    nameTv.setText(sub.getName());
                    nameTv.setTextColor(Palette.TEXT);
                    nameTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                    nameTv.setPadding(dp(activity, 10), 0, 0, 0);
                    nameTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    row.addView(nameTv);

                    int count = countPkgsInDir(sub);
                    if (count > 0) {
                        TextView badgeTv = new TextView(activity);
                        badgeTv.setText(count + " .PKG");
                        badgeTv.setTextColor(Palette.HIGHLIGHT);
                        badgeTv.setTypeface(Typeface.DEFAULT_BOLD);
                        badgeTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                        row.addView(badgeTv);
                    }

                    row.setOnClickListener(v -> {
                        currentDir[0] = sub;
                        refreshUi[0].run();
                    });
                    subDirsContainer.addView(row, rlp);
                }
            }
        };

        String appFolder = activity.getString(R.string.app_folder);
        String[][] shortcuts = new String[][]{
                {"Download", new File(storageRoot, "Download").getAbsolutePath()},
                {"Download/" + appFolder, new File(new File(storageRoot, "Download"), appFolder).getAbsolutePath()},
                {"Documents", new File(storageRoot, "Documents").getAbsolutePath()},
                {activity.getString(R.string.internal_storage), storageRoot.getAbsolutePath()}
        };
        for (String[] sc : shortcuts) {
            // Only the app's own output folder is created on tap; the others are shown when they exist.
            boolean isAppFolder = sc[0].equals("Download/" + appFolder);
            if (!isAppFolder && !new File(sc[1]).isDirectory()) continue;
            Button chip = smallButton(activity, sc[0], Palette.SURFACE_2, Palette.TEXT, 0);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            clp.rightMargin = dp(activity, 6);
            chip.setOnClickListener(v -> {
                File target = new File(sc[1]);
                if (!target.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    target.mkdirs();
                }
                currentDir[0] = target;
                refreshUi[0].run();
            });
            chipRow.addView(chip, clp);
        }

        Button upBtn = smallButton(activity, activity.getString(R.string.fp_up), Palette.SURFACE_2, Palette.TEXT,
                Icons.UP_LEVEL);
        upBtn.setOnClickListener(v -> {
            File parent = currentDir[0].getParentFile();
            if (parent != null && parent.canRead()) {
                currentDir[0] = parent;
                refreshUi[0].run();
            }
        });
        actionsRow.addView(upBtn);

        if (allowCreateFolder) {
            Button newBtn = smallButton(activity, activity.getString(R.string.fp_new), Palette.SURFACE_2, Palette.ACCENT,
                    Icons.NEW_FOLDER);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            nlp.leftMargin = dp(activity, 6);
            newBtn.setOnClickListener(v -> {
                newFolderBox.setVisibility(
                        newFolderBox.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE
                );
            });
            actionsRow.addView(newBtn, nlp);
        }

        if (onLaunchSystemPicker != null) {
            Button sysBtn = smallButton(activity, activity.getString(R.string.fp_system), Palette.SURFACE_2,
                    Palette.TEXT_2, 0);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            slp.leftMargin = dp(activity, 6);
            sysBtn.setOnClickListener(v -> {
                dialog.dismiss();
                onLaunchSystemPicker.run();
            });
            actionsRow.addView(sysBtn, slp);
        }

        createFolderBtn.setOnClickListener(v -> {
            String name = newFolderInput.getText().toString().trim();
            if (!name.isEmpty()) {
                File created = new File(currentDir[0], name);
                //noinspection ResultOfMethodCallIgnored
                created.mkdirs();
                currentDir[0] = created;
                newFolderInput.setText("");
                newFolderBox.setVisibility(View.GONE);
                refreshUi[0].run();
            }
        });

        refreshUi[0].run();
        dialog.show();
    }

    private static int countPkgsInDir(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return 0;
        int c = 0;
        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".pkg")) {
                c++;
            }
        }
        return c;
    }

    private static Button smallButton(Context ctx, String text, int bgColor, int textColor, int iconType) {
        Button b = new Button(ctx);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(textColor);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        b.setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 10), dp(ctx, 6));
        if (iconType != 0) {
            b.setCompoundDrawablesWithIntrinsicBounds(Icons.drawable(ctx, iconType, 15), null, null, null);
            b.setCompoundDrawablePadding(dp(ctx, 6));
        }
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bgColor);
        gd.setCornerRadius(dp(ctx, 10));
        gd.setStroke(dp(ctx, 1), Palette.BORDER);
        b.setBackground(gd);
        b.setStateListAnimator(null);
        return b;
    }

    private static File getStorageRoot() {
        return Texts.storageRoot();
    }

    public static String resolveTreeUriToFilePath(Uri uri) {
        if (uri == null) return null;
        try {
            String docId = DocumentsContract.getTreeDocumentId(uri);
            if (docId == null) return null;
            if (docId.startsWith("raw:")) return docId.substring(4);
            String[] parts = docId.split(":");
            String root = getStorageRoot().getAbsolutePath();
            if (parts.length >= 2 && "primary".equalsIgnoreCase(parts[0])) {
                return root + "/" + parts[1];
            }
            if (parts.length == 1 && "primary".equalsIgnoreCase(parts[0])) {
                return root;
            }
            if (parts.length >= 2) {
                return "/storage/" + parts[0] + "/" + parts[1];
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static int dp(Context ctx, int v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                v,
                ctx.getResources().getDisplayMetrics()
        );
    }
}
