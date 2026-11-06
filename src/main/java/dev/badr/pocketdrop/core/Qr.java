package dev.badr.pocketdrop.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Small QR code generator (byte mode, versions 1-10, error correction L or M), written for PocketDrop so
 * the link QR is made on the device with no external service or library.
 */
public final class Qr {
    private static final int[] ECC_PER_BLOCK_L = {-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18};
    private static final int[] BLOCKS_L = {-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4};
    private static final int[] ECC_PER_BLOCK_M = {-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26};
    private static final int[] BLOCKS_M = {-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5};

    private final int version;
    private final int size;
    private final boolean medium;
    private final boolean[][] modules;
    private final boolean[][] isFunction;

    public static Qr encode(String text, boolean medium) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        int ver;
        for (ver = 1; ver <= 10; ver++) {
            int capacity = numDataCodewords(ver, medium) * 8;
            int countBits = ver <= 9 ? 8 : 16;
            if (4 + countBits + data.length * 8 <= capacity) break;
        }
        if (ver > 10) throw new IllegalArgumentException("Text is too long for this QR generator");

        int capacityBits = numDataCodewords(ver, medium) * 8;
        List<Boolean> bits = new ArrayList<>();
        append(bits, 0x4, 4);
        append(bits, data.length, ver <= 9 ? 8 : 16);
        for (byte b : data) append(bits, b & 0xFF, 8);
        append(bits, 0, Math.min(4, capacityBits - bits.size()));
        while (bits.size() % 8 != 0) bits.add(false);
        for (int pad = 0xEC; bits.size() < capacityBits; pad ^= 0xEC ^ 0x11) append(bits, pad, 8);

