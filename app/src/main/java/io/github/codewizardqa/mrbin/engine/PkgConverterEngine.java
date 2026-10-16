package io.github.codewizardqa.mrbin.engine;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;

import io.github.codewizardqa.mrbin.R;
import io.github.codewizardqa.mrbin.engine.PkgModels.ActiveConversionProgress;
import io.github.codewizardqa.mrbin.engine.PkgModels.ConversionPhase;
import io.github.codewizardqa.mrbin.engine.PkgModels.ConversionResultItem;
import io.github.codewizardqa.mrbin.engine.PkgModels.DiscoveredPkgItem;
import io.github.codewizardqa.mrbin.engine.orbis.OrbisPkg;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Batch driver around {@link OrbisPkg}: scans the source folder, extracts the selected PKGs one after the
 * other in a background thread, and keeps a plain-text log the user can copy and send.
 * Messages for the user come from the app's strings (English or Spanish); the log is always in English
 * so any log pasted into an issue reads the same.
 */
public final class PkgConverterEngine {

    public interface Listener {
        void onStateChanged();

        void onMessage(String message);
    }

    private final Context ctx;
    private final SettingsStore settings;
    private final File logFile;
    private final String version;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private volatile List<DiscoveredPkgItem> discovered = Collections.emptyList();
    private volatile ActiveConversionProgress progress = new ActiveConversionProgress();
    private final List<ConversionResultItem> results = new CopyOnWriteArrayList<>();
    private volatile boolean scanning;
    private volatile boolean cancelRequested;
    private volatile boolean paused;
    private final Object pauseLock = new Object();

    public PkgConverterEngine(Context ctx, SettingsStore settings, File logFile, String version) {
        this.ctx = ctx.getApplicationContext();
        this.settings = settings;
        this.logFile = logFile;
        this.version = version;
    }

    // ---------------------------------------------------------------------------------------- listeners

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void fireState() {
        for (Listener l : listeners) l.onStateChanged();
    }

    private void fireMessage(String m) {
        for (Listener l : listeners) l.onMessage(m);
    }

    // ------------------------------------------------------------------------------------------ getters

    public List<DiscoveredPkgItem> getDiscovered() {
        return discovered;
    }

    public ActiveConversionProgress getProgress() {
        return progress;
    }

    public List<ConversionResultItem> getResults() {
        return results;
    }

    public boolean isScanning() {
        return scanning;
    }

    public File getLogFile() {
        return logFile;
    }

    // --------------------------------------------------------------------------------------------- scan

    /** Lists the .pkg files of the folder and reads each one's headers (read-only). */
    public void scanSourceFolder(File customDir) {
        if (progress.running || scanning) return;
        File dir = customDir != null ? customDir : new File(settings.getSourceDirPath());
        scanning = true;
        fireState();
        worker.execute(() -> {
            List<DiscoveredPkgItem> out = new ArrayList<>();
            try {
                File[] files = dir.listFiles();
                if (files == null) {
                    log("Scan: could not read " + dir + " (file access permission?)");
                    fireMessage(ctx.getString(R.string.msg_cannot_read_folder, dir.getPath()));
                } else {
                    Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                    for (File f : files) {
                        if (!f.isFile() || !f.getName().toLowerCase(Locale.ROOT).endsWith(".pkg")) continue;
                        OrbisPkg.Info info = OrbisPkg.inspect(f);
                        log("Scan: " + f.getName() + " (" + f.length() + " bytes) → " + info.kind
                                + " · " + info.reason + (info.detail.isEmpty() ? "" : " · " + info.detail)
                                + (info.contentId.isEmpty() ? "" : " · " + info.contentId));
                        out.add(new DiscoveredPkgItem(f, info));
                    }
                }
            } catch (Throwable t) {
                log("Scan failed: " + stack(t));
            } finally {
                discovered = out;
                scanning = false;
                fireState();
            }
        });
    }

    public void toggleSelection(String itemId) {
        if (progress.running) return;
        for (DiscoveredPkgItem it : discovered) {
            if (it.id.equals(itemId) && it.info.canExtract()) it.selected = !it.selected;
        }
        fireState();
    }

