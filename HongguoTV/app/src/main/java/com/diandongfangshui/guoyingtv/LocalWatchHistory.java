package com.diandongfangshui.guoyingtv;

import android.content.SharedPreferences;

public final class LocalWatchHistory implements ContentServices.WatchHistoryRepository {
    private final SharedPreferences preferences;
    public LocalWatchHistory(SharedPreferences preferences) { this.preferences = preferences; }
    public long position(String seriesId, int episode) {
        return Math.max(0, preferences.getLong("pos_" + seriesId + "_" + episode, 0));
    }
    public void save(String seriesId, int episode, long positionMs) {
        preferences.edit().putLong("pos_" + seriesId + "_" + episode, Math.max(0, positionMs))
                .putInt("last_" + seriesId, episode).apply();
    }
}
