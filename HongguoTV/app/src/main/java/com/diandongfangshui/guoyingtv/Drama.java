package com.diandongfangshui.guoyingtv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Two explicit input formats:
 * v1: standalone authorized media catalog with episode.url.
 * v2: metadata adapter for fields found in the uploaded Hongguo APK.
 * No method derives playable URLs from video_id or video_model.
 */
public final class Drama {
    public final String id;
    public final String title;
    public final String genre;
    public final String description;
    public final String poster;
    public final List<Episode> episodes;
    public final boolean metadataAdapter;
    private final JSONObject json;

    private Drama(JSONObject json, String id, String title, String genre,
                  String description, String poster, List<Episode> episodes,
                  boolean metadataAdapter) {
        this.json = json;
        this.id = id;
        this.title = title;
        this.genre = genre;
        this.description = description;
        this.poster = poster;
        this.episodes = episodes;
        this.metadataAdapter = metadataAdapter;
    }

    public JSONObject toJson() { return json; }

    public boolean hasPlayableEpisodes() {
        for (Episode episode : episodes) if (episode.isPlayable()) return true;
        return false;
    }

    public int firstPlayableEpisode(int preferred) {
        if (preferred >= 0 && preferred < episodes.size() && episodes.get(preferred).isPlayable())
            return preferred;
        for (int i = 0; i < episodes.size(); i++)
            if (episodes.get(i).isPlayable()) return i;
        return -1;
    }

    /** Infers only the explicitly tagged v2 metadata object when played from details. */
    public static Drama fromJson(JSONObject obj) throws JSONException {
        return fromJson(obj, obj.has("series_id") ? 2 : 1);
    }

    public static Drama fromJson(JSONObject obj, int version) throws JSONException {
        if (version != 1 && version != 2) throw new JSONException("Unsupported catalog version");
        final boolean v2 = version == 2;
        String id = obj.optString(v2 ? "series_id" : "id", "").trim();
        String title = obj.optString(v2 ? "series_title" : "title", "").trim();
        if (id.isEmpty() || title.isEmpty() || id.length() > 100 || title.length() > 200) {
            throw new JSONException("Invalid drama id/title");
        }
        String genre = obj.optString("category", "精选");
        String description = obj.optString("description", "");
        String poster = obj.optString(v2 ? "series_cover" : "poster", "").trim();
        if (!poster.isEmpty() && !UrlPolicy.isHttpsUrl(poster)) poster = "";
        JSONArray array = obj.getJSONArray("episodes");
        if (array.length() == 0 || array.length() > 2000) {
            throw new JSONException("Episode count invalid: " + id);
        }
        List<Episode> episodes = new ArrayList<>();
        Set<Integer> indices = new HashSet<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject ep = array.getJSONObject(i);
            int episodeIndex = v2 ? ep.optInt("episode_index", -1) : i + 1;
            if (episodeIndex < 1 || !indices.add(episodeIndex)) {
                throw new JSONException("Missing/duplicate episode_index in " + id);
            }
            String videoId = v2 ? ep.optString("video_id", "").trim() : "";
            if (v2 && videoId.isEmpty()) {
                throw new JSONException("Missing video_id for episode " + episodeIndex);
            }
            String url = ep.optString(v2 ? "playback_url" : "url", "").trim();
            if ((!v2 && url.isEmpty()) || (!url.isEmpty() && !UrlPolicy.isHttpsUrl(url))) {
                throw new JSONException("Only explicitly authorized HTTPS media URLs are supported");
            }
            String name = ep.optString("title", "第 " + episodeIndex + " 集");
            episodes.add(new Episode(name, url, videoId, episodeIndex));
        }
        return new Drama(obj, id, title, genre, description, poster, episodes, v2);
    }

    public static final class Episode {
        public final String title;
        public final String url;
        public final String videoId;
        public final int episodeIndex;

        public Episode(String title, String url, String videoId, int episodeIndex) {
            this.title = title;
            this.url = url;
            this.videoId = videoId;
            this.episodeIndex = episodeIndex;
        }

        public boolean isPlayable() { return UrlPolicy.isHttpsUrl(url); }
    }
}
