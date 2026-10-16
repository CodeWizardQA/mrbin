// SPDX-License-Identifier: GPL-3.0-or-later
// Mr. Bin engine: Java translation of parts of shadPS4 (Copyright 2024 shadPS4 Emulator Project,
// GPL-2.0-or-later) and LibOrbisPkg (Maxton, LGPL-3.0). See the README, "License and credits".
package io.github.codewizardqa.mrbin.engine.orbis;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Public entry point of the PS4 PKG engine: inspect a PKG and extract a fake PKG (fPKG) to a game folder.
 *
 * <p>Pipeline (shadPS4 v0.7.0 pkg.cpp, LibOrbisPkg PkgTool "pkg_extract"):
 * header + entry table → dk3 → image key → EKPFS (public fake keyset) → XTS keys from the PFS seed →
 * outer PFS (signed, encrypted) → "pfs_image.dat" → PFSC → inner PFS → every file under uroot.
 * The sce_sys entries of the PKG (param.sfo, icon0.png, …) are written to sce_sys/ like shadPS4 does.
 *
 * <p>Nothing is ever invented: every byte written comes out of the PKG. If the PKG cannot be opened the
 * method says why and writes nothing. The source file is opened read-only.
 */
public final class OrbisPkg {
    private OrbisPkg() {}

    public enum Kind {
        /** PS4 fake PKG: opens with the public fake keyset. */
        PS4_FAKE,
        /** PS4 store PKG: the image key is not made for the fake keyset; only a console with the license opens it. */
        PS4_RETAIL,
        /** PS3 package ("\x7FPKG"): not supported. */
        PS3,
        /** Not a PKG at all. */
        NOT_PKG,
        /** A PS4 fPKG whose content does not read correctly (truncated or damaged). */
        DAMAGED
    }

    /** Why a PKG got its {@link Kind}; the app turns it into text in the phone's language. */
    public enum Reason {
        OK,
        PS3,
        NOT_PS4,
        BAD_HEADER,
        /** The file is shorter than its header says; {@link Info#expectedSize} has the size it should have. */
        INCOMPLETE,
        NO_PFS,
        RETAIL,
        /** The fake keys open the image key but the decrypted content is garbage. */
        BAD_CONTENT,
        NO_PFS_IMAGE,
        INNER_DAMAGED,
        UNREADABLE
    }

    public static final class Info {
        public Kind kind = Kind.NOT_PKG;
        public Reason reasonCode = Reason.NOT_PS4;
        /** Plain-language explanation in English, for the log and the command-line tool. */
        public String reason = "";
        /** Size the header expects, for {@link Reason#INCOMPLETE}; -1 otherwise. */
        public long expectedSize = -1;
        /** Technical detail of the failing step, for the log. */
        public String detail = "";
        public String contentId = "";
        public String titleId = "";
        public String title = "";
        public String category = "";
        public String appVersion = "";
        public long pkgSize;
        public long pfsImageSize;
        public long contentFlags;
        /** Files that extraction will write (PFS files + sce_sys entries), -1 when unknown. */
        public int fileCount = -1;
        public int dirCount = -1;
        /** Bytes that extraction will write, -1 when unknown. */
        public long contentBytes = -1;

        public boolean canExtract() {
            return kind == Kind.PS4_FAKE;
        }

        /** Folder name used for the game: the title ID ("CUSA01623"), or a safe fallback. */
        public String folderName() {
            if (titleId.matches("[A-Z]{4}[0-9]{5}")) return titleId;
            String c = contentId.replaceAll("[^A-Za-z0-9_-]", "_");
            return c.isEmpty() ? "PKG" : c;
        }
    }

    public interface Listener {
        /** Called often (every file and every few MB); keep it cheap. */
        void onProgress(String currentPath, long doneBytes, long totalBytes, int filesDone, int filesTotal);

        boolean isCancelled();

        /** Blocks while the user has paused. */
        void waitWhilePaused() throws InterruptedException;
    }

