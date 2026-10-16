// SPDX-License-Identifier: GPL-3.0-or-later
// Mr. Bin engine: Java translation of parts of shadPS4 (Copyright 2024 shadPS4 Emulator Project,
// GPL-2.0-or-later) and LibOrbisPkg (Maxton, LGPL-3.0). See the README, "License and credits".
package io.github.codewizardqa.mrbin.engine.orbis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Read-only PFS image: superblock, dinode table and directory tree.
 * Port of LibOrbisPkg PFS/PfsReader.cs + PfsStructs.cs, cross-checked with shadPS4 v0.7.0 pfs.h.
 */
final class PfsImage {
    static final long MAGIC = 20130315L;
    static final int MODE_SIGNED = 0x1;
    static final int MODE_64BIT = 0x2;
    static final int MODE_ENCRYPTED = 0x4;

    static final int DIRENT_FILE = 2;
    static final int DIRENT_DIR = 3;
    static final int DIRENT_DOT = 4;
    static final int DIRENT_DOTDOT = 5;

    private static final int INODE_MODE_DIR = 0x4000;
    private static final int INODE_FLAG_COMPRESSED = 0x1;
    private static final int MAX_DEPTH = 64;
    private static final int MAX_NODES = 2_000_000;

    /** Plaintext PFS superblock (first 0x400 bytes of the image). */
    static final class Header {
        long version, magic;
        int mode;
        int blockSize;
        long nBlock, dinodeCount, ndBlock, dinodeBlockCount, superrootIno;
        byte[] seed = new byte[16];

        boolean signed() {
            return (mode & MODE_SIGNED) != 0;
        }

        boolean is64() {
            return (mode & MODE_64BIT) != 0;
        }

        boolean encrypted() {
            return (mode & MODE_ENCRYPTED) != 0;
        }

        static Header parse(byte[] b) throws IOException {
            Header h = new Header();
            h.version = Le.i64(b, 0x00);
            h.magic = Le.i64(b, 0x08);
            h.mode = Le.u16(b, 0x1C);
            h.blockSize = Le.i32(b, 0x20);
            h.nBlock = Le.i64(b, 0x28);
            h.dinodeCount = Le.i64(b, 0x30);
            h.ndBlock = Le.i64(b, 0x38);
            h.dinodeBlockCount = Le.i64(b, 0x40);
            h.superrootIno = Le.i64(b, 0x48);
            System.arraycopy(b, 0x370, h.seed, 0, 16);
            if (h.version != 1 || h.magic != MAGIC) {
                throw new IOException("not a PFS image (version " + h.version + ", magic " + h.magic + ")");
            }
            if (h.blockSize < 0x1000 || h.blockSize > (1 << 24) || Integer.bitCount(h.blockSize) != 1) {
                throw new IOException("PFS: invalid block size " + h.blockSize);
            }
            return h;
        }
    }

    /** One dinode. Field layout is the same for D32/S32/S64 up to Blocks (0x60). */
    static final class Inode {
        int mode;
        long flags;
        long size;           // bytes stored in the image (PFSC container size for compressed files)
        long sizeLogical;    // "SizeCompressed" in LibOrbisPkg naming: the uncompressed size
        long blocks;
        long[] db = new long[12];
        long[] ib = new long[5];

        boolean isDir() {
            return (mode & INODE_MODE_DIR) != 0;
        }

        boolean compressed() {
            return (flags & INODE_FLAG_COMPRESSED) != 0;
        }
    }

    static final class Node {
        final String name;
        final String path;  // relative to uroot, '/' separated
        final int ino;
        final boolean dir;
        final List<Node> children = new ArrayList<>();

        Node(String name, String path, int ino, boolean dir) {
            this.name = name;
            this.path = path;
            this.ino = ino;
            this.dir = dir;
        }
    }

    final Header hdr;
    private final Source img;
    private final Inode[] inodes;
    private final int inodeSize;
    private final Node uroot;
    private int nodeCount = 0;

