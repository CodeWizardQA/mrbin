// SPDX-License-Identifier: GPL-3.0-or-later
// Mr. Bin engine: Java translation of parts of shadPS4 (Copyright 2024 shadPS4 Emulator Project,
// GPL-2.0-or-later) and LibOrbisPkg (Maxton, LGPL-3.0). See the README, "License and credits".
package io.github.codewizardqa.mrbin.engine.orbis;

import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Random-access, read-only byte source. Every layer of a PKG (file → PFS image slice → XTS → PFSC → file)
 * is a Source, so a 40 GB game is streamed 64 KB at a time and never loaded into memory.
 */
abstract class Source implements Closeable {
    abstract long size();

    /** Reads exactly len bytes at pos or throws. */
    abstract void read(long pos, byte[] buf, int off, int len) throws IOException;

    @Override
    public void close() throws IOException {
    }

    final byte[] read(long pos, int len) throws IOException {
        byte[] b = new byte[len];
        read(pos, b, 0, len);
        return b;
    }

    final void checkRange(long pos, int len) throws IOException {
        if (pos < 0 || len < 0 || pos + len > size()) {
            throw new EOFException("read outside data: pos=" + pos + " len=" + len + " size=" + size());
        }
    }

    // ---------------------------------------------------------------------------------------------

    /** A file on disk, read with positional FileChannel reads (the source PKG is opened read-only). */
    static final class FileSource extends Source {
        private final FileInputStream in;
        private final FileChannel ch;
        private final long size;

        FileSource(File f) throws IOException {
            in = new FileInputStream(f);
            ch = in.getChannel();
            size = ch.size();
        }

        @Override
        long size() {
            return size;
        }

        @Override
        void read(long pos, byte[] buf, int off, int len) throws IOException {
            checkRange(pos, len);
            ByteBuffer bb = ByteBuffer.wrap(buf, off, len);
            while (bb.hasRemaining()) {
                int n = ch.read(bb, pos + bb.position() - off);
                if (n < 0) throw new EOFException("unexpected end of PKG");
            }
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }

    /** A window [offset, offset+length) of another source. */
    static final class Slice extends Source {
        private final Source base;
        private final long offset;
        private final long length;

        Slice(Source base, long offset, long length) throws IOException {
            if (offset < 0 || length < 0 || offset + length > base.size()) {
                throw new EOFException("slice outside data: offset=" + offset + " length=" + length + " size=" + base.size());
            }
            this.base = base;
            this.offset = offset;
            this.length = length;
        }

        @Override
        long size() {
            return length;
        }

        @Override
        void read(long pos, byte[] buf, int off, int len) throws IOException {
            checkRange(pos, len);
            base.read(offset + pos, buf, off, len);
        }
    }

    /**
     * A file made of whole blocks listed in order (noncontiguous file in a signed PFS image).
     * Port of LibOrbisPkg ChunkedMemoryReader.
     */
    static final class BlockList extends Source {
        private final Source base;
        private final long blockSize;
        private final long[] blocks;
        private final long length;

        BlockList(Source base, long blockSize, long[] blocks, long length) {
            this.base = base;
            this.blockSize = blockSize;
            this.blocks = blocks;
            this.length = length;
        }

        @Override
        long size() {
            return length;
        }

        @Override
        void read(long pos, byte[] buf, int off, int len) throws IOException {
            checkRange(pos, len);
            while (len > 0) {
                int idx = (int) (pos / blockSize);
                long inBlock = pos % blockSize;
                int n = (int) Math.min(len, blockSize - inBlock);
                base.read(blocks[idx] * blockSize + inBlock, buf, off, n);
                pos += n;
                off += n;
                len -= n;
            }
        }
    }

    /**
     * AES-128-XTS view of an encrypted PFS image. Sectors are 0x1000 bytes; sectors below startSector
     * (the first PFS block, which holds the plaintext superblock) are not encrypted.
     * Decrypts 64 KB at a time and keeps the last chunk.
     */
    static final class Xts extends Source {
        private static final int CHUNK = 0x10000;
        private final Source base;
        private final OrbisCrypto.Xts xts;
        private final long startSector;
        private final byte[] chunk = new byte[CHUNK];
        private long chunkStart = -1;
        private int chunkLen = 0;

        Xts(Source base, byte[] dataKey, byte[] tweakKey, long startSector) {
            this.base = base;
            this.xts = new OrbisCrypto.Xts(dataKey, tweakKey);
            this.startSector = startSector;
        }

        @Override
        long size() {
            return base.size();
        }

        private void load(long start) throws IOException {
            int len = (int) Math.min(CHUNK, base.size() - start);
            base.read(start, chunk, 0, len);
            int sectors = len / OrbisCrypto.Xts.SECTOR;
            long first = start / OrbisCrypto.Xts.SECTOR;
            // Skip the plaintext sectors at the start of the image.
            int skip = (int) Math.max(0, Math.min(sectors, startSector - first));
            if (sectors - skip > 0) {
                xts.decryptSectors(chunk, skip * OrbisCrypto.Xts.SECTOR, sectors - skip, first + skip);
            }
            chunkStart = start;
            chunkLen = len;
        }

