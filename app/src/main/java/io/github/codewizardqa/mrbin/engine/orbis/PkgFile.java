// SPDX-License-Identifier: GPL-3.0-or-later
// Mr. Bin engine: Java translation of parts of shadPS4 (Copyright 2024 shadPS4 Emulator Project,
// GPL-2.0-or-later) and LibOrbisPkg (Maxton, LGPL-3.0). See the README, "License and credits".
package io.github.codewizardqa.mrbin.engine.orbis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * PS4 "\x7FCNT" package: big-endian header, entry table and the fake-PKG key chain.
 * Port of shadPS4 v0.7.0 src/core/file_format/pkg.{h,cpp} and LibOrbisPkg PKG/PkgReader.cs, Pkg.cs, Entry.cs.
 */
final class PkgFile {
    static final long MAGIC_PS4 = 0x7F434E54L; // "\x7FCNT"
    static final long MAGIC_PS3 = 0x7F504B47L; // "\x7FPKG"

    static final int ENTRY_DIGESTS = 0x0001;
    static final int ENTRY_ENTRY_KEYS = 0x0010;
    static final int ENTRY_IMAGE_KEY = 0x0020;
    static final int ENTRY_GENERAL_DIGESTS = 0x0080;
    static final int ENTRY_METAS = 0x0100;
    static final int ENTRY_NAMES = 0x0200;
    static final int ENTRY_PARAM_SFO = 0x1000;

    static final class Entry {
        int id;
        long nameOffset, flags1, flags2, offset, size;
        byte[] raw32 = new byte[32]; // the 32-byte table record; part of the entry IV (shadPS4: memcpy(&entry))

        boolean encrypted() {
            return (flags1 & 0x80000000L) != 0;
        }

        int keyIndex() {
            return (int) ((flags2 & 0xF000) >> 12);
        }
    }

    final long fileSize;
    final long pkgType;
    final long entryCount;
    final long entryTableOffset;
    final String contentId;
    final long drmType, contentType, contentFlags;
    final long pfsImageCount;
    final long pfsFlags, pfsImageOffset, pfsImageSize;
    final long packageSize;
    final List<Entry> entries = new ArrayList<>();

    PkgFile(Source s) throws IOException {
        fileSize = s.size();
        if (fileSize < 0x1000) throw new IOException("file too small for a PS4 PKG header");
        byte[] h = s.read(0, 0x1000);
        long magic = Le.be32(h, 0);
        if (magic != MAGIC_PS4) throw new IOException("not a PS4 PKG");
        pkgType = Le.be32(h, 0x04);
        entryCount = Le.be32(h, 0x10);
        entryTableOffset = Le.be32(h, 0x18);
        contentId = cstr(h, 0x40, 0x24);
        drmType = Le.be32(h, 0x70);
        contentType = Le.be32(h, 0x74);
        contentFlags = Le.be32(h, 0x78);
        pfsImageCount = Le.be32(h, 0x404);
        pfsFlags = Le.be64(h, 0x408);
        pfsImageOffset = Le.be64(h, 0x410);
        pfsImageSize = Le.be64(h, 0x418);
        packageSize = Le.be64(h, 0x430);

        if (entryCount > 65536 || entryTableOffset + entryCount * 32 > fileSize) {
            throw new IOException("PKG entry table is outside the file");
        }
        byte[] t = s.read(entryTableOffset, (int) entryCount * 32);
        for (int i = 0; i < entryCount; i++) {
            Entry e = new Entry();
            int o = i * 32;
            e.id = (int) Le.be32(t, o);
            e.nameOffset = Le.be32(t, o + 4);
            e.flags1 = Le.be32(t, o + 8);
            e.flags2 = Le.be32(t, o + 12);
            e.offset = Le.be32(t, o + 16);
            e.size = Le.be32(t, o + 20);
            // The IV input is the record as LibOrbisPkg MetaEntry.GetBytes / shadPS4 PKGEntry{} rebuild it:
            // the six big-endian fields followed by 8 zero bytes (the padding in the file is not used).
            System.arraycopy(t, o, e.raw32, 0, 24);
            entries.add(e);
        }
    }

    static long magicOf(Source s) throws IOException {
        if (s.size() < 4) return -1;
        return Le.be32(s.read(0, 4), 0);
    }

