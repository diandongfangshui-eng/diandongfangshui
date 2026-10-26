package com.diandongfangshui.guoyingtv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CatalogStore {
    public static final String PREF = "guoying_tv";
    public static final String CATALOG_URL = "catalog_url";
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int MAX_BYTES = 2_000_000;

    private CatalogStore() {}

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public interface Callback {
        void onLoaded(Result result);
    }

    public static final class Result {
        public final List<Drama> dramas;
        public final String notice;
        public final boolean demo;
        Result(List<Drama> dramas, String notice, boolean demo) {
            this.dramas = dramas;
            this.notice = notice;
            this.demo = demo;
        }
    }

    public static boolean isHttpsUrl(String raw) {
        try {
            URL url = new URL(raw);
            return "https".equalsIgnoreCase(url.getProtocol())
                    && url.getHost() != null && !url.getHost().isEmpty()
                    && url.getUserInfo() == null;
        } catch (Exception e) {
            return false;
        }
    }

    public static void load(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        WORKERS.execute(() -> {
            String configured = prefs(app).getString(CATALOG_URL, "").trim();
            Result result;
            if (!configured.isEmpty()) {
                try {
                    if (!isHttpsUrl(configured)) throw new IllegalArgumentException("片单地址必须使用 HTTPS");
                    String body = download(configured);
                    List<Drama> records = parse(body);
                    File cache = new File(app.getFilesDir(), "catalog-cache.json");
                    try (FileOutputStream fos = new FileOutputStream(cache)) {
                        fos.write(body.getBytes(StandardCharsets.UTF_8));
                    }
                    result = new Result(records, "已加载自定义片单 · " + records.size() + " 部", false);
                } catch (Exception remoteError) {
                    try {
                        File cache = new File(app.getFilesDir(), "catalog-cache.json");
                        try (InputStream in = new FileInputStream(cache)) {
                            result = new Result(parse(readLimit(in)), "网络更新失败，使用上次成功加载的片单", false);
                        }
                    } catch (Exception cacheError) {
                        result = getDemo(app, "片单加载失败：" + remoteError.getMessage() + "。当前显示演示样片");
                    }
                }
            } else {
                result = getDemo(app, "当前为公开演示样片。请在设置中导入你有权使用的 HTTPS 剧集片单");
            }
            Result loaded = result;
            MAIN.post(() -> callback.onLoaded(loaded));
        });
    }

    private static Result getDemo(Context app, String notice) {
        try (InputStream in = app.getAssets().open("demo_catalog.json")) {
            return new Result(parse(readLimit(in)), notice, true);
        } catch (Exception e) {
            return new Result(new ArrayList<>(), "演示片单读取失败：" + e.getMessage(), true);
        }
    }

    private static List<Drama> parse(String text) throws JSONException {
        JSONObject root = new JSONObject(text);
        if (root.optInt("version", -1) != 1) throw new JSONException("Expected version=1");
        JSONArray dramas = root.getJSONArray("dramas");
        if (dramas.length() > 300) throw new JSONException("Too many entries");
        List<Drama> parsed = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < dramas.length(); i++) {
            Drama drama = Drama.fromJson(dramas.getJSONObject(i));
            if (!ids.add(drama.id)) throw new JSONException("Duplicate drama id: " + drama.id);
            parsed.add(drama);
        }
        return parsed;
    }

    private static String download(String target) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(target).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(12000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json");
        try {
            if (connection.getResponseCode() != 200) {
                throw new IllegalStateException("HTTP " + connection.getResponseCode());
            }
            try (InputStream in = connection.getInputStream()) {
                return readLimit(in);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String readLimit(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
            if (out.size() + n > MAX_BYTES) throw new IllegalArgumentException("片单超过 2MB");
            out.write(buffer, 0, n);
        }
        return out.toString("UTF-8");
    }
}
