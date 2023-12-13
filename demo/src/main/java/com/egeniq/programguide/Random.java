package com.egeniq.programguide;

import java.util.List;

public class Random {
    private static final java.util.Random rand = new java.util.Random();

    public static long nextLong(long max) {
        return nextLong(0, max);
    }

    public static <T> T nextInList(List<T> list) {
        int index = rand.nextInt(list.size());
        return list.get(index);
    }

    public static long nextLong(long min, long max) {
        long origin = Math.min(min, max);
        long bound = Math.max(min, max);
        long r = rand.nextLong();
        if (origin < bound) {
            long n = bound - origin, m = n - 1;
            if ((n & m) == 0L)  // power of two
                r = (r & m) + origin;
            else if (n > 0L) {  // reject over-represented candidates
                for (long u = r >>> 1;            // ensure nonnegative
                     u + m - (r = u % n) < 0L;    // rejection check
                     u = rand.nextLong() >>> 1) // retry
                    ;
                r += origin;
            }
            else {              // range not representable as long
                while (r < origin || r >= bound)
                    r = rand.nextLong();
            }
        }
        return r;
    }
}