    /**
     * @param raw    the image bytes (outer image: still encrypted)
     * @param xtsKeys {tweak, data} or null for plaintext images
     */
    PfsImage(Source raw, byte[][] xtsKeys) throws IOException {
        if (raw.size() < 0x400) throw new IOException("PFS image too small");
        hdr = Header.parse(raw.read(0, 0x400));
        if (hdr.encrypted()) {
            if (xtsKeys == null) throw new IOException("PFS image is encrypted and no key was given");
            img = new Source.Xts(raw, xtsKeys[1], xtsKeys[0], hdr.blockSize / OrbisCrypto.Xts.SECTOR);
        } else {
            img = raw;
        }
        if (hdr.signed()) {
            inodeSize = hdr.is64() ? 0x310 : 0x2C8;
        } else {
            if (hdr.is64()) throw new IOException("PFS: unsigned 64-bit images are not supported");
            inodeSize = 0xA8;
        }
        long bs = hdr.blockSize;
        if (hdr.dinodeCount <= 0 || hdr.dinodeCount > MAX_NODES) {
            throw new IOException("PFS: implausible inode count " + hdr.dinodeCount);
        }
        int perBlock = (int) (bs / inodeSize);
        if (hdr.dinodeBlockCount <= 0 || hdr.dinodeBlockCount * perBlock < hdr.dinodeCount
                || (1 + hdr.dinodeBlockCount) * bs > img.size()) {
            throw new IOException("PFS: inode table does not fit the image");
        }
        inodes = new Inode[(int) hdr.dinodeCount];
        byte[] blk = new byte[(int) bs];
        int total = 0;
        for (long i = 0; i < hdr.dinodeBlockCount && total < inodes.length; i++) {
            img.read(bs + bs * i, blk, 0, blk.length);
            for (int j = 0; j < perBlock && total < inodes.length; j++) {
                inodes[total++] = parseInode(blk, j * inodeSize);
            }
        }
        // LibOrbisPkg PfsReader: the superroot is always dinode 0.
        Node superroot = loadDir(0, "", "", new HashSet<Integer>(), 0);
        Node u = null;
        for (Node c : superroot.children) {
            if (c.dir && "uroot".equals(c.name)) u = c;
        }
        if (u == null) throw new IOException("PFS: no uroot directory");
        uroot = u;
    }

    private Inode parseInode(byte[] b, int o) {
        Inode n = new Inode();
        n.mode = Le.u16(b, o);
        n.flags = Le.u32(b, o + 4);
        n.size = Le.i64(b, o + 8);
        n.sizeLogical = Le.i64(b, o + 0x10);
        if (!hdr.signed()) {
            n.blocks = Le.u32(b, o + 0x60);
            for (int i = 0; i < 12; i++) n.db[i] = Le.i32(b, o + 0x64 + i * 4);
            for (int i = 0; i < 5; i++) n.ib[i] = Le.i32(b, o + 0x94 + i * 4);
        } else if (!hdr.is64()) {
            n.blocks = Le.u32(b, o + 0x60);
            for (int i = 0; i < 12; i++) n.db[i] = Le.i32(b, o + 0x64 + i * 36 + 32);
            for (int i = 0; i < 5; i++) n.ib[i] = Le.i32(b, o + 0x64 + 12 * 36 + i * 36 + 32);
        } else {
            n.blocks = Le.i64(b, o + 0x60);
            for (int i = 0; i < 12; i++) n.db[i] = Le.i64(b, o + 0x68 + i * 40 + 32);
            for (int i = 0; i < 5; i++) n.ib[i] = Le.i64(b, o + 0x68 + 12 * 40 + i * 40 + 32);
        }
        return n;
    }

    Inode inode(int ino) {
        return inodes[ino];
    }

    Node uroot() {
        return uroot;
    }

    private void checkBlocks(int ino, Inode n) throws IOException {
        long maxBlock = img.size() / hdr.blockSize;
        if (n.blocks < 1 || n.db[0] < 1 || n.db[0] + n.blocks > maxBlock) {
            throw new IOException("PFS: inode " + ino + " is corrupt");
        }
    }

