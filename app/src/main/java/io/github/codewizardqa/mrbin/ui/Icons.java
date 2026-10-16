package io.github.codewizardqa.mrbin.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.widget.ImageView;

import io.github.codewizardqa.mrbin.R;

import java.util.HashMap;
import java.util.Map;

/**
 * The app's icons, drawn in code on a 64×64 grid (no image files, no emoji): lime and mustard from
 * {@link Palette}, green for done and red for stop. The one exception is {@link #MR_BIN}, Mr. Bin's face,
 * which is drawable-nodpi/ic_glyph.png, cut from the same artwork as the launcher icon by art/icon.py.
 * Bitmaps are cached per type and size.
 */
public final class Icons {

    public static final int MR_BIN = 1;
    public static final int BINARY_DISC = 2;
    public static final int CPU_CHIP = 3;
    public static final int FOLDER_IN = 4;
    public static final int FOLDER_SHIELD = 5;
    public static final int RADAR_SCAN = 6;
    public static final int BOLT_TURBO = 7;
    public static final int CRYPTO_KEY = 8;
    public static final int PLAY_RESUME = 9;
    public static final int PAUSE_HOLD = 10;
    public static final int STOP_SAFE = 11;
    public static final int CHECK_VERIFIED = 12;
    public static final int ALERT_WARN = 13;
    public static final int UP_LEVEL = 14;
    public static final int NEW_FOLDER = 15;
    public static final int EBOOT_BADGE = 16;
    public static final int LOG_LIST = 17;
    public static final int BACK = 18;
    public static final int COPY = 19;
    public static final int SHARE = 20;

    private static final int PRIMARY = Palette.ACCENT;
    private static final int SECONDARY = Palette.HIGHLIGHT;
    private static final int EMERALD = Palette.SUCCESS;
    private static final int CORAL = Palette.DANGER;
    private static final int QUIET = Palette.TEXT_2;

    private static final Map<String, Bitmap> CACHE = new HashMap<>();

    private Icons() {}

    public static ImageView imageView(Context ctx, int iconType, int sizeDp) {
        ImageView iv = new ImageView(ctx);
        iv.setImageDrawable(drawable(ctx, iconType, sizeDp));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return iv;
    }

    public static Drawable drawable(Context ctx, int iconType, int sizeDp) {
        int px = Math.max(24, (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                sizeDp,
                ctx.getResources().getDisplayMetrics()
        ));
        String key = iconType + "_" + px;
        Bitmap bmp = CACHE.get(key);
        if (bmp == null) {
            bmp = iconType == MR_BIN ? renderGlyph(ctx, px) : renderBitmap(iconType, px);
            CACHE.put(key, bmp);
        }
        return new BitmapDrawable(ctx.getResources(), bmp);
    }

    /** Mr. Bin's face, from the artwork. */
    private static Bitmap renderGlyph(Context ctx, int sizePx) {
        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Drawable glyph = ctx.getDrawable(R.drawable.ic_glyph);
        glyph.setBounds(0, 0, sizePx, sizePx);
        glyph.draw(new Canvas(bmp));
        return bmp;
    }