        @Override
        void read(long pos, byte[] buf, int off, int len) throws IOException {
            checkRange(pos, len);
            while (len > 0) {
                long start = pos - (pos % CHUNK);
                if (start != chunkStart) load(start);
                int in = (int) (pos - start);
                int n = Math.min(len, chunkLen - in);
                if (n <= 0) throw new EOFException("XTS read past end");
                System.arraycopy(chunk, in, buf, off, n);
                pos += n;
                off += n;
                len -= n;
            }
        }
    }

    /**
     * PFSC container (block-compressed file). Port of LibOrbisPkg PFS/PFSCReader.cs:
     * sector size == block size → stored; > block size → zeros; < block size → zlib (2-byte header skipped, raw deflate).
     */
    static final class Pfsc extends Source {
        static final int MAGIC = 0x43534650; // "PFSC" little-endian
        private final Source base;
        private final int blockSize;
        private final long dataLength;
        private final long[] sectorMap;
        private final byte[] sector;
        private byte[] comp = new byte[0];
        private int cachedIdx = -1;
        private final Inflater inflater = new Inflater(true);

        Pfsc(Source base) throws IOException {
            this.base = base;
            byte[] h = base.read(0, 0x30);
            if (Le.i32(h, 0) != MAGIC) throw new IOException("not a PFSC container");
            if (Le.i32(h, 4) != 0) throw new IOException("PFSC: unexpected data at 0x4");
            int bs = Le.i32(h, 0x0C);
            long bs2 = Le.i64(h, 0x10);
            long offsets = Le.i64(h, 0x18);
            dataLength = Le.i64(h, 0x28);
            if (bs <= 0 || bs > (1 << 24) || bs != bs2) throw new IOException("PFSC: block size mismatch");
            if (dataLength < 0) throw new IOException("PFSC: bad data length");
            long blocks = dataLength / bs;
            if (blocks + 1 > Integer.MAX_VALUE / 8 || offsets < 0 || offsets + (blocks + 1) * 8 > base.size()) {
                throw new IOException("PFSC: sector map outside the container");
            }
            blockSize = bs;
            sectorMap = new long[(int) blocks + 1];
            byte[] map = new byte[(int) Math.min(1 << 20, sectorMap.length * 8L)];
            int done = 0;
            while (done < sectorMap.length) {
                int n = Math.min(sectorMap.length - done, map.length / 8);
                base.read(offsets + done * 8L, map, 0, n * 8);
                for (int i = 0; i < n; i++) sectorMap[done + i] = Le.i64(map, i * 8);
                done += n;
            }
            sector = new byte[blockSize];
        }

        @Override
        long size() {
            return dataLength;
        }

        private void loadSector(int idx) throws IOException {
            if (idx + 1 >= sectorMap.length) throw new IOException("PFSC: read past the last sector");
            long so = sectorMap[idx];
            long ss = sectorMap[idx + 1] - so;
            if (ss < 0 || so < 0 || so + Math.min(ss, blockSize) > base.size()) {
                throw new IOException("PFSC: corrupt sector map at " + idx);
            }
            if (ss == blockSize) {
                base.read(so, sector, 0, blockSize);
            } else if (ss > blockSize) {
                Arrays.fill(sector, (byte) 0);
            } else {
                if (ss < 2) throw new IOException("PFSC: sector " + idx + " too small");
                int n = (int) ss - 2;
                if (comp.length < n) comp = new byte[n];
                base.read(so + 2, comp, 0, n);
                inflater.reset();
                inflater.setInput(comp, 0, n);
                int got = 0;
                try {
                    while (got < blockSize) {
                        int r = inflater.inflate(sector, got, blockSize - got);
                        if (r == 0) {
                            if (inflater.finished() || inflater.needsInput() || inflater.needsDictionary()) break;
                        }
                        got += r;
                    }
                } catch (DataFormatException e) {
                    throw new IOException("PFSC: sector " + idx + " does not decompress (" + e.getMessage() + ")");
                }
                // A short stream leaves the tail as zeros, like DeflateStream.Read into a cleared buffer.
                if (got < blockSize) Arrays.fill(sector, got, blockSize, (byte) 0);
            }
            cachedIdx = idx;
        }

        @Override
        void read(long pos, byte[] buf, int off, int len) throws IOException {
            checkRange(pos, len);
            while (len > 0) {
                int idx = (int) (pos / blockSize);
                if (idx != cachedIdx) loadSector(idx);
                int in = (int) (pos % blockSize);
                int n = Math.min(len, blockSize - in);
                System.arraycopy(sector, in, buf, off, n);
                pos += n;
                off += n;
                len -= n;
            }
        }

        @Override
        public void close() {
            inflater.end();
        }
    }
}