    public void selectAll(boolean select) {
        if (progress.running) return;
        for (DiscoveredPkgItem it : discovered) it.selected = select && it.info.canExtract();
        fireState();
    }

    // ------------------------------------------------------------------------------------------ control

    public void setPaused(boolean pause) {
        synchronized (pauseLock) {
            paused = pause;
            pauseLock.notifyAll();
        }
        ActiveConversionProgress p = progress;
        if (p.running) {
            progress = new ActiveConversionProgress(p.running, pause, p.phase, p.currentItem, p.currentIndex,
                    p.totalItems, p.batchBytesDone, p.batchBytesTotal, 0L, p.etaSeconds, p.elapsedMillis,
                    pause ? ctx.getString(R.string.status_paused) : p.statusDetail,
                    p.filesDone, p.filesTotal, p.currentPath);
        }
        fireState();
    }

    public void cancelConversion() {
        cancelRequested = true;
        setPaused(false);
    }

    // ------------------------------------------------------------------------------------------ extract

    public void startBatchConversion(Runnable onServiceStartRequested) {
        if (progress.running || scanning) return;
        List<DiscoveredPkgItem> batch = new ArrayList<>();
        long total = 0;
        for (DiscoveredPkgItem it : discovered) {
            if (it.selected && it.info.canExtract()) {
                batch.add(it);
                total += Math.max(0, it.info.contentBytes);
            }
        }
        if (batch.isEmpty()) return;
        cancelRequested = false;
        paused = false;
        final long batchTotal = total;
        progress = new ActiveConversionProgress(true, false, ConversionPhase.READING, batch.get(0), 1,
                batch.size(), 0L, batchTotal, 0L, -1L, 0L,
                ctx.getString(R.string.status_opening, batch.get(0).pkgFile.getName()));
        fireState();
        if (onServiceStartRequested != null) onServiceStartRequested.run();
        worker.execute(() -> runBatch(batch, batchTotal));
    }

