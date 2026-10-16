package io.github.codewizardqa.mrbin.ui;

import android.content.Context;
import android.os.Environment;

import io.github.codewizardqa.mrbin.R;
import io.github.codewizardqa.mrbin.engine.PkgModels.ConversionPhase;
import io.github.codewizardqa.mrbin.engine.PkgModels.Formatters;
import io.github.codewizardqa.mrbin.engine.orbis.OrbisPkg;

import java.io.File;
import java.util.Locale;

/** Turns what the engine reports (phases, PKG kinds, reasons) into text in the phone's language. */
public final class Texts {
    private Texts() {}

    public static String phase(Context c, ConversionPhase p) {
        switch (p) {
            case READING:
                return c.getString(R.string.phase_reading);
            case EXTRACTING:
                return c.getString(R.string.phase_extracting);
            case DONE:
                return c.getString(R.string.phase_done);
            case CANCELLED:
                return c.getString(R.string.phase_cancelled);
            case ERROR:
                return c.getString(R.string.phase_error);
            case IDLE:
            default:
                return c.getString(R.string.phase_idle);
        }
    }

    public static String kindBadge(Context c, OrbisPkg.Kind k) {
        switch (k) {
            case PS4_FAKE:
                return "fPKG";
            case PS4_RETAIL:
                return c.getString(R.string.badge_store);
            case PS3:
                return "PS3";
            case DAMAGED:
                return c.getString(R.string.badge_damaged);
            default:
                return c.getString(R.string.badge_not_pkg);
        }
    }

    public static String reason(Context c, OrbisPkg.Info i) {
        switch (i.reasonCode) {
            case OK:
                return c.getString(R.string.reason_ok);
            case PS3:
                return c.getString(R.string.reason_ps3);
            case NOT_PS4:
                return c.getString(R.string.reason_not_ps4);
            case BAD_HEADER:
                return c.getString(R.string.reason_bad_header);
            case INCOMPLETE:
                return c.getString(R.string.reason_incomplete,
                        Formatters.humanBytes(i.pkgSize), Formatters.humanBytes(i.expectedSize));
            case NO_PFS:
                return c.getString(R.string.reason_no_pfs);
            case RETAIL:
                return c.getString(R.string.reason_retail);
            case BAD_CONTENT:
                return c.getString(R.string.reason_bad_content);
            case NO_PFS_IMAGE:
                return c.getString(R.string.reason_no_pfs_image);
            case INNER_DAMAGED:
                return c.getString(R.string.reason_inner_damaged);
            case UNREADABLE:
            default:
                return c.getString(R.string.reason_unreadable);
        }
    }

    /** param.sfo CATEGORY in words: gd = game, gp = update (patch), ac = add-on (DLC); anything else as it is. */
    public static String category(Context c, String code) {
        switch (code) {
            case "gd":
            case "gde":
                return c.getString(R.string.cat_game);
            case "gp":
                return c.getString(R.string.cat_update);
            case "ac":
                return c.getString(R.string.cat_dlc);
            default:
                return code;
        }
    }

    /** 28824 → "28,824" in English, "28.824" in Spanish. */
    public static String count(long n) {
        return String.format(Locale.getDefault(), "%,d", n);
    }

    /** Time left; "calculating…" while the engine has no speed yet. */
    public static String eta(Context c, long seconds) {
        return seconds < 0 ? c.getString(R.string.calculating) : Formatters.humanEta(seconds);
    }

    /** "/storage/emulated/0/Download/Games" → "Download/Games"; other volumes keep their full path. */
    public static String shortPath(Context c, String path) {
        String root = storageRoot().getAbsolutePath();
        if (path.equals(root)) return c.getString(R.string.internal_storage);
        if (path.startsWith(root + "/")) return path.substring(root.length() + 1);
        return path;
    }

    static File storageRoot() {
        try {
            File ext = Environment.getExternalStorageDirectory();
            if (ext != null) return ext;
        } catch (Throwable ignored) {
        }
        return new File("/storage/emulated/0");
    }
}
