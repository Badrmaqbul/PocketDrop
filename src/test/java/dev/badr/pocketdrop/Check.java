package dev.badr.pocketdrop;

import java.util.function.BooleanSupplier;

/** Tiny test helper (no JUnit needed). */
final class Check {
    static int passed, failed;

    static void ok(String name, boolean cond) {
        if (cond) {
            passed++;
            System.out.println("  PASS  " + name);
        } else {
            failed++;
            System.out.println("  FAIL  " + name);
        }
    }

    static void eq(String name, Object expected, Object actual) {
        boolean same = expected == null ? actual == null : expected.equals(actual);
        if (!same) System.out.println("        expected: " + expected + "\n        actual:   " + actual);
        ok(name, same);
    }

    static boolean waitUntil(BooleanSupplier c, long ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (c.getAsBoolean()) return true;
            try { Thread.sleep(25); } catch (InterruptedException e) { return false; }
        }
        return c.getAsBoolean();
    }

    static void section(String s) { System.out.println("\n== " + s); }
}
