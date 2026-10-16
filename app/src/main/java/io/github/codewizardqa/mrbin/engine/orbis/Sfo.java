// SPDX-License-Identifier: GPL-3.0-or-later
// Mr. Bin engine: Java translation of parts of shadPS4 (Copyright 2024 shadPS4 Emulator Project,
// GPL-2.0-or-later) and LibOrbisPkg (Maxton, LGPL-3.0). See the README, "License and credits".
package io.github.codewizardqa.mrbin.engine.orbis;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal param.sfo reader (little-endian "\0PSF" table), enough to show TITLE, TITLE_ID, CATEGORY,
 * APP_VER and VERSION. Layout as in LibOrbisPkg SFO/ParamSfo.cs and shadPS4 psf.cpp.
 */
public final class Sfo {
    private Sfo() {}

    /** Returns key → value (strings and integers as text); empty map when the data is not a PSF table. */
    public static Map<String, String> parse(byte[] b) {
        Map<String, String> out = new LinkedHashMap<>();
        if (b == null || b.length < 0x14) return out;
        if (b[0] != 0 || b[1] != 'P' || b[2] != 'S' || b[3] != 'F') return out;
        long keyTable = Le.u32(b, 0x08);
        long dataTable = Le.u32(b, 0x0C);
        long count = Le.u32(b, 0x10);
        if (count > 1024 || 0x14 + count * 16 > b.length) return out;
        for (int i = 0; i < count; i++) {
            int o = 0x14 + i * 16;
            int keyOff = Le.u16(b, o);
            int fmt = Le.u16(b, o + 2);
            long len = Le.u32(b, o + 4);
            long dataOff = Le.u32(b, o + 12);
            long k = keyTable + keyOff;
            long d = dataTable + dataOff;
            if (k >= b.length || d > b.length || d + len > b.length) continue;
            int ke = (int) k;
            while (ke < b.length && b[ke] != 0) ke++;
            String key = new String(b, (int) k, ke - (int) k, StandardCharsets.US_ASCII);
            String value;
            if (fmt == 0x0404 && len >= 4) {
                value = Long.toString(Le.u32(b, (int) d));
            } else {
                int n = (int) len;
                while (n > 0 && b[(int) d + n - 1] == 0) n--;
                value = new String(b, (int) d, n, StandardCharsets.UTF_8);
            }
            out.put(key, value);
        }
        return out;
    }
}
