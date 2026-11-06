package dev.badr.pocketdrop.core;

/**
 * Small self-test for Windows secure storage. Run it once on the Windows computer:
 * java -cp target/classes dev.badr.pocketdrop.core.DpapiCheck
 */
public final class DpapiCheck {
    private DpapiCheck() {}

    public static void main(String[] args) {
        try {
            SecretProtector p = SecretProtector.systemDefault();
            String secret = "123456789:TEST-token-for-the-check-0123456789";
            String enc = p.protect(secret);
            String back = p.unprotect(enc);
            boolean ok = secret.equals(back) && !enc.contains("TEST");
            System.out.println(ok ? "OK: Windows secure storage works." : "FAILED: the value did not round-trip.");
            System.exit(ok ? 0 : 1);
        } catch (Exception e) {
            System.out.println("FAILED: " + e.getMessage());
            System.exit(1);
        }
    }
}
