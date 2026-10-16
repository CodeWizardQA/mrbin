package io.github.codewizardqa.mrbin.engine;

import io.github.codewizardqa.mrbin.engine.orbis.OrbisPkg;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** State shown by the UI and the notification. Everything here describes real work on real files. */
public final class PkgModels {
    private PkgModels() {}

    /** A .pkg found in the source folder, with what the engine read from it. */
    public static final class DiscoveredPkgItem {
        public final String id;
        public final File pkgFile;
        public final OrbisPkg.Info info;
        public boolean selected;

        public DiscoveredPkgItem(File pkgFile, OrbisPkg.Info info) {
            this.id = pkgFile.getAbsolutePath();
            this.pkgFile = pkgFile;
            this.info = info;
            this.selected = info.canExtract();
        }

        public String displayName() {
            if (!info.title.isEmpty()) {
                return info.title + (info.titleId.isEmpty() ? "" : " (" + info.titleId + ")");
            }
            return pkgFile.getName();
        }
    }

    /** Where a batch is. The app shows each phase in the phone's language (ui/Texts). */
    public enum ConversionPhase {
        IDLE,
        READING,
        EXTRACTING,
        DONE,
        CANCELLED,
        ERROR
    }

    public static final class ActiveConversionProgress {
        public final boolean running;
        public final boolean paused;
        public final ConversionPhase phase;
        public final DiscoveredPkgItem currentItem;
        public final int currentIndex;
        public final int totalItems;
        public final long batchBytesDone;
        public final long batchBytesTotal;
        public final long currentSpeedBps;
        public final long etaSeconds;
        public final long elapsedMillis;
        public final String statusDetail;
        /** Files of the current PKG written so far and in total; -1 while unknown (reading, before the first file). */
        public final int filesDone;
        public final int filesTotal;
        /** Path inside the game folder of the file being written; empty outside {@link ConversionPhase#EXTRACTING}. */
        public final String currentPath;

        public ActiveConversionProgress() {
            this(false, false, ConversionPhase.IDLE, null, 0, 0, 0L, 0L, 0L, -1L, 0L, "");
        }

        public ActiveConversionProgress(
                boolean running,
                boolean paused,
                ConversionPhase phase,
                DiscoveredPkgItem currentItem,
                int currentIndex,
                int totalItems,
                long batchBytesDone,
                long batchBytesTotal,
                long currentSpeedBps,
                long etaSeconds,
                long elapsedMillis,
                String statusDetail
        ) {
            this(running, paused, phase, currentItem, currentIndex, totalItems, batchBytesDone, batchBytesTotal,
                    currentSpeedBps, etaSeconds, elapsedMillis, statusDetail, -1, -1, "");
        }

        public ActiveConversionProgress(
                boolean running,
                boolean paused,
                ConversionPhase phase,
                DiscoveredPkgItem currentItem,
                int currentIndex,
                int totalItems,
                long batchBytesDone,
                long batchBytesTotal,
                long currentSpeedBps,
                long etaSeconds,
                long elapsedMillis,
                String statusDetail,
                int filesDone,
                int filesTotal,
                String currentPath
        ) {
            this.filesDone = filesDone;
            this.filesTotal = filesTotal;
            this.currentPath = currentPath == null ? "" : currentPath;
            this.running = running;
            this.paused = paused;
            this.phase = phase;
            this.currentItem = currentItem;
            this.currentIndex = currentIndex;
            this.totalItems = totalItems;
            this.batchBytesDone = batchBytesDone;
            this.batchBytesTotal = batchBytesTotal;
            this.currentSpeedBps = currentSpeedBps;
            this.etaSeconds = etaSeconds;
            this.elapsedMillis = elapsedMillis;
            this.statusDetail = statusDetail;
        }

        public float batchProgressFloat() {
            if (batchBytesTotal <= 0L) return 0f;
            return Math.max(0f, Math.min(1f, batchBytesDone / (float) batchBytesTotal));
        }

        public int batchPercentInt() {
            return Math.round(batchProgressFloat() * 100f);
        }
    }

    /** Outcome of one PKG. */
    public static final class ConversionResultItem {
        public final String sourcePkgName;
        public final String sourcePkgPath;
        public final boolean success;
        public final String title;
        public final String gameDirPath;
        public final int files;
        public final long bytes;
        public final String ebootHead;
        public final long ebootSize;
        public final List<String> skipped;
        public final String errorMessage;
        public final long durationMillis;

        private ConversionResultItem(String sourcePkgName, String sourcePkgPath, boolean success, String title,
                                     String gameDirPath, int files, long bytes, String ebootHead, long ebootSize,
                                     List<String> skipped, String errorMessage, long durationMillis) {
            this.sourcePkgName = sourcePkgName;
            this.sourcePkgPath = sourcePkgPath;
            this.success = success;
            this.title = title;
            this.gameDirPath = gameDirPath;
            this.files = files;
            this.bytes = bytes;
            this.ebootHead = ebootHead;
            this.ebootSize = ebootSize;
            this.skipped = skipped;
            this.errorMessage = errorMessage;
            this.durationMillis = durationMillis;
        }

        public static ConversionResultItem ok(DiscoveredPkgItem it, OrbisPkg.Result r, long ms) {
            return new ConversionResultItem(it.pkgFile.getName(), it.pkgFile.getAbsolutePath(), true, it.displayName(),
                    r.gameDir.getAbsolutePath(), r.files, r.bytes, r.ebootHead, r.ebootSize,
                    new ArrayList<>(r.skippedEntries), null, ms);
        }

        public static ConversionResultItem failed(DiscoveredPkgItem it, String message, long ms) {
            return new ConversionResultItem(it.pkgFile.getName(), it.pkgFile.getAbsolutePath(), false, it.displayName(),
                    null, 0, 0L, "", -1L, new ArrayList<>(), message, ms);
        }

        /** Orbis SELF magic 4F 15 3D 1D: what the real eboot.bin of a PS4 game starts with. */
        public boolean ebootIsSelf() {
            return ebootHead.startsWith("4F 15 3D 1D");
        }
    }

    public static final class Formatters {
        private Formatters() {}

        public static String humanBytes(long bytes) {
            return OrbisPkg.human(bytes);
        }

        public static String humanSpeed(long bps) {
            if (bps <= 0L) return "—";
            return humanBytes(bps) + "/s";
        }

        /** For seconds ≥ 0; the app shows its own "calculating…" when the ETA is still unknown (-1). */
        public static String humanEta(long seconds) {
            if (seconds < 0L) return "…";
            if (seconds == 0L) return "0 s";
            long h = seconds / 3600L;
            long m = (seconds % 3600L) / 60L;
            long s = seconds % 60L;
            if (h > 0L) return h + " h " + m + " min";
            if (m > 0L) return m + " min " + s + " s";
            return s + " s";
        }

        public static String humanDuration(long ms) {
            if (ms < 1000L) return ms + " ms";
            long s = ms / 1000L;
            if (s < 60L) return String.format(Locale.getDefault(), "%.1f s", ms / 1000.0);
            return (s / 60L) + " min " + (s % 60L) + " s";
        }
    }
}
