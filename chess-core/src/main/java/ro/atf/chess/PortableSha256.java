package ro.atf.chess;

import java.util.Arrays;

/** Small dependency-free SHA-256 implementation shared by JVM and TeaVM. */
final class PortableSha256 {
    private static final int[] ROUND = {
            0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
            0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
            0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
            0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
    };
    private static final int[] INITIAL = {
            0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
            0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19
    };

    private PortableSha256() { }

    static byte[] digest(byte[] input) {
        long paddedLength = ((input.length + 9L + 63L) / 64L) * 64L;
        if (paddedLength > Integer.MAX_VALUE) throw new IllegalArgumentException("SHA-256 input is too large");
        byte[] padded = Arrays.copyOf(input, (int) paddedLength);
        padded[input.length] = (byte) 0x80;
        long bitLength = (long) input.length << 3;
        for (int i = 0; i < Long.BYTES; i++) padded[padded.length - 1 - i] = (byte) (bitLength >>> (i * 8));

        int[] state = INITIAL.clone();
        int[] words = new int[64];
        for (int offset = 0; offset < padded.length; offset += 64) {
            for (int i = 0; i < 16; i++) {
                int start = offset + i * 4;
                words[i] = (padded[start] & 0xff) << 24 | (padded[start + 1] & 0xff) << 16
                        | (padded[start + 2] & 0xff) << 8 | padded[start + 3] & 0xff;
            }
            for (int i = 16; i < words.length; i++) {
                int left = words[i - 15];
                int right = words[i - 2];
                int sigma0 = Integer.rotateRight(left, 7) ^ Integer.rotateRight(left, 18) ^ (left >>> 3);
                int sigma1 = Integer.rotateRight(right, 17) ^ Integer.rotateRight(right, 19) ^ (right >>> 10);
                words[i] = words[i - 16] + sigma0 + words[i - 7] + sigma1;
            }

            int a = state[0], b = state[1], c = state[2], d = state[3];
            int e = state[4], f = state[5], g = state[6], h = state[7];
            for (int i = 0; i < words.length; i++) {
                int sum1 = Integer.rotateRight(e, 6) ^ Integer.rotateRight(e, 11) ^ Integer.rotateRight(e, 25);
                int choice = (e & f) ^ (~e & g);
                int first = h + sum1 + choice + ROUND[i] + words[i];
                int sum0 = Integer.rotateRight(a, 2) ^ Integer.rotateRight(a, 13) ^ Integer.rotateRight(a, 22);
                int majority = (a & b) ^ (a & c) ^ (b & c);
                int second = sum0 + majority;
                h = g; g = f; f = e; e = d + first;
                d = c; c = b; b = a; a = first + second;
            }
            state[0] += a; state[1] += b; state[2] += c; state[3] += d;
            state[4] += e; state[5] += f; state[6] += g; state[7] += h;
        }

        byte[] output = new byte[32];
        for (int i = 0; i < state.length; i++) {
            for (int j = 0; j < 4; j++) output[i * 4 + j] = (byte) (state[i] >>> (24 - j * 8));
        }
        return output;
    }

    static String hex(byte[] input) {
        byte[] digest = digest(input);
        var hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hex.append(Character.forDigit((value & 0xff) >>> 4, 16));
            hex.append(Character.forDigit(value & 0x0f, 16));
        }
        return hex.toString();
    }
}
