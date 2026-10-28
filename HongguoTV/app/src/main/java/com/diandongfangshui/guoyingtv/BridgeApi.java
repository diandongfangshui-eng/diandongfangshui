package com.diandongfangshui.guoyingtv;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Standalone consumer of the documented hongguotv home-bridge HTTP protocol v1.
 * The bridge is a separate third-party program, not bundled or copied here.
 * Do not put video service tokens or platform credentials into GitHub.
 */
public final class BridgeApi {
    public static final String PREF_BASE = "bridge_base";
    public static final String PREF_TOKEN = "bridge_token";
    private static final int LIMIT_BYTES = 2 * 1024 * 1024;
    private final String origin;
    private final String token;

    public BridgeApi(String base, String token) {
        this.origin = normalizeBase(base);
        this.token = token == null ? "" : token.trim();
        if (this.token.indexOf('\r') >= 0 || this.token.indexOf('\n') >= 0)
            throw new IllegalArgumentException("访问口令含非法换行");
    }

    public static BridgeApi fromSaved(Context app) {
        SharedPreferences prefs = CatalogStore.prefs(app);
        return new BridgeApi(prefs.getString(PREF_BASE, ""), prefs.getString(PREF_TOKEN, ""));
    }

    public String base() { return origin; }
    public String token() { return token; }

    public static String normalizeBase(String input) {
        try {
            String trimmed = input == null ? "" : input.trim();
            URI uri = new URI(trimmed);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))
                throw new IllegalArgumentException("只允许 HTTP 或 HTTPS");
            if (uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty()
                        && !"/".equals(uri.getRawPath())))
                throw new IllegalArgumentException("请输入基础地址，不要添加路径、密码或查询参数");
            String host = uri.getHost();
            if (host == null || !isPrivateIPv4(host)) {
                throw new IllegalArgumentException("仅接受局域网 IPv4 地址：192.168.x.x、10.x.x.x、172.16–31.x.x");
            }
            int port = uri.getPort();
            if (port != -1 && (port <= 0 || port > 65535))
                throw new IllegalArgumentException("端口超出范围");
            String scheme = uri.getScheme().toLowerCase(java.util.Locale.ROOT);
            return scheme + "://" + host + (port < 0 ? "" : ":" + port);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("服务地址格式错误");
        }
    }

    private static boolean isPrivateIPv4(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return false;
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            if (parts[i].isEmpty() || parts[i].length() > 3) return false;
            for (int j = 0; j < parts[i].length(); j++)
                if (parts[i].charAt(j) < '0' || parts[i].charAt(j) > '9') return false;
            try {
                octets[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) { return false; }
            if (octets[i] > 255) return false;
        }
        return octets[0] == 10 || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
                || (octets[0] == 192 && octets[1] == 168);
    }

    private String segment(String id) {
        if (id == null || id.isEmpty() || id.length() > 256)
            throw new IllegalArgumentException("无效的剧集 ID");
        // URI path segment encoding; preserves strings rather than coercing numeric IDs.
        try {
            return new URI(null, null, "/" + id, null).getRawPath().substring(1);
        } catch (Exception e) {
            throw new IllegalArgumentException("无效的 ID 字符");
        }
    }

    public JSONObject health() throws Exception {
        JSONObject json = fetchJson("/health");
        if (!"hongguotv".equals(json.optString("service")) || json.optInt("apiVersion") != 1) {
            throw new IllegalStateException("服务协议不匹配：需要 hongguotv API v1");
        }
        return json;
    }

    public List<Series> home(int page) throws Exception {
        return list(fetchJson("/api/home?page=" + safePage(page)));
    }

    public List<Series> search(String keyword, int page) throws Exception {
        String encoded = java.net.URLEncoder.encode(keyword, "UTF-8");
        return list(fetchJson("/api/search?q=" + encoded + "&page=" + safePage(page)));
    }

    private static int safePage(int page) {
        if (page < 1 || page > 200) throw new IllegalArgumentException("页数超出范围");
        return page;
    }

    private static List<Series> list(JSONObject json) throws JSONException {
        JSONArray items = json.getJSONArray("items");
        if (items.length() > 100) throw new JSONException("服务返回了过多条目");
        List<Series> result = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.getJSONObject(i);
            result.add(Series.fromJson(o, false));
        }
        return result;
    }

    public Series series(String id) throws Exception {
        return Series.fromJson(fetchJson("/api/series/" + segment(id)), true);
    }

    public String resolve(String episodeId, boolean fresh) throws Exception {
        JSONObject o = fetchJson("/api/episodes/" + segment(episodeId) + "/play"
                + (fresh ? "?fresh=1" : ""));
        String path = o.optString("path", "");
        String expected = "/api/episodes/" + segment(episodeId) + "/stream";
        // Media proxy must remain on the explicitly configured local bridge.
        if (!path.equals(expected) && !path.startsWith(expected + "?"))
            throw new SecurityException("服务返回了非预期的媒体路径");
        String contentType = o.optString("contentType", "video/mp4");
        if (!contentType.toLowerCase(java.util.Locale.ROOT).startsWith("video/"))
            throw new SecurityException("服务返回了非视频 MIME 类型");
        return origin + path;
    }

    public String displayError(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private JSONObject fetchJson(String relative) throws Exception {
        URL url = new URL(origin + relative);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(12000);
        c.setInstanceFollowRedirects(false);
        c.setRequestMethod("GET");
        c.setRequestProperty("Accept", "application/json");
        if (!token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token);
        try {
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) {
                String message = code == 401 ? "口令无效或未填写" :
                        code == 502 ? "内容服务无法访问上游" : "HTTP " + code;
                throw new IllegalStateException(message);
            }
            String type = c.getContentType();
            if (type != null && !type.toLowerCase(java.util.Locale.ROOT).contains("json"))
                throw new IllegalStateException("服务没有返回 JSON");
            try (InputStream input = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (out.size() + count > LIMIT_BYTES)
                        throw new IllegalStateException("服务响应过大");
                    out.write(buffer, 0, count);
                }
                return new JSONObject(out.toString("UTF-8"));
            }
        } finally {
            c.disconnect();
        }
    }

    public static final class Series {
        public final String id, title, cover, description, badge;
        public final List<Episode> episodes = new ArrayList<>();

        private Series(String id, String title, String cover, String desc, String badge) {
            this.id = id;
            this.title = title;
            this.cover = cover;
            this.description = desc;
            this.badge = badge;
        }

        private static Series fromJson(JSONObject o, boolean includeEpisodes) throws JSONException {
            String id = o.getString("id"), title = o.getString("title");
            if (id.trim().isEmpty() || title.trim().isEmpty())
                throw new JSONException("剧集 ID/标题缺失");
            Series item = new Series(id, title, o.optString("cover", ""),
                    o.optString("description", ""), o.optString("badge", ""));
            if (includeEpisodes) {
                JSONArray arr = o.getJSONArray("episodes");
                if (arr.length() > 400) throw new JSONException("分集数量超出限制");
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject e = arr.getJSONObject(i);
                    String eid = e.getString("id");
                    if (eid.isEmpty()) throw new JSONException("分集缺失 ID");
                    item.episodes.add(new Episode(eid, e.optInt("number", i + 1),
                            e.optString("title", "第 " + (i + 1) + " 集")));
                }
            }
            return item;
        }
    }

    public static final class Episode {
        public final String id;
        public final int number;
        public final String title;
        private Episode(String id, int number, String title) {
            this.id = id;
            this.number = number;
            this.title = title;
        }
    }
}
