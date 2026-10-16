package io.github.codewizardqa.mrbin.engine.orbis;

import java.io.File;
import java.math.BigInteger;

/**
 * Mac-JVM driver for the engine, run by scripts/test-engine.sh:
 *   keys                     – checks the RSA keysets are internally consistent
 *   inspect <pkg>            – prints what the app would show
 *   extract <pkg> <destRoot> – extracts like the app does
 */
public final class OrbisCli {
    public static void main(String[] a) throws Exception {
        switch (a[0]) {
            case "keys":
                checkKeyset("FAKE", OrbisKeys.FAKE);
                checkKeyset("PKG_DERIVED_KEY3", OrbisKeys.PKG_DERIVED_KEY3);
                System.out.println("keys OK");
                break;
            case "inspect": {
                OrbisPkg.Info i = OrbisPkg.inspect(new File(a[1]));
                print(i);
                break;
            }
            case "extract": {
                OrbisPkg.Info i = OrbisPkg.inspect(new File(a[1]));
                print(i);
                try {
                    OrbisPkg.Result r = OrbisPkg.extract(new File(a[1]), new File(a[2]), new OrbisPkg.Listener() {
                        @Override
                        public void onProgress(String p, long d, long t, int fd, int ft) {
                        }

                        @Override
                        public boolean isCancelled() {
                            return false;
                        }

                        @Override
                        public void waitWhilePaused() {
                        }
                    });
                    System.out.println("RESULT dir=" + r.gameDir + " files=" + r.files + " dirs=" + r.dirs + " bytes=" + r.bytes
                            + " eboot=" + r.ebootSize + " head=" + r.ebootHead + " skipped=" + r.skippedEntries);
                } catch (OrbisPkg.NotExtractableException e) {
                    System.out.println("NOT_EXTRACTABLE " + e.info.kind + ": " + e.getMessage());
                    System.exit(3);
                }
                break;
            }
            default:
                throw new IllegalArgumentException(a[0]);
        }
    }

    private static void print(OrbisPkg.Info i) {
        System.out.println("kind=" + i.kind + " contentId=" + i.contentId + " titleId=" + i.titleId + " title=" + i.title
                + " category=" + i.category + " appVer=" + i.appVersion + " files=" + i.fileCount + " dirs=" + i.dirCount
                + " bytes=" + i.contentBytes + "\nreason=" + i.reasonCode + ": " + i.reason + "\ndetail=" + i.detail);
    }

    private static void checkKeyset(String name, OrbisKeys.RsaKeyset k) {
        BigInteger one = BigInteger.ONE;
        boolean ok = k.p.multiply(k.q).equals(k.n)
                && k.d.mod(k.p.subtract(one)).equals(k.dp)
                && k.d.mod(k.q.subtract(one)).equals(k.dq)
                && k.q.multiply(k.qinv).mod(k.p).equals(one)
                && k.n.bitLength() == 2048;
        BigInteger m = new BigInteger("123456789abcdef", 16);
        ok &= m.modPow(k.e, k.n).modPow(k.d, k.n).equals(m);
        if (!ok) throw new AssertionError(name + " keyset is inconsistent");
    }
}
