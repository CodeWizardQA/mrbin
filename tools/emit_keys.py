import re, sys, subprocess
lines = subprocess.run(["/usr/bin/python3","-I",__import__("os").path.join(__import__("os").path.dirname(__import__("os").path.abspath(__file__)), "keys2java.py"),sys.argv[1]],capture_output=True,text=True).stdout.split("\n")
d = {}
for l in lines:
    if not l.strip(): continue
    cls, k, n, hexs = l.split()
    d.setdefault(cls, {})[k] = hexs
def chunk(h):
    parts = [h[i:i+64] for i in range(0, len(h), 64)]
    return "\n".join('            + "%s"' % p if i else '            "%s"' % p for i, p in enumerate(parts))
print("""// SPDX-License-Identifier: GPL-3.0-or-later
// Mr. Bin engine: Java translation of parts of shadPS4 (Copyright 2024 shadPS4 Emulator Project,
// GPL-2.0-or-later) and LibOrbisPkg (Maxton, LGPL-3.0). See the README, "License and credits".
package io.github.codewizardqa.mrbin.engine.orbis;

import java.math.BigInteger;

/**
 * Public fake-PKG RSA keysets, copied byte for byte from shadPS4 v0.7.0
 * src/core/crypto/keys.h (GPL-2.0-or-later); LibOrbisPkg Util/Keys.cs has the same values.
 * They only open fake PKGs (fPKG). Retail PKGs are keyed to the console and cannot be opened here.
 */
final class OrbisKeys {
    private OrbisKeys() {}

    static final class RsaKeyset {
        final BigInteger n, e, d, p, q, dp, dq, qinv;

        RsaKeyset(String n, String e, String d, String p, String q, String dp, String dq, String qinv) {
            this.n = new BigInteger(n, 16);
            this.e = new BigInteger(e, 16);
            this.d = new BigInteger(d, 16);
            this.p = new BigInteger(p, 16);
            this.q = new BigInteger(q, 16);
            this.dp = new BigInteger(dp, 16);
            this.dq = new BigInteger(dq, 16);
            this.qinv = new BigInteger(qinv, 16);
        }
    }
""")
for cls, java in (("FakeKeyset","FAKE"),("PkgDerivedKey3Keyset","PKG_DERIVED_KEY3")):
    f = d[cls]
    args = ",\n".join(chunk(f[k]) for k in ("Modulus","PublicExponent","PrivateExponent","Prime1","Prime2","Exponent1","Exponent2","Coefficient"))
    print("    /** %s */\n    static final RsaKeyset %s = new RsaKeyset(\n%s);\n" % (cls, java, args))
print("}")