    public static final class Result {
        public File gameDir;
        public int files;
        public int dirs;
        public long bytes;
        /** First 16 bytes of eboot.bin as written, hex, or "" when the game has no eboot.bin. */
        public String ebootHead = "";
        public long ebootSize = -1;
        /** sce_sys entries encrypted with a non-public key, therefore not written: "sce_sys/name (key N)". */
        public List<String> skippedEntries = new ArrayList<>();
    }

    /** Thrown when the user cancels; the partial folder has already been removed. */
    public static final class CancelledException extends IOException {
        CancelledException() {
            super("cancelled by the user");
        }
    }

    /** Thrown when the PKG cannot be extracted; {@link #info} says why. Nothing was written. */
    public static final class NotExtractableException extends IOException {
        public final Info info;

        NotExtractableException(Info info) {
            super(info.reason + (info.detail.isEmpty() ? "" : " (" + info.detail + ")"));
            this.info = info;
        }
    }

    /** The game folder already exists and has files; nothing was written. */
    public static final class DestinationNotEmptyException extends IOException {
        public final File dir;

        DestinationNotEmptyException(File dir) {
            super(dir + " already exists and is not empty. Delete it or choose another destination folder.");
            this.dir = dir;
        }
    }

    /** Not enough free space for the whole game plus a margin; nothing was written. */
    public static final class NoSpaceException extends IOException {
        public final File dir;
        public final long needed;
        public final long free;

        NoSpaceException(File dir, long needed, long free) {
            super("Not enough space: " + needed + " bytes needed and " + free + " free in " + dir + ".");
            this.dir = dir;
            this.needed = needed;
            this.free = free;
        }
    }

    // ------------------------------------------------------------------------------------------- open

    private static final String PFS_IMAGE_NAME = "pfs_image.dat";

    /** Everything needed to read the game; holds the PKG file open. */
    private static final class Opened implements Closeable {
        final Info info = new Info();
        Source.FileSource src;
        PkgFile pkg;
        PkgFile.Keys keys;
        PfsImage inner;
        Source innerSource;

        @Override
        public void close() throws IOException {
            if (innerSource != null) innerSource.close();
            if (src != null) src.close();
        }
    }