    private Node loadDir(int ino, String name, String path, Set<Integer> seen, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("PFS: directory tree too deep");
        if (!seen.add(ino)) throw new IOException("PFS: directory loop at inode " + ino);
        Inode in = inodes[ino];
        if (!in.isDir()) throw new IOException("PFS: inode " + ino + " is not a directory");
        checkBlocks(ino, in);
        Node dir = new Node(name, path, ino, true);
        int bs = hdr.blockSize;
        byte[] blk = new byte[bs];
        List<int[]> subdirs = new ArrayList<>();
        List<String> subdirNames = new ArrayList<>();
        for (long b = 0; b < in.blocks; b++) {
            img.read((in.db[0] + b) * bs, blk, 0, bs);
            int p = 0;
            while (p + 16 <= bs) {
                long dIno = Le.u32(blk, p);
                int type = Le.i32(blk, p + 4);
                int nameLen = Le.i32(blk, p + 8);
                int entSize = Le.i32(blk, p + 12);
                if (entSize == 0) break;
                if (entSize < 16 || p + entSize > bs || nameLen < 0 || nameLen > entSize - 16) {
                    throw new IOException("PFS: corrupt directory entry in inode " + ino);
                }
                if (type == DIRENT_FILE || type == DIRENT_DIR) {
                    String n = new String(blk, p + 16, nameLen, StandardCharsets.UTF_8);
                    checkName(n);
                    if (dIno >= inodes.length) throw new IOException("PFS: entry '" + n + "' points outside the inode table");
                    if (++nodeCount > MAX_NODES) throw new IOException("PFS: too many entries");
                    String childPath = path.isEmpty() ? n : path + "/" + n;
                    if (type == DIRENT_FILE) {
                        Inode fi = inodes[(int) dIno];
                        if (fi.isDir()) throw new IOException("PFS: '" + childPath + "' is marked as file but its inode is a directory");
                        dir.children.add(new Node(n, childPath, (int) dIno, false));
                    } else {
                        subdirs.add(new int[]{(int) dIno});
                        subdirNames.add(n);
                    }
                }
                p += entSize;
            }
        }
        for (int i = 0; i < subdirs.size(); i++) {
            String n = subdirNames.get(i);
            // The superroot's children are seen from uroot's point of view: "uroot" itself is the root path.
            String childPath = path.isEmpty() && name.isEmpty() ? "" : (path.isEmpty() ? n : path + "/" + n);
            dir.children.add(loadDir(subdirs.get(i)[0], n, childPath, seen, depth + 1));
        }
        return dir;
    }

    private static void checkName(String n) throws IOException {
        if (n.isEmpty() || n.equals(".") || n.equals("..") || n.indexOf('/') >= 0 || n.indexOf('\\') >= 0
                || n.indexOf('\0') >= 0) {
            throw new IOException("PFS: unsafe file name '" + n + "'");
        }
    }

    /**
     * The bytes of a file as stored in the image (for a compressed file this is the PFSC container;
     * {@link #openFile} undoes that).
     */
    Source openStored(int ino) throws IOException {
        Inode n = inodes[ino];
        long bs = hdr.blockSize;
        long maxBlock = img.size() / bs;
        if (n.size < 0) throw new IOException("PFS: negative size in inode " + ino);
        if (n.size == 0) return new Source.Slice(img, 0, 0);
        if (n.blocks < 1 || n.blocks * bs < n.size) throw new IOException("PFS: inode " + ino + " has fewer blocks than its size");
        // LibOrbisPkg PfsReader.LoadFile: a second direct block other than -1 means a block list.
        if (n.blocks > 1 && n.db[1] != -1) {
            if (!hdr.signed()) {
                if (n.db[1] != n.db[0] + 1) throw new IOException("PFS: unsigned image with a noncontiguous file (inode " + ino + ")");
            } else {
                long[] list = blockList(ino, n);
                boolean contiguous = true;
                for (int i = 1; i < list.length; i++) {
                    if (list[i - 1] + 1 != list[i]) {
                        contiguous = false;
                        break;
                    }
                }
                if (!contiguous) {
                    for (long b : list) if (b < 1 || b >= maxBlock) throw new IOException("PFS: inode " + ino + " points outside the image");
                    return new Source.BlockList(img, bs, list, n.size);
                }
            }
        }
        checkBlocks(ino, n);
        return new Source.Slice(img, n.db[0] * bs, n.size);
    }

