package com.diandongfangshui.guoyingtv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class Drama {
    public final String id;
    public final String title;
    public final String genre;
    public final String description;
    public final String poster;
    public final List<Episode> episodes;
    private final JSONObject json;

    private Drama(JSONObject json, String id, String title, String genre,
                  String description, String poster, List<Episode> episodes) {
        this.json = json;
        this.id = id;
        this.title = title;
        this.genre = genre;
        this.description = description;
        this.poster = poster;
        this.episodes = episodes;
    }

    public JSONObject toJson() { return json; }

    public static Drama fromJson(JSONObject obj) throws JSONException {
        String id = obj.getString("id").trim();
        String title = obj.getString("title").trim();
        if (id.isEmpty() || title.isEmpty() || id.length() > 100) {
            throw new JSONException("Invalid drama id/title");
        }
        String genre = obj.optString("category", "精选");
        String description = obj.optString("description", "");
        String poster = obj.optString("poster", "");
        if (!poster.isEmpty() && !CatalogStore.isHttpsUrl(poster)) poster = "";
        JSONArray array = obj.getJSONArray("episodes");
        if (array.length() == 0 || array.length() > 300) {
            throw new JSONException("Episode count invalid: " + id);
        }
        List<Episode> episodes = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject e = array.getJSONObject(i);
            String url = e.getString("url").trim();
            if (!CatalogStore.isHttpsUrl(url)) {
                throw new JSONException("Only HTTPS media URLs are supported");
            }
            episodes.add(new Episode(e.optString("title", "第 " + (i + 1) + " 集"), url));
        }
        return new Drama(obj, id, title, genre, description, poster, episodes);
    }

    public static final class Episode {
        public final String title;
        public final String url;
        public Episode(String title, String url) {
            this.title = title;
            this.url = url;
        }
    }
}