    private static Opened open(File f) throws IOException {
        Opened o = new Opened();
        Info info = o.info;
        info.pkgSize = f.length();
        o.src = new Source.FileSource(f);
        long magic = PkgFile.magicOf(o.src);
        if (magic == PkgFile.MAGIC_PS3) {
            info.kind = Kind.PS3;
            info.reasonCode = Reason.PS3;
            info.reason = "PS3 PKG: not supported in this version.";
            return o;
        }
        if (magic != PkgFile.MAGIC_PS4) {
            info.kind = Kind.NOT_PKG;
            info.reasonCode = Reason.NOT_PS4;
            info.reason = "Not a PS4 PKG (the file does not start with \\x7FCNT).";
            return o;
        }
        try {
            o.pkg = new PkgFile(o.src);
        } catch (IOException e) {
            info.kind = Kind.DAMAGED;
            info.reasonCode = Reason.BAD_HEADER;
            info.reason = "The PKG header is damaged or the file is incomplete.";
            info.detail = e.getMessage();
            return o;
        }
        PkgFile pkg = o.pkg;
        info.contentId = pkg.contentId;
        info.titleId = pkg.titleId();
        info.pfsImageSize = pkg.pfsImageSize;
        info.contentFlags = pkg.contentFlags;

        PkgFile.Entry sfoEntry = pkg.find(PkgFile.ENTRY_PARAM_SFO);
        if (sfoEntry != null && !sfoEntry.encrypted()) {
            try {
                Map<String, String> sfo = Sfo.parse(pkg.readEntry(o.src, sfoEntry));
                info.title = valueOr(sfo, "TITLE");
                info.category = valueOr(sfo, "CATEGORY");
                info.appVersion = valueOr(sfo, "APP_VER");
            } catch (IOException ignored) {
                // The title is informative only.
            }
        }

        if (pkg.packageSize > info.pkgSize || pkg.pfsImageOffset + pkg.pfsImageSize > info.pkgSize) {
            info.kind = Kind.DAMAGED;
            info.reasonCode = Reason.INCOMPLETE;
            info.expectedSize = Math.max(pkg.packageSize, pkg.pfsImageOffset + pkg.pfsImageSize);
            info.reason = "The PKG is incomplete: the file has " + info.pkgSize + " bytes and the header says "
                    + info.expectedSize + ". Was the download cut short?";
            return o;
        }
        if (pkg.pfsImageSize < 0x10000 || pkg.pfsImageOffset < 0x1000) {
            info.kind = Kind.DAMAGED;
            info.reasonCode = Reason.NO_PFS;
            info.reason = "The PKG has no PFS image (there is no content to extract).";
            return o;
        }

        o.keys = pkg.deriveFakeKeys(o.src);
        if (o.keys.ekpfs == null) {
            info.kind = Kind.PS4_RETAIL;
            info.reasonCode = Reason.RETAIL;
            info.reason = "Store PKG: it is encrypted with the account's license and only a PS4 opens it; "
                    + "this app cannot extract it.";
            info.detail = o.keys.dk3 == null
                    ? "the entry key (dk3) does not decrypt with the public key"
                    : "the image key does not decrypt with the public fPKG keyset (invalid PKCS#1 padding)";
            return o;
        }

        // Outer PFS: signed + encrypted. Its superblock (first block) is plaintext and carries the seed.
        PfsImage outer;
        try {
            Source outerRaw = new Source.Slice(o.src, pkg.pfsImageOffset, pkg.pfsImageSize);
            byte[] sb = outerRaw.read(0, 0x400);
            byte[] seed = new byte[16];
            System.arraycopy(sb, 0x370, seed, 0, 16);
            boolean newCrypt = (pkg.pfsFlags & 0x2000000000000000L) != 0;
            outer = new PfsImage(outerRaw, OrbisCrypto.pfsXtsKeys(o.keys.ekpfs, seed, newCrypt));
        } catch (IOException e) {
            info.kind = Kind.DAMAGED;
            info.reasonCode = Reason.BAD_CONTENT;
            info.reason = "The fPKG keys open the image key, but the decrypted content makes no sense: "
                    + "the PKG is damaged or is not a regular fPKG.";
            info.detail = "outer PFS: " + e.getMessage();
            return o;
        }
        PfsImage.Node imageNode = null;
        for (PfsImage.Node n : outer.uroot().children) {
            if (!n.dir && PFS_IMAGE_NAME.equals(n.name)) imageNode = n;
        }
        if (imageNode == null) {
            info.kind = Kind.DAMAGED;
            info.reasonCode = Reason.NO_PFS_IMAGE;
            info.reason = "The PKG has no pfs_image.dat (the game image).";
            return o;
        }
        try {
            Source stored = outer.openRaw(imageNode.ino);
            byte[] m = stored.read(0, 4);
            o.innerSource = Le.i32(m, 0) == Source.Pfsc.MAGIC ? new Source.Pfsc(stored) : stored;
            o.inner = new PfsImage(o.innerSource, null);
        } catch (IOException e) {
            info.kind = Kind.DAMAGED;
            info.reasonCode = Reason.INNER_DAMAGED;
            info.reason = "The inner game image (pfs_image.dat) is damaged.";
            info.detail = "inner PFS: " + e.getMessage();
            return o;
        }

        int files = 0;
        long bytes = 0;
        Set<String> paths = new HashSet<>();
        for (PfsImage.Node n : o.inner.allFiles()) {
            paths.add(n.path);
            files++;
            bytes += o.inner.logicalSize(n.ino);
        }
        for (PkgFile.Entry e : sysEntries(pkg)) {
            String p = "sce_sys/" + PkgFile.entryName(e.id);
            if (paths.add(p)) {
                files++;
                bytes += e.size;
            }
        }
        info.fileCount = files;
        info.dirCount = o.inner.allDirs().size();
        info.contentBytes = bytes;
        info.kind = Kind.PS4_FAKE;
        info.reasonCode = Reason.OK;
        info.reason = "fPKG: can be extracted.";
        return o;
    }

