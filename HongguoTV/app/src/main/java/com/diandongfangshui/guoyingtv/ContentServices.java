package com.diandongfangshui.guoyingtv;

import java.util.List;

/** Service boundaries. Implementations must report unavailable capabilities explicitly. */
public final class ContentServices {
    private ContentServices() {}
    public interface CatalogRepository {
        List<Drama> home(int page) throws Exception;
        List<String> categories() throws Exception;
        List<Drama> ranking(int page) throws Exception;
    }
    public interface SearchRepository {
        List<Drama> search(String keyword, int page) throws Exception;
    }
    public interface SeriesRepository {
        Drama detail(String seriesId) throws Exception;
    }
    public interface PlaybackResolver {
        Playback resolve(String seriesId, int episode, boolean refresh) throws Exception;
    }
    public interface AuthenticationProvider {
        String status();
        boolean canSynchronize();
    }
    public interface WatchHistoryRepository {
        long position(String seriesId, int episode);
        void save(String seriesId, int episode, long positionMs);
    }
    public static final class Playback {
        public final String url;
        public final long expiresAtMs;
        public final String audioUrl, backupUrl, format, definition;
        // Transient authorization supplied by the content service. Never log or persist this value.
        final String playAuth;
        org.json.JSONObject rawPlayInfo;
        public Playback(String url, long expiresAtMs) {
            this(url, expiresAtMs, "", "", "", "", "");
        }
        public Playback(String url, long expiresAtMs, String audioUrl, String backupUrl,
                        String format, String definition, String playAuth) {
            if (!UrlPolicy.isHttpsUrl(url)) throw new IllegalArgumentException("媒体地址必须为 HTTPS");
            this.url = url;
            this.expiresAtMs = expiresAtMs;
            this.audioUrl = audioUrl;
            this.backupUrl = backupUrl;
            this.format = format;
            this.definition = definition;
            this.playAuth = playAuth;
        }
        public boolean requiresAuthorizedDecoder() { return !playAuth.isEmpty(); }
        public boolean usable(long nowMs) { return expiresAtMs == 0 || expiresAtMs > nowMs + 30000; }
    }
}
