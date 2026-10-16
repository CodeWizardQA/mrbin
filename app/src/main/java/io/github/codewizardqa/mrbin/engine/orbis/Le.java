// SPDX-License-Identifier: GPL-3.0-or-later
// Mr. Bin engine: Java translation of parts of shadPS4 (Copyright 2024 shadPS4 Emulator Project,
// GPL-2.0-or-later) and LibOrbisPkg (Maxton, LGPL-3.0). See the README, "License and credits".
package io.github.codewizardqa.mrbin.engine.orbis;

/** Little/big-endian field readers. */
final class Le {
    private Le() {}

    static int u16(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8);
    }

    static int i32(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8) | ((b[o + 2] & 0xFF) << 16) | ((b[o + 3] & 0xFF) << 24);
    }

    static long u32(byte[] b, int o) {
        return i32(b, o) & 0xFFFFFFFFL;
    }

    static long i64(byte[] b, int o) {
        return u32(b, o) | (u32(b, o + 4) << 32);
    }

    static int be16(byte[] b, int o) {
        return ((b[o] & 0xFF) << 8) | (b[o + 1] & 0xFF);
    }

    static long be32(byte[] b, int o) {
        return (((b[o] & 0xFFL) << 24) | ((b[o + 1] & 0xFF) << 16) | ((b[o + 2] & 0xFF) << 8) | (b[o + 3] & 0xFF));
    }

    static long be64(byte[] b, int o) {
        return (be32(b, o) << 32) | be32(b, o + 4);
    }

    static String hex(byte[] b, int off, int len) {
        StringBuilder sb = new StringBuilder(len * 3);
        for (int i = 0; i < len; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format("%02X", b[off + i] & 0xFF));
        }
        return sb.toString();
    }
}