    private static String cstr(byte[] b, int off, int max) {
        int n = 0;
        while (n < max && b[off + n] != 0) n++;
        return new String(b, off, n, StandardCharsets.US_ASCII);
    }

    String titleId() {
        // Content ID: "UP9000-CUSA01623_00-GOD0OF0WAR0III0R" → characters 7..16.
        return contentId.length() >= 16 ? contentId.substring(7, 16) : "";
    }

    Entry find(int id) {
        for (Entry e : entries) if (e.id == id) return e;
        return null;
    }

    byte[] readEntry(Source s, Entry e) throws IOException {
        if (e.size > 64L * 1024 * 1024 || e.offset + e.size > fileSize) {
            throw new IOException("PKG entry 0x" + Integer.toHexString(e.id) + " is outside the file");
        }
        return s.read(e.offset, (int) e.size);
    }

    // --------------------------------------------------------------------------------------------- keys

    /** Result of the fake-key chain; any null field means that step failed. */
    static final class Keys {
        byte[] dk3;
        byte[] ekpfs;
    }

    /**
     * dk3 = RSA(PkgDerivedKey3, ENTRY_KEYS.key[3]);
     * ivKey = SHA256(IMAGE_KEY table record || dk3); imageKey = AES-CBC(ivKey, IMAGE_KEY data);
     * EKPFS = RSA(FakeKeyset, imageKey). (shadPS4 PKG::Extract, LibOrbisPkg Pkg.GetEkpfs)
     */
    Keys deriveFakeKeys(Source s) throws IOException {
        Keys k = new Keys();
        Entry ek = find(ENTRY_ENTRY_KEYS);
        Entry ik = find(ENTRY_IMAGE_KEY);
        if (ek == null || ik == null) return k;
        byte[] keys = readEntry(s, ek);
        // seed digest (32) + 7 digests (7*32) + 7 keys (7*256)
        int keyOff = 32 + 7 * 32 + 3 * 256;
        if (keys.length < keyOff + 256) return k;
        byte[] key3 = new byte[256];
        System.arraycopy(keys, keyOff, key3, 0, 256);
        byte[] dk3 = OrbisCrypto.rsaDecryptPkcs1(key3, OrbisKeys.PKG_DERIVED_KEY3);
        if (dk3 == null || dk3.length != 32) return k;
        k.dk3 = dk3;

        byte[] img = readEntry(s, ik);
        if (img.length < 256) return k;
        byte[] img256 = new byte[256];
        System.arraycopy(img, 0, img256, 0, 256);
        byte[] ivKey = OrbisCrypto.sha256(ik.raw32, dk3);
        byte[] imageKey = OrbisCrypto.aesCbcDecrypt(ivKey, img256);
        byte[] ekpfs = OrbisCrypto.rsaDecryptPkcs1(imageKey, OrbisKeys.FAKE);
        if (ekpfs == null || ekpfs.length != 32) return k;
        k.ekpfs = ekpfs;
        return k;
    }

    /**
     * Plain bytes of an entry. Encrypted entries with key index 3 are decrypted with dk3 over the size
     * rounded up to 16 and cut back to the entry size (LibOrbisPkg pkg_extractentry, Entry.Decrypt).
     * Returns null for an encrypted entry with another key index (that key is not public).
     */
    byte[] plainEntry(Source s, Entry e, byte[] dk3) throws IOException {
        if (!e.encrypted()) return readEntry(s, e);
        if (e.keyIndex() != 3 || dk3 == null) return null;
        long padded = (e.size + 15) & ~15L;
        if (padded > 64L * 1024 * 1024 || e.offset + padded > fileSize) {
            throw new IOException("PKG entry 0x" + Integer.toHexString(e.id) + " is outside the file");
        }
        byte[] enc = s.read(e.offset, (int) padded);
        byte[] dec = OrbisCrypto.aesCbcDecrypt(OrbisCrypto.sha256(e.raw32, dk3), enc);
        byte[] out = new byte[(int) e.size];
        System.arraycopy(dec, 0, out, 0, out.length);
        return out;
    }

    // --------------------------------------------------------------------------------------------- names

    /** Entry file name from shadPS4's table (see {@link PkgEntryNames}), or null. */
    static String entryName(int id) {
        return PkgEntryNames.of(id);
    }
}