    private static String valueOr(Map<String, String> m, String k) {
        String v = m.get(k);
        return v == null ? "" : v.trim();
    }

    /** Entries that go to sce_sys/: named in shadPS4's table, id ≥ 0x400 (below are PKG internals: keys, digests…). */
    private static List<PkgFile.Entry> sysEntries(PkgFile pkg) {
        List<PkgFile.Entry> out = new ArrayList<>();
        for (PkgFile.Entry e : pkg.entries) {
            if (e.id < 0x400 || PkgFile.entryName(e.id) == null) continue;
            if (e.encrypted() && e.keyIndex() != 3) continue; // only key 3 (dk3) is public
            out.add(e);
        }
        return out;
    }

    /** Reads the PKG headers, keys and both PFS directory trees. Never writes anything. */
    public static Info inspect(File pkgFile) {
        try (Opened o = open(pkgFile)) {
            return o.info;
        } catch (IOException e) {
            Info i = new Info();
            i.kind = Kind.DAMAGED;
            i.reasonCode = Reason.UNREADABLE;
            i.pkgSize = pkgFile.length();
            i.reason = "The PKG could not be read.";
            i.detail = String.valueOf(e.getMessage());
            return i;
        }
    }

    // ---------------------------------------------------------------------------------------- extract

    private static final int BUF = 1 << 20;
    private static final long SPACE_MARGIN = 64L << 20;
    static final String PARTIAL_SUFFIX = ".partial";

    /**
     * Extracts a fake PKG to {@code destRoot/<TITLE_ID>/}. Writes into {@code <TITLE_ID>.partial} and
     * renames it at the end, so the game folder only appears when every file is complete. On error or
     * cancel the partial folder is deleted.
     */
    public static Result extract(File pkgFile, File destRoot, Listener l) throws IOException {
        try (Opened o = open(pkgFile)) {
            Info info = o.info;
            if (!info.canExtract()) throw new NotExtractableException(info);

            if (!destRoot.isDirectory() && !destRoot.mkdirs()) {
                throw new IOException("Could not create the destination folder " + destRoot);
            }
            File finalDir = new File(destRoot, info.folderName());
            String[] existing = finalDir.list();
            if (finalDir.exists() && (existing == null || existing.length > 0)) {
                throw new DestinationNotEmptyException(finalDir);
            }
            long free = destRoot.getUsableSpace();
            if (free < info.contentBytes + SPACE_MARGIN) {
                throw new NoSpaceException(destRoot, info.contentBytes + SPACE_MARGIN, free);
            }
            File tmp = new File(destRoot, info.folderName() + PARTIAL_SUFFIX);
            if (tmp.exists()) deleteTree(tmp);
            if (!tmp.mkdirs()) throw new IOException("Could not create " + tmp);

            boolean ok = false;
            try {
                Result r = writeAll(o, tmp, l);
                if (finalDir.exists() && !finalDir.delete()) {
                    throw new IOException("Could not replace the empty folder " + finalDir);
                }
                if (!tmp.renameTo(finalDir)) throw new IOException("Could not rename " + tmp + " to " + finalDir);
                r.gameDir = finalDir;
                ok = true;
                return r;
            } finally {
                if (!ok) deleteTree(tmp);
            }
        }
    }

