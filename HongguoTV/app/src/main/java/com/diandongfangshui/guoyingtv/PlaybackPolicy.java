package com.diandongfangshui.guoyingtv;

/** Bounded recovery and persisted-position validation, independent of the player UI. */
public final class PlaybackPolicy {
    private PlaybackPolicy() {}
    public static boolean retry(int attempts, int httpStatus) {
        return attempts < 2 && httpStatus != 401 && httpStatus != 403 && httpStatus != 404;
    }
    public static long resume(long position, long duration) {
        if (position < 0 || (duration > 0 && position >= duration - 1000)) return 0;
        return position;
    }
}