    private void runBatch(List<DiscoveredPkgItem> batch, long batchTotal) {
        File dest = new File(settings.getDestDirPath());
        final long t0 = SystemClock.elapsedRealtime();
        long before = 0;
        boolean anyError = false;
        boolean cancelled = false;
        log("=== " + ctx.getString(R.string.app_name) + " " + version + " · " + Build.MANUFACTURER + " " + Build.MODEL
                + " · Android " + Build.VERSION.RELEASE + " · destination " + dest + " · free " + dest.getUsableSpace()
                + " bytes");

        for (int i = 0; i < batch.size(); i++) {
            DiscoveredPkgItem it = batch.get(i);
            final int index = i + 1;
            final long base = before;
            final long itemStart = SystemClock.elapsedRealtime();
            log("Extract " + index + "/" + batch.size() + ": " + it.pkgFile + " → " + new File(dest, it.info.folderName()));
            try {
                final long[] lastBytes = {0};
                final long[] lastT = {itemStart};
                final long[] speed = {0};
                final long[] lastFire = {0};
                OrbisPkg.Result r = OrbisPkg.extract(it.pkgFile, dest, new OrbisPkg.Listener() {
                    @Override
                    public void onProgress(String path, long done, long total, int filesDone, int filesTotal) {
                        long now = SystemClock.elapsedRealtime();
                        if (now - lastT[0] >= 1000) {
                            long s = (done - lastBytes[0]) * 1000L / (now - lastT[0]);
                            speed[0] = speed[0] == 0 ? s : (speed[0] * 2 + s) / 3;
                            lastBytes[0] = done;
                            lastT[0] = now;
                        }
                        long batchDone = base + done;
                        long eta = speed[0] > 0 ? (batchTotal - batchDone) / speed[0] : -1L;
                        progress = new ActiveConversionProgress(true, paused, ConversionPhase.EXTRACTING, it, index,
                                batch.size(), batchDone, batchTotal, speed[0], eta, now - t0,
                                filesDone + "/" + filesTotal + " · " + path, filesDone, filesTotal, path);
                        if (now - lastFire[0] >= 250) {
                            lastFire[0] = now;
                            fireState();
                        }
                    }

                    @Override
                    public boolean isCancelled() {
                        return cancelRequested;
                    }

                    @Override
                    public void waitWhilePaused() throws InterruptedException {
                        synchronized (pauseLock) {
                            while (paused && !cancelRequested) pauseLock.wait(500);
                        }
                    }
                });
                long ms = SystemClock.elapsedRealtime() - itemStart;
                results.add(0, ConversionResultItem.ok(it, r, ms));
                log("OK " + it.pkgFile.getName() + ": " + r.files + " files, " + r.dirs + " folders, " + r.bytes
                        + " bytes in " + ms + " ms → " + r.gameDir + " · eboot.bin " + r.ebootSize + " bytes, starts "
                        + r.ebootHead + (r.skippedEntries.isEmpty() ? "" : " · not written: " + r.skippedEntries));
            } catch (OrbisPkg.CancelledException c) {
                cancelled = true;
                log("Cancelled in " + it.pkgFile.getName() + "; the partial folder was deleted.");
                break;
            } catch (Throwable t) {
                anyError = true;
                long ms = SystemClock.elapsedRealtime() - itemStart;
                String msg = userMessage(t);
                results.add(0, ConversionResultItem.failed(it, msg, ms));
                log("ERROR " + it.pkgFile.getName() + ": " + stack(t));
            }
            before += Math.max(0, it.info.contentBytes);
        }

        long elapsed = SystemClock.elapsedRealtime() - t0;
        ConversionPhase end = cancelled ? ConversionPhase.CANCELLED
                : anyError ? ConversionPhase.ERROR : ConversionPhase.DONE;
        String detail = cancelled ? ctx.getString(R.string.status_cancelled)
                : anyError ? ctx.getString(R.string.status_errors)
                : ctx.getResources().getQuantityString(R.plurals.status_done, batch.size(), dest.getPath());
        ActiveConversionProgress p = progress;
        progress = new ActiveConversionProgress(false, false, end, p.currentItem, p.currentIndex, batch.size(),
                cancelled ? p.batchBytesDone : batchTotal, batchTotal, 0L, 0L, elapsed, detail,
                cancelled || anyError ? p.filesDone : p.filesTotal, p.filesTotal, "");
        fireState();
        fireMessage(detail);
        scanSourceFolder(null);
    }

    // ---------------------------------------------------------------------------------------------- log

    private static final long LOG_MAX = 512 * 1024;

    public synchronized void log(String line) {
        if (logFile == null) return;
        try {
            File parent = logFile.getParentFile();
            if (parent != null) parent.mkdirs();
            if (logFile.length() > LOG_MAX) {
                File old = new File(logFile.getPath() + ".1");
                old.delete();
                logFile.renameTo(old);
            }
            String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            try (Writer w = new FileWriter(logFile, true)) {
                w.write(ts + " " + line + "\n");
            }
        } catch (IOException ignored) {
        }
    }

    /** The last part of the log, for the "copy log" button. */
    public String readLogTail(int maxChars) {
        try {
            String s = new String(Files.readAllBytes(logFile.toPath()), StandardCharsets.UTF_8);
            return s.length() <= maxChars ? s : s.substring(s.length() - maxChars);
        } catch (IOException e) {
            return ctx.getString(R.string.log_empty);
        }
    }

    /** The error as the user reads it in Results: the two common cases in their language, the rest as is. */
    private String userMessage(Throwable t) {
        if (t instanceof OrbisPkg.DestinationNotEmptyException) {
            return ctx.getString(R.string.err_dest_not_empty, ((OrbisPkg.DestinationNotEmptyException) t).dir.getPath());
        }
        if (t instanceof OrbisPkg.NoSpaceException) {
            OrbisPkg.NoSpaceException e = (OrbisPkg.NoSpaceException) t;
            return ctx.getString(R.string.err_no_space, OrbisPkg.human(e.needed), OrbisPkg.human(e.free), e.dir.getPath());
        }
        return t instanceof IOException ? String.valueOf(t.getMessage()) : t.toString();
    }

    private static String stack(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        String s = sw.toString();
        return s.length() > 4000 ? s.substring(0, 4000) : s;
    }
}