        byte[] codewords = new byte[bits.size() / 8];
        for (int i = 0; i < bits.size(); i++) {
            if (bits.get(i)) codewords[i >>> 3] |= 1 << (7 - (i & 7));
        }
        return new Qr(ver, medium, codewords);
    }

    public int size() { return size; }

    public boolean get(int x, int y) {
        return x >= 0 && x < size && y >= 0 && y < size && modules[y][x];
    }

    private static void append(List<Boolean> bits, int value, int count) {
        for (int i = count - 1; i >= 0; i--) bits.add(((value >>> i) & 1) != 0);
    }

    private Qr(int version, boolean medium, byte[] dataCodewords) {
        this.version = version;
        this.medium = medium;
        this.size = version * 4 + 17;
        this.modules = new boolean[size][size];
        this.isFunction = new boolean[size][size];

        drawFunctionPatterns();
        byte[] all = addEccAndInterleave(dataCodewords);
        drawCodewords(all);

        int best = 0;
        int minPenalty = Integer.MAX_VALUE;
        for (int m = 0; m < 8; m++) {
            applyMask(m);
            drawFormatBits(m);
            int p = penalty();
            if (p < minPenalty) { minPenalty = p; best = m; }
            applyMask(m); // undo
        }
        applyMask(best);
        drawFormatBits(best);
    }

    // ---------------------------------------------------------------- function patterns

    private void drawFunctionPatterns() {
        for (int i = 0; i < size; i++) {
            setFunction(6, i, i % 2 == 0);
            setFunction(i, 6, i % 2 == 0);
        }
        drawFinder(3, 3);
        drawFinder(size - 4, 3);
        drawFinder(3, size - 4);

        int[] align = alignmentPositions();
        int n = align.length;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if ((i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0)) continue;
                drawAlignment(align[i], align[j]);
            }
        }
        drawFormatBits(0);
        drawVersion();
    }

    private void drawFinder(int cx, int cy) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int dist = Math.max(Math.abs(dx), Math.abs(dy));
                int x = cx + dx, y = cy + dy;
                if (x >= 0 && x < size && y >= 0 && y < size) setFunction(x, y, dist != 2 && dist != 4);
            }
        }
    }

    private void drawAlignment(int cx, int cy) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) setFunction(cx + dx, cy + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
        }
    }

    private void drawFormatBits(int mask) {
        int data = ((medium ? 0 : 1) << 3) | mask; // L = 01, M = 00
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        int bits = ((data << 10) | rem) ^ 0x5412;

        for (int i = 0; i <= 5; i++) setFunction(8, i, bit(bits, i));
        setFunction(8, 7, bit(bits, 6));
        setFunction(8, 8, bit(bits, 7));
        setFunction(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) setFunction(14 - i, 8, bit(bits, i));

        for (int i = 0; i < 8; i++) setFunction(size - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) setFunction(8, size - 15 + i, bit(bits, i));
        setFunction(8, size - 8, true);
    }

    private void drawVersion() {
        if (version < 7) return;
        int rem = version;
        for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
        int bits = (version << 12) | rem;
        for (int i = 0; i < 18; i++) {
            boolean b = bit(bits, i);
            int a = size - 11 + i % 3;
            int c = i / 3;
            setFunction(a, c, b);
            setFunction(c, a, b);
        }
    }

    private void setFunction(int x, int y, boolean dark) {
        modules[y][x] = dark;
        isFunction[y][x] = true;
    }

    private int[] alignmentPositions() {
        if (version == 1) return new int[0];
        int n = version / 7 + 2;
        int step = (version * 4 + n * 2 + 1) / (n * 2 - 2) * 2;
        int[] r = new int[n];
        r[0] = 6;
        for (int i = r.length - 1, pos = size - 7; i >= 1; i--, pos -= step) r[i] = pos;
        return r;
    }

    // ---------------------------------------------------------------- data

    private static int numRawModules(int ver) {
        int size = ver * 4 + 17;
        int result = size * size;
        result -= 8 * 8 * 3;
        result -= 15 * 2 + 1;
        result -= (size - 16) * 2;
        if (ver >= 2) {
            int n = ver / 7 + 2;
            result -= (25 * n - 10) * n - 55; // alignment patterns, minus the part shared with the timing lines
            if (ver >= 7) result -= 36;       // version information blocks
        }
        return result;
    }

    private static int numDataCodewords(int ver, boolean medium) {
        int ecc = medium ? ECC_PER_BLOCK_M[ver] : ECC_PER_BLOCK_L[ver];
        int blocks = medium ? BLOCKS_M[ver] : BLOCKS_L[ver];
        return numRawModules(ver) / 8 - ecc * blocks;
    }

    private byte[] addEccAndInterleave(byte[] data) {
        int blocks = medium ? BLOCKS_M[version] : BLOCKS_L[version];
        int eccLen = medium ? ECC_PER_BLOCK_M[version] : ECC_PER_BLOCK_L[version];
        int raw = numRawModules(version) / 8;
        int shortBlocks = blocks - raw % blocks;
        int shortLen = raw / blocks;

        byte[][] dataBlocks = new byte[blocks][];
        byte[][] eccBlocks = new byte[blocks][];
        byte[] divisor = rsDivisor(eccLen);
        for (int i = 0, k = 0; i < blocks; i++) {
            int dl = shortLen - eccLen + (i < shortBlocks ? 0 : 1);
            byte[] d = new byte[dl];
            System.arraycopy(data, k, d, 0, dl);
            k += dl;
            dataBlocks[i] = d;
            eccBlocks[i] = rsRemainder(d, divisor);
        }
        byte[] out = new byte[raw];
        int idx = 0;
        for (int i = 0; i < shortLen - eccLen + 1; i++) {
            for (int j = 0; j < blocks; j++) {
                if (i < dataBlocks[j].length) out[idx++] = dataBlocks[j][i];
            }
        }
        for (int i = 0; i < eccLen; i++) {
            for (int j = 0; j < blocks; j++) out[idx++] = eccBlocks[j][i];
        }
        return out;
    }

    private void drawCodewords(byte[] data) {
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (!isFunction[y][x] && i < data.length * 8) {
                        modules[y][x] = bit(data[i >>> 3], 7 - (i & 7));
                        i++;
                    }
                }
            }
        }
    }

    private void applyMask(int mask) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean invert;
                switch (mask) {
                    case 0 -> invert = (x + y) % 2 == 0;
                    case 1 -> invert = y % 2 == 0;
                    case 2 -> invert = x % 3 == 0;
                    case 3 -> invert = (x + y) % 3 == 0;
                    case 4 -> invert = (x / 3 + y / 2) % 2 == 0;
                    case 5 -> invert = x * y % 2 + x * y % 3 == 0;
                    case 6 -> invert = (x * y % 2 + x * y % 3) % 2 == 0;
                    default -> invert = ((x + y) % 2 + x * y % 3) % 2 == 0;
                }
                if (invert && !isFunction[y][x]) modules[y][x] = !modules[y][x];
            }
        }
    }

    private int penalty() {
        int result = 0;
        // runs of the same colour, rows then columns
        for (int pass = 0; pass < 2; pass++) {
            for (int a = 0; a < size; a++) {
                int run = 1;
                for (int b = 1; b < size; b++) {
                    boolean cur = pass == 0 ? modules[a][b] : modules[b][a];
                    boolean prev = pass == 0 ? modules[a][b - 1] : modules[b - 1][a];
                    if (cur == prev) {
                        run++;
                        if (run == 5) result += 3; else if (run > 5) result++;
                    } else {
                        run = 1;
                    }
                }
            }
        }
        // 2x2 blocks
        for (int y = 0; y < size - 1; y++) {
            for (int x = 0; x < size - 1; x++) {
                boolean c = modules[y][x];
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) result += 3;
            }
        }
        // finder-like patterns
        int[] p1 = {1, 0, 1, 1, 1, 0, 1, 0, 0, 0, 0};
        int[] p2 = {0, 0, 0, 0, 1, 0, 1, 1, 1, 0, 1};
        for (int pass = 0; pass < 2; pass++) {
            for (int a = 0; a < size; a++) {
                for (int b = 0; b + 11 <= size; b++) {
                    boolean m1 = true, m2 = true;
                    for (int k = 0; k < 11 && (m1 || m2); k++) {
                        int v = (pass == 0 ? modules[a][b + k] : modules[b + k][a]) ? 1 : 0;
                        if (v != p1[k]) m1 = false;
                        if (v != p2[k]) m2 = false;
                    }
                    if (m1) result += 40;
                    if (m2) result += 40;
                }
            }
        }
        // dark / light balance
        int dark = 0;
        for (boolean[] row : modules) for (boolean m : row) if (m) dark++;
        int total = size * size;
        int k = (Math.abs(dark * 20 - total * 10) + total - 1) / total - 1;
        result += Math.max(0, k) * 10;
        return result;
    }

    // ---------------------------------------------------------------- Reed-Solomon over GF(256)

    private static byte[] rsDivisor(int degree) {
        byte[] result = new byte[degree];
        result[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < degree; j++) {
                result[j] = (byte) gfMul(result[j] & 0xFF, root);
                if (j + 1 < degree) result[j] ^= result[j + 1];
            }
            root = gfMul(root, 0x02);
        }
        return result;
    }

    private static byte[] rsRemainder(byte[] data, byte[] divisor) {
        byte[] result = new byte[divisor.length];
        for (byte b : data) {
            int factor = (b ^ result[0]) & 0xFF;
            System.arraycopy(result, 1, result, 0, result.length - 1);
            result[result.length - 1] = 0;
            for (int i = 0; i < result.length; i++) result[i] ^= (byte) gfMul(divisor[i] & 0xFF, factor);
        }
        return result;
    }

    private static int gfMul(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z & 0xFF;
    }

    private static boolean bit(int value, int i) { return ((value >>> i) & 1) != 0; }
}
