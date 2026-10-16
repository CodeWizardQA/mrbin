// SPDX-License-Identifier: GPL-3.0-or-later
// Mr. Bin engine: Java translation of parts of shadPS4 (Copyright 2024 shadPS4 Emulator Project,
// GPL-2.0-or-later) and LibOrbisPkg (Maxton, LGPL-3.0). See the README, "License and credits".
package io.github.codewizardqa.mrbin.engine.orbis;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Crypto primitives of the PS4 fake-PKG path, ported from shadPS4 v0.7.0 (src/core/crypto/crypto.cpp)
 * and LibOrbisPkg (Util/Crypto.cs, PFS/XtsDecryptReader.cs).
 */
final class OrbisCrypto {
    private OrbisCrypto() {}

    /**
     * RSA-2048 private-key decryption (CRT) followed by a strict PKCS#1 v1.5 type 2 unpadding.
     * Returns null when the padding is not valid, which is what happens when the ciphertext was
     * not made for this key (a retail PKG decrypted with the public fake keys).
     */
    static byte[] rsaDecryptPkcs1(byte[] ciphertext, OrbisKeys.RsaKeyset k) {
        if (ciphertext.length != 256) return null;
        BigInteger c = new BigInteger(1, ciphertext);
        if (c.compareTo(k.n) >= 0) return null;
        BigInteger m1 = c.mod(k.p).modPow(k.dp, k.p);
        BigInteger m2 = c.mod(k.q).modPow(k.dq, k.q);
        BigInteger h = k.qinv.multiply(m1.subtract(m2)).mod(k.p);
        BigInteger m = m2.add(h.multiply(k.q));
        byte[] em = toFixed(m, 256);
        if (em[0] != 0 || em[1] != 2) return null;
        int i = 2;
        while (i < em.length && em[i] != 0) i++;
        // At least 8 non-zero padding bytes, and a zero separator.
        if (i >= em.length || i < 10) return null;
        byte[] out = new byte[em.length - i - 1];
        System.arraycopy(em, i + 1, out, 0, out.length);
        return out;
    }

    static byte[] toFixed(BigInteger v, int len) {
        byte[] raw = v.toByteArray();
        byte[] out = new byte[len];
        if (raw.length > len) {
            System.arraycopy(raw, raw.length - len, out, 0, len);
        } else {
            System.arraycopy(raw, 0, out, len - raw.length, raw.length);
        }
        return out;
    }

    static byte[] sha256(byte[]... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (byte[] p : parts) md.update(p);
            return md.digest();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** AES-128-CBC without padding; key = ivKey[16..32], iv = ivKey[0..16] (shadPS4 aesCbcCfb128Decrypt). */
    static byte[] aesCbcDecrypt(byte[] ivKey, byte[] data) {
        try {
            Cipher c = Cipher.getInstance("AES/CBC/NoPadding");
            byte[] key = new byte[16];
            byte[] iv = new byte[16];
            System.arraycopy(ivKey, 16, key, 0, 16);
            System.arraycopy(ivKey, 0, iv, 0, 16);
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            return c.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * PFS XTS keys: HMAC-SHA256(EKPFS, index=1 (LE u32) || seed) → tweak = [0..16], data = [16..32].
     * With the "new crypt" PKG flag (pfs_flags bit 61) the HMAC key is HMAC-SHA256(EKPFS, seed) (LibOrbisPkg PfsGenEncKey).
     * Returns {tweakKey, dataKey}.
     */
    static byte[][] pfsXtsKeys(byte[] ekpfs, byte[] seed, boolean newCrypt) {
        byte[] key = newCrypt ? hmacSha256(ekpfs, seed) : ekpfs;
        byte[] d = new byte[4 + seed.length];
        d[0] = 1;
        System.arraycopy(seed, 0, d, 4, seed.length);
        byte[] enc = hmacSha256(key, d);
        byte[] tweak = new byte[16];
        byte[] data = new byte[16];
        System.arraycopy(enc, 0, tweak, 0, 16);
        System.arraycopy(enc, 16, data, 0, 16);
        return new byte[][]{tweak, data};
    }

    /**
     * AES-128-XTS decryption of whole 0x1000-byte sectors, built on AES/ECB/NoPadding.
     * The tweak of sector n is AES(tweakKey, n as little-endian u64 || 8 zero bytes), multiplied by x in
     * GF(2^128) (polynomial 0x87, little-endian) for each 16-byte block, exactly as XtsDecryptReader.cs.
     */
    static final class Xts {
        static final int SECTOR = 0x1000;
        private final Cipher dataDec;
        private final Cipher tweakEnc;
        private byte[] tweaks = new byte[0];
        private byte[] tweakIn = new byte[0];

        Xts(byte[] dataKey, byte[] tweakKey) {
            try {
                dataDec = Cipher.getInstance("AES/ECB/NoPadding");
                dataDec.init(Cipher.DECRYPT_MODE, new SecretKeySpec(dataKey, "AES"));
                tweakEnc = Cipher.getInstance("AES/ECB/NoPadding");
                tweakEnc.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(tweakKey, "AES"));
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException(e);
            }
        }

        /** Decrypts buf[off .. off+count*SECTOR) in place; the first sector there is number firstSector. */
        void decryptSectors(byte[] buf, int off, int count, long firstSector) {
            int len = count * SECTOR;
            if (tweaks.length < len) {
                tweaks = new byte[len];
                tweakIn = new byte[count * 16];
            }
            if (tweakIn.length < count * 16) tweakIn = new byte[count * 16];
            for (int s = 0; s < count; s++) {
                long n = firstSector + s;
                int b = s * 16;
                for (int i = 0; i < 8; i++) tweakIn[b + i] = (byte) (n >>> (8 * i));
                for (int i = 8; i < 16; i++) tweakIn[b + i] = 0;
            }
            byte[] t0;
            try {
                t0 = tweakEnc.doFinal(tweakIn, 0, count * 16);
                for (int s = 0; s < count; s++) {
                    int base = s * SECTOR;
                    System.arraycopy(t0, s * 16, tweaks, base, 16);
                    for (int j = 16; j < SECTOR; j += 16) {
                        int prev = base + j - 16;
                        int cur = base + j;
                        int carry = 0;
                        for (int k = 0; k < 16; k++) {
                            int v = tweaks[prev + k] & 0xFF;
                            tweaks[cur + k] = (byte) ((v << 1) | carry);
                            carry = v >>> 7;
                        }
                        if (carry != 0) tweaks[cur] ^= (byte) 0x87;
                    }
                }
                for (int i = 0; i < len; i++) buf[off + i] ^= tweaks[i];
                dataDec.doFinal(buf, off, len, buf, off);
                for (int i = 0; i < len; i++) buf[off + i] ^= tweaks[i];
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