    private static Result writeAll(Opened o, File root, Listener l) throws IOException {
        Info info = o.info;
        Result r = new Result();
        byte[] buf = new byte[BUF];
        long[] done = {0};
        int filesTotal = info.fileCount;

        for (PfsImage.Node d : o.inner.allDirs()) {
            File dir = new File(root, d.path);
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create " + dir);
            r.dirs++;
        }

        // 1. sce_sys entries of the PKG (shadPS4 writes these to sce_sys/; the inner PFS overrides on conflict).
        Set<String> innerPaths = new HashSet<>();
        for (PfsImage.Node n : o.inner.allFiles()) innerPaths.add(n.path);
        for (PkgFile.Entry e : o.pkg.entries) {
            String name = PkgFile.entryName(e.id);
            if (e.id >= 0x400 && name != null && e.encrypted() && e.keyIndex() != 3) {
                r.skippedEntries.add("sce_sys/" + name + " (key " + e.keyIndex() + ")");
            }
        }
        for (PkgFile.Entry e : sysEntries(o.pkg)) {
            String rel = "sce_sys/" + PkgFile.entryName(e.id);
            if (innerPaths.contains(rel)) continue;
            checkpoint(l);
            byte[] data = o.pkg.plainEntry(o.src, e, o.keys.dk3);
            if (data == null) throw new IOException("Could not decrypt the entry " + rel);
            File out = new File(root, rel);
            mkParent(out);
            try (OutputStream os = new FileOutputStream(out)) {
                os.write(data);
            }
            done[0] += data.length;
            r.files++;
            r.bytes += data.length;
            l.onProgress(rel, done[0], info.contentBytes, r.files, filesTotal);
        }

        // 2. Every file of the game, in on-disk order so the PKG is read front to back.
        for (PfsImage.Node n : o.inner.allFiles()) {
            checkpoint(l);
            long size = o.inner.logicalSize(n.ino);
            File out = new File(root, n.path);
            mkParent(out);
            Source in = o.inner.openFile(n.ino);
            try (OutputStream os = new FileOutputStream(out)) {
                long pos = 0;
                long lastReport = 0;
                while (pos < size) {
                    checkpoint(l);
                    int len = (int) Math.min(buf.length, size - pos);
                    in.read(pos, buf, 0, len);
                    if (pos == 0 && "eboot.bin".equals(n.path)) {
                        int k = Math.min(16, len);
                        r.ebootHead = Le.hex(buf, 0, k);
                    }
                    os.write(buf, 0, len);
                    pos += len;
                    done[0] += len;
                    if (pos - lastReport >= (8L << 20)) {
                        l.onProgress(n.path, done[0], info.contentBytes, r.files, filesTotal);
                        lastReport = pos;
                    }
                }
            } finally {
                in.close();
            }
            if (out.length() != size) {
                throw new IOException("Wrong size after writing " + n.path + ": " + out.length() + " of " + size);
            }
            if ("eboot.bin".equals(n.path)) r.ebootSize = size;
            r.files++;
            r.bytes += size;
            l.onProgress(n.path, done[0], info.contentBytes, r.files, filesTotal);
        }
        return r;
    }

    private static void checkpoint(Listener l) throws IOException {
        if (l.isCancelled()) throw new CancelledException();
        try {
            l.waitWhilePaused();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("interrupted");
        }
        if (l.isCancelled()) throw new CancelledException();
    }

    private static void mkParent(File f) throws IOException {
        File p = f.getParentFile();
        if (p != null && !p.isDirectory() && !p.mkdirs()) throw new IOException("No se pudo crear " + p);
    }

    static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    /** Human-readable byte count (es-CO style decimal comma). */
    public static String human(long b) {
        if (b < 0) return "?";
        String[] u = {"B", "KB", "MB", "GB", "TB"};
        double v = b;
        int i = 0;
        while (v >= 1024 && i < u.length - 1) {
            v /= 1024;
            i++;
        }
        return String.format(Locale.getDefault(), i == 0 ? "%.0f %s" : "%.2f %s", v, u[i]);
    }
}
