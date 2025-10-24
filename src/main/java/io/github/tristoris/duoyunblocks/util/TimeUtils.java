package io.github.tristoris.duoyunblocks.util;

public class TimeUtils {
    private static final int TICKS_PER_SECOND = 20;
    private static final int TICKS_PER_MINUTE = 20 * 60;

    public static int secondsToTicks(double seconds) {
        return (int) Math.round(seconds * TICKS_PER_SECOND);
    }

    public static int minutesToTicks(double minutes) {
        return (int) Math.round(minutes * TICKS_PER_MINUTE);
    }

    public static double ticksToSeconds(int ticks) {
        return ticks / (double) TICKS_PER_SECOND;
    }

    public static double ticksToMinutes(int ticks) {
        return ticks / (double) TICKS_PER_MINUTE;
    }

    private TimeUtils() {}
}