    /**
     * All blocks of a file (blocks × block size), without trusting its size fields. Used for pfs_image.dat,
     * which LibOrbisPkg reads through an unbounded view and hands to PFSCReader.
     */
    Source openRaw(int ino) throws IOException {
        Inode n = inodes[ino];
        long bs = hdr.blockSize;
        if (n.blocks < 1) throw new IOException("PFS: inode " + ino + " has no blocks");
        if (n.blocks > 1 && n.db[1] != -1 && hdr.signed()) {
            long[] list = blockList(ino, n);
            long max = img.size() / bs;
            for (long b : list) if (b < 1 || b >= max) throw new IOException("PFS: inode " + ino + " points outside the image");
            return new Source.BlockList(img, bs, list, n.blocks * bs);
        }
        checkBlocks(ino, n);
        return new Source.Slice(img, n.db[0] * bs, n.blocks * bs);
    }

    /** The logical (uncompressed) bytes of a file. */
    Source openFile(int ino) throws IOException {
        Inode n = inodes[ino];
        Source stored = openStored(ino);
        if (n.compressed() && n.size != n.sizeLogical) {
            Source.Pfsc p = new Source.Pfsc(stored);
            if (p.size() < n.sizeLogical) throw new IOException("PFS: compressed file shorter than its size");
            return new Source.Slice(p, 0, n.sizeLogical);
        }
        return stored;
    }

    long logicalSize(int ino) {
        Inode n = inodes[ino];
        return (n.compressed() && n.size != n.sizeLogical) ? n.sizeLogical : n.size;
    }

    /** Signed images: block numbers from direct + indirect blocks (LibOrbisPkg PfsReader.LoadFile). */
    private long[] blockList(int ino, Inode n) throws IOException {
        long bs = hdr.blockSize;
        if (n.blocks > Integer.MAX_VALUE) throw new IOException("PFS: inode " + ino + " too large");
        long[] blocks = new long[(int) n.blocks];
        int entry = hdr.is64() ? 40 : 36;
        int perBlock = (int) (bs / entry);
        long remaining = n.blocks;
        for (int i = 0; i < 12 && i < remaining; i++) blocks[i] = n.db[i];
        remaining -= 12;
        int idx = 12;
        for (int i = 0; i < remaining && i < perBlock; i++) {
            blocks[idx + i] = readBlockNo(n.ib[0] * bs + (long) i * entry + 32);
        }
        remaining -= perBlock;
        idx += perBlock;
        for (int j = 0; (long) j * perBlock < remaining; j++) {
            long indirect = readBlockNo(n.ib[1] * bs + (long) j * entry + 32);
            for (int i = 0; i < perBlock && i + (long) j * perBlock < remaining; i++) {
                blocks[idx + i] = readBlockNo(indirect * bs + (long) i * entry + 32);
            }
            idx += perBlock;
        }
        return blocks;
    }

    private final byte[] numBuf = new byte[8];

    private long readBlockNo(long pos) throws IOException {
        if (hdr.is64()) {
            img.read(pos, numBuf, 0, 8);
            return Le.i64(numBuf, 0);
        }
        img.read(pos, numBuf, 0, 4);
        return Le.i32(numBuf, 0);
    }

    /** All files under uroot, in on-disk order (sorted by first block) so the PKG is read front to back. */
    List<Node> allFiles() {
        List<Node> out = new ArrayList<>();
        collect(uroot, out);
        java.util.Collections.sort(out, (a, b) -> Long.compare(inodes[a.ino].db[0], inodes[b.ino].db[0]));
        return out;
    }

    List<Node> allDirs() {
        List<Node> out = new ArrayList<>();
        collectDirs(uroot, out);
        return out;
    }

    private static void collect(Node d, List<Node> out) {
        for (Node c : d.children) {
            if (c.dir) collect(c, out);
            else out.add(c);
        }
    }

    private static void collectDirs(Node d, List<Node> out) {
        for (Node c : d.children) {
            if (c.dir) {
                out.add(c);
                collectDirs(c, out);
            }
        }
    }
}