    private static Bitmap renderBitmap(int iconType, int sizePx) {
        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float s = sizePx / 64f;

        Paint primaryStroke = strokePaint(PRIMARY, 3.8f * s);
        Paint secondaryStroke = strokePaint(SECONDARY, 3.6f * s);
        Paint emeraldStroke = strokePaint(EMERALD, 4.0f * s);
        Paint coralStroke = strokePaint(CORAL, 3.8f * s);
        Paint primaryFill = fillPaint(PRIMARY);
        Paint secondaryFill = fillPaint(SECONDARY);
        Paint quietStroke = strokePaint(QUIET, 4.2f * s);

        switch (iconType) {
            case BINARY_DISC: {
                // Disc with a core
                c.drawCircle(32 * s, 32 * s, 22 * s, primaryStroke);
                c.drawCircle(32 * s, 32 * s, 9 * s, secondaryStroke);
                c.drawCircle(32 * s, 32 * s, 3 * s, primaryFill);
                // Tracks
                RectF arc = new RectF(16 * s, 16 * s, 48 * s, 48 * s);
                c.drawArc(arc, -60f, 100f, false, secondaryStroke);
                c.drawArc(arc, 120f, 100f, false, secondaryStroke);
                break;
            }
            case CPU_CHIP: {
                // Chip with pins
                RectF outer = new RectF(16 * s, 16 * s, 48 * s, 48 * s);
                c.drawRoundRect(outer, 5 * s, 5 * s, primaryStroke);
                RectF inner = new RectF(24 * s, 24 * s, 40 * s, 40 * s);
                c.drawRoundRect(inner, 3 * s, 3 * s, secondaryStroke);
                float[] pins = new float[]{22 * s, 32 * s, 42 * s};
                for (float p : pins) {
                    c.drawLine(p, 7 * s, p, 16 * s, secondaryStroke);
                    c.drawLine(p, 48 * s, p, 57 * s, secondaryStroke);
                    c.drawLine(7 * s, p, 16 * s, p, primaryStroke);
                    c.drawLine(48 * s, p, 57 * s, p, primaryStroke);
                }
                break;
            }
            case FOLDER_IN: {
                // Folder with an arrow going in
                Path folder = new Path();
                folder.moveTo(8 * s, 18 * s);
                folder.lineTo(24 * s, 18 * s);
                folder.lineTo(29 * s, 24 * s);
                folder.lineTo(56 * s, 24 * s);
                folder.lineTo(56 * s, 52 * s);
                folder.lineTo(8 * s, 52 * s);
                folder.close();
                c.drawPath(folder, primaryStroke);
                // Arrow
                c.drawLine(32 * s, 10 * s, 32 * s, 40 * s, secondaryStroke);
                c.drawLine(23 * s, 31 * s, 32 * s, 40 * s, secondaryStroke);
                c.drawLine(41 * s, 31 * s, 32 * s, 40 * s, secondaryStroke);
                break;
            }
            case FOLDER_SHIELD: {
                // Folder with a shield and a check
                Path folder = new Path();
                folder.moveTo(8 * s, 18 * s);
                folder.lineTo(24 * s, 18 * s);
                folder.lineTo(29 * s, 24 * s);
                folder.lineTo(52 * s, 24 * s);
                folder.lineTo(52 * s, 48 * s);
                folder.lineTo(8 * s, 48 * s);
                folder.close();
                c.drawPath(folder, primaryStroke);

                Path shield = new Path();
                shield.moveTo(44 * s, 30 * s);
                shield.lineTo(58 * s, 35 * s);
                shield.lineTo(58 * s, 46 * s);
                shield.lineTo(44 * s, 58 * s);
                shield.lineTo(30 * s, 46 * s);
                shield.lineTo(30 * s, 35 * s);
                shield.close();
                c.drawPath(shield, secondaryStroke);
                c.drawLine(38 * s, 44 * s, 43 * s, 49 * s, emeraldStroke);
                c.drawLine(43 * s, 49 * s, 51 * s, 39 * s, emeraldStroke);
                break;
            }
            case RADAR_SCAN: {
                c.drawCircle(32 * s, 32 * s, 23 * s, secondaryStroke);
                c.drawCircle(32 * s, 32 * s, 13 * s, primaryStroke);
                c.drawLine(32 * s, 6 * s, 32 * s, 58 * s, secondaryStroke);
                c.drawLine(6 * s, 32 * s, 58 * s, 32 * s, secondaryStroke);
                c.drawLine(32 * s, 32 * s, 48 * s, 16 * s, primaryStroke);
                break;
            }
            case BOLT_TURBO: {
                Path bolt = new Path();
                bolt.moveTo(36 * s, 6 * s);
                bolt.lineTo(16 * s, 34 * s);
                bolt.lineTo(31 * s, 34 * s);
                bolt.lineTo(24 * s, 58 * s);
                bolt.lineTo(48 * s, 28 * s);
                bolt.lineTo(33 * s, 28 * s);
                bolt.close();
                c.drawPath(bolt, primaryFill);
                c.drawPath(bolt, secondaryStroke);
                break;
            }
            case CRYPTO_KEY: {
                c.drawCircle(20 * s, 32 * s, 11 * s, primaryStroke);
                c.drawCircle(20 * s, 32 * s, 4 * s, secondaryStroke);
                c.drawLine(31 * s, 32 * s, 56 * s, 32 * s, primaryStroke);
                c.drawLine(46 * s, 32 * s, 46 * s, 42 * s, secondaryStroke);
                c.drawLine(54 * s, 32 * s, 54 * s, 44 * s, secondaryStroke);
                break;
            }
            case PLAY_RESUME: {
                Path play = new Path();
                play.moveTo(18 * s, 12 * s);
                play.lineTo(52 * s, 32 * s);
                play.lineTo(18 * s, 52 * s);
                play.close();
                c.drawPath(play, secondaryStroke);
                break;
            }
            case PAUSE_HOLD: {
                RectF r1 = new RectF(16 * s, 12 * s, 27 * s, 52 * s);
                RectF r2 = new RectF(37 * s, 12 * s, 48 * s, 52 * s);
                c.drawRoundRect(r1, 3 * s, 3 * s, primaryStroke);
                c.drawRoundRect(r2, 3 * s, 3 * s, secondaryStroke);
                break;
            }
            case STOP_SAFE: {
                RectF stop = new RectF(14 * s, 14 * s, 50 * s, 50 * s);
                c.drawRoundRect(stop, 8 * s, 8 * s, coralStroke);
                c.drawLine(24 * s, 24 * s, 40 * s, 40 * s, coralStroke);
                c.drawLine(40 * s, 24 * s, 24 * s, 40 * s, coralStroke);
                break;
            }
            case CHECK_VERIFIED: {
                c.drawCircle(32 * s, 32 * s, 23 * s, emeraldStroke);
                c.drawLine(20 * s, 33 * s, 29 * s, 42 * s, emeraldStroke);
                c.drawLine(29 * s, 42 * s, 46 * s, 23 * s, emeraldStroke);
                break;
            }
            case ALERT_WARN: {
                Path tri = new Path();
                tri.moveTo(32 * s, 10 * s);
                tri.lineTo(56 * s, 52 * s);
                tri.lineTo(8 * s, 52 * s);
                tri.close();
                c.drawPath(tri, primaryStroke);
                c.drawLine(32 * s, 24 * s, 32 * s, 38 * s, secondaryStroke);
                c.drawCircle(32 * s, 45 * s, 2.5f * s, secondaryFill);
                break;
            }
            case UP_LEVEL: {
                c.drawLine(32 * s, 52 * s, 32 * s, 14 * s, secondaryStroke);
                c.drawLine(18 * s, 28 * s, 32 * s, 14 * s, primaryStroke);
                c.drawLine(46 * s, 28 * s, 32 * s, 14 * s, primaryStroke);
                break;
            }
            case NEW_FOLDER: {
                c.drawLine(32 * s, 14 * s, 32 * s, 50 * s, primaryStroke);
                c.drawLine(14 * s, 32 * s, 50 * s, 32 * s, secondaryStroke);
                break;
            }
            case LOG_LIST: {
                // A sheet with three lines: quiet on purpose, it is a secondary place.
                RectF sheet = new RectF(14 * s, 8 * s, 50 * s, 56 * s);
                c.drawRoundRect(sheet, 6 * s, 6 * s, quietStroke);
                c.drawLine(23 * s, 22 * s, 41 * s, 22 * s, quietStroke);
                c.drawLine(23 * s, 32 * s, 41 * s, 32 * s, quietStroke);
                c.drawLine(23 * s, 42 * s, 34 * s, 42 * s, quietStroke);
                break;
            }
            case BACK: {
                c.drawLine(50 * s, 32 * s, 16 * s, 32 * s, quietStroke);
                c.drawLine(30 * s, 18 * s, 16 * s, 32 * s, quietStroke);
                c.drawLine(30 * s, 46 * s, 16 * s, 32 * s, quietStroke);
                break;
            }
            case COPY: {
                RectF back = new RectF(10 * s, 10 * s, 38 * s, 38 * s);
                c.drawRoundRect(back, 5 * s, 5 * s, secondaryStroke);
                RectF front = new RectF(24 * s, 24 * s, 54 * s, 54 * s);
                c.drawRoundRect(front, 5 * s, 5 * s, primaryStroke);
                break;
            }
            case SHARE: {
                c.drawLine(20 * s, 32 * s, 44 * s, 18 * s, secondaryStroke);
                c.drawLine(20 * s, 32 * s, 44 * s, 46 * s, secondaryStroke);
                c.drawCircle(20 * s, 32 * s, 6.5f * s, primaryFill);
                c.drawCircle(44 * s, 18 * s, 6.5f * s, primaryFill);
                c.drawCircle(44 * s, 46 * s, 6.5f * s, primaryFill);
                break;
            }
            case EBOOT_BADGE:
            default: {
                RectF box = new RectF(8 * s, 14 * s, 56 * s, 50 * s);
                c.drawRoundRect(box, 6 * s, 6 * s, secondaryStroke);
                c.drawLine(18 * s, 26 * s, 28 * s, 32 * s, primaryStroke);
                c.drawLine(18 * s, 38 * s, 28 * s, 32 * s, primaryStroke);
                c.drawLine(32 * s, 38 * s, 46 * s, 38 * s, emeraldStroke);
                break;
            }
        }
        return bmp;
    }

    private static Paint strokePaint(int color, float width) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        return p;
    }

    private static Paint fillPaint(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        return p;
    }
}
