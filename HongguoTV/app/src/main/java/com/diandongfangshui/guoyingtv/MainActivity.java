package com.diandongfangshui.guoyingtv;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(13, 17, 26);
    private static final int PANEL = Color.rgb(25, 31, 43);
    private static final int ACCENT = Color.rgb(246, 78, 70);
    private static final int WHITE = Color.WHITE;
    private static final int MUTED = Color.rgb(167, 177, 194);
    private static final ExecutorService IMAGES = Executors.newFixedThreadPool(3);
    private final List<Drama> catalog = new ArrayList<>();
    private FrameLayout stage;
    private LinearLayout sidebar;
    private String currentTab = "精选";
    private String status = "正在读取剧集…";
    private Drama selected = null;
    private boolean isDemo = true;
    private boolean loaded = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        buildShell();
        loadCatalog();
    }

    @Override protected void onResume() {
        super.onResume();
        if (loaded && stage != null) renderPage();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable shape(int fill, int radius, int strokeColor, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (stroke > 0) drawable.setStroke(dp(stroke), strokeColor);
        return drawable;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String content, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(content);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(true);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView button(String content, Runnable action) {
        TextView view = text(content, 16, WHITE, true);
        view.setFocusable(true);
        view.setClickable(true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(18), dp(10), dp(18), dp(10));
        view.setBackground(shape(PANEL, 12, PANEL, 1));
        view.setOnFocusChangeListener((v, focus) -> {
            view.setBackground(focus
                    ? shape(Color.rgb(54, 38, 42), 12, ACCENT, 2)
                    : shape(PANEL, 12, PANEL, 1));
            view.setScaleX(focus ? 1.04f : 1f);
            view.setScaleY(focus ? 1.04f : 1f);
        });
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private void buildShell() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.HORIZONTAL);
        shell.setBackgroundColor(BG);
        setContentView(shell);
        sidebar = vertical();
        sidebar.setBackgroundColor(Color.rgb(20, 25, 35));
        sidebar.setPadding(dp(22), dp(24), dp(18), dp(20));
        shell.addView(sidebar, new LinearLayout.LayoutParams(dp(202),
                ViewGroup.LayoutParams.MATCH_PARENT));
        stage = new FrameLayout(this);
        shell.addView(stage, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    }

    private void buildSidebar() {
        sidebar.removeAllViews();
        TextView logo = text("●  果影 TV", 27, WHITE, true);
        logo.setPadding(0, 0, 0, dp(32));
        sidebar.addView(logo);
        String[] tabs = {"精选", "分类", "搜索", "追剧收藏", "最近观看", "设置"};
        for (String tab : tabs) {
            TextView nav = button(tab, () -> {
                selected = null;
                currentTab = tab;
                renderPage();
            });
            if (currentTab.equals(tab) && selected == null) {
                nav.setTextColor(ACCENT);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
            lp.bottomMargin = dp(11);
            sidebar.addView(nav, lp);
        }
        View spacer = new View(this);
        sidebar.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));
        TextView legal = text("非官方独立客户端\n仅支持授权片单", 11, MUTED, false);
        legal.setLineSpacing(dp(5), 1f);
        sidebar.addView(legal);
    }

    private void loadCatalog() {
        loaded = false;
        status = "正在加载片单…";
        renderPage();
        CatalogStore.load(this, result -> {
            catalog.clear();
            catalog.addAll(result.dramas);
            status = result.notice;
            isDemo = result.demo;
            loaded = true;
            selected = null;
            renderPage();
        });
    }

    private LinearLayout page() {
        buildSidebar();
        stage.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        stage.addView(scroll);
        LinearLayout content = vertical();
        content.setPadding(dp(38), dp(24), dp(42), dp(35));
        scroll.addView(content);
        return content;
    }

    private void heading(LinearLayout col, String title, String subtitle) {
        TextView header = text(title, 32, WHITE, true);
        col.addView(header);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView desc = text(subtitle, 14, MUTED, false);
            desc.setPadding(0, dp(7), 0, dp(18));
            col.addView(desc);
        }
    }

    private void sectionTitle(LinearLayout col, String title) {
        TextView t = text(title, 23, WHITE, true);
        t.setPadding(0, dp(21), 0, dp(12));
        col.addView(t);
    }

    private void renderPage() {
        if (stage == null) return;
        LinearLayout col = page();
        if (selected != null) {
            detail(col, selected);
            return;
        }
        switch (currentTab) {
            case "分类":
                categories(col);
                break;
            case "搜索":
                search(col);
                break;
            case "追剧收藏":
                favorites(col);
                break;
            case "最近观看":
                history(col);
                break;
            case "设置":
                settings(col);
                break;
            default:
                home(col);
                break;
        }
    }

    private void home(LinearLayout col) {
        heading(col, "今天看点什么？", status);
        if (isDemo) {
            TextView warning = text("演示模式：这里是公开测试影片，并非红果正版剧库。在「设置」导入有权限的片单后即可替换。", 14, Color.rgb(255, 202, 139), false);
            warning.setPadding(dp(18), dp(12), dp(18), dp(12));
            warning.setBackground(shape(Color.rgb(64, 43, 27), 10, Color.rgb(94, 63, 36), 1));
            col.addView(warning);
        }
        if (catalog.isEmpty()) {
            sectionTitle(col, "暂无剧集");
            col.addView(button("打开设置并导入片单", () -> { currentTab = "设置"; renderPage(); }));
            return;
        }
        List<Drama> recent = fromIds(getRecentIds());
        if (!recent.isEmpty()) addRow(col, "继续观看", recent);
        addRow(col, "为你精选", catalog);
        Map<String, List<Drama>> groups = new LinkedHashMap<>();
        for (Drama drama : catalog) {
            groups.computeIfAbsent(drama.genre, k -> new ArrayList<>()).add(drama);
        }
        for (Map.Entry<String, List<Drama>> entry : groups.entrySet()) {
            addRow(col, entry.getKey(), entry.getValue());
        }
    }

    private void categories(LinearLayout col) {
        heading(col, "题材分类", "按片单提供的分类查看剧集");
        Map<String, List<Drama>> groups = new LinkedHashMap<>();
        for (Drama drama : catalog) {
            groups.computeIfAbsent(drama.genre, k -> new ArrayList<>()).add(drama);
        }
        if (groups.isEmpty()) sectionTitle(col, "暂无剧集，先导入片单");
        for (Map.Entry<String, List<Drama>> entry : groups.entrySet())
            addRow(col, entry.getKey() + " · " + entry.getValue().size() + " 部", entry.getValue());
    }

    private void favorites(LinearLayout col) {
        List<Drama> favs = fromIds(CatalogStore.prefs(this).getStringSet("favorites", new HashSet<>()));
        heading(col, "我的收藏", favs.size() + " 部短剧保存在当前电视");
        if (favs.isEmpty()) sectionTitle(col, "尚未收藏剧集");
        else addRow(col, "已收藏", favs);
    }

    private void history(LinearLayout col) {
        List<Drama> recent = fromIds(getRecentIds());
        heading(col, "最近观看", "本机保存最近观看记录与每集播放进度");
        if (recent.isEmpty()) sectionTitle(col, "目前没有观看记录");
        else addRow(col, "继续看", recent);
    }

    private List<String> getRecentIds() {
        String value = CatalogStore.prefs(this).getString("recent_ids", "");
        List<String> ids = new ArrayList<>();
        if (!value.isEmpty()) {
            for (String id : value.split(",")) if (!id.isEmpty()) ids.add(id);
        }
        return ids;
    }

    private List<Drama> fromIds(Iterable<String> ids) {
        List<Drama> found = new ArrayList<>();
        for (String id : ids) {
            for (Drama d : catalog) {
                if (d.id.equals(id)) { found.add(d); break; }
            }
        }
        return found;
    }

    private void addRow(LinearLayout col, String title, List<Drama> dramas) {
        if (dramas.isEmpty()) return;
        sectionTitle(col, title);
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setPadding(dp(5), dp(4), dp(12), dp(10));
        hsv.addView(strip);
        for (Drama drama : dramas) {
            LinearLayout card = vertical();
            card.setFocusable(true);
            card.setClickable(true);
            card.setPadding(dp(8), dp(8), dp(8), dp(10));
            card.setBackground(shape(PANEL, 13, PANEL, 1));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(190), dp(253));
            p.rightMargin = dp(16);
            strip.addView(card, p);
            ImageView poster = new ImageView(this);
            poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            poster.setBackground(shape(Color.rgb(49, 55, 71), 8, PANEL, 0));
            card.addView(poster, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(170)));
            loadImage(poster, drama.poster);
            TextView label = text(drama.title, 15, WHITE, true);
            label.setMaxLines(2);
            label.setPadding(0, dp(9), 0, 0);
            card.addView(label);
            TextView note = text(drama.episodes.size() + " 集  ·  " + drama.genre, 11, MUTED, false);
            note.setPadding(0, dp(5), 0, 0);
            card.addView(note);
            card.setOnFocusChangeListener((view, focus) -> {
                card.setBackground(focus
                        ? shape(Color.rgb(48, 38, 45), 13, ACCENT, 2)
                        : shape(PANEL, 13, PANEL, 1));
                card.setScaleX(focus ? 1.035f : 1f);
                card.setScaleY(focus ? 1.035f : 1f);
            });
            card.setOnClickListener(v -> { selected = drama; renderPage(); });
        }
        col.addView(hsv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(278)));
    }

    private void loadImage(ImageView view, String link) {
        if (!CatalogStore.isHttpsUrl(link)) return;
        IMAGES.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(link).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(8000);
                conn.setInstanceFollowRedirects(false);
                if (conn.getResponseCode() != 200) return;
                if (conn.getContentLength() > 3_000_000) return;
                try (InputStream input = conn.getInputStream()) {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = 2;
                    Bitmap img = BitmapFactory.decodeStream(input, null, opts);
                    if (img != null) runOnUiThread(() -> {
                        if (!isFinishing() && view.isAttachedToWindow()) view.setImageBitmap(img);
                    });
                }
            } catch (Exception ignored) {
                // Broken poster is nonfatal: retain placeholder background.
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void detail(LinearLayout col, Drama drama) {
        heading(col, drama.title, drama.genre + " · 全 " + drama.episodes.size() + " 集");
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        col.addView(head);
        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackground(shape(PANEL, 12, PANEL, 0));
        head.addView(poster, new LinearLayout.LayoutParams(dp(215), dp(260)));
        loadImage(poster, drama.poster);
        LinearLayout info = vertical();
        info.setPadding(dp(24), dp(6), 0, 0);
        head.addView(info, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView desc = text(drama.description, 17, MUTED, false);
        desc.setLineSpacing(dp(5), 1f);
        info.addView(desc);
        int saved = CatalogStore.prefs(this).getInt("last_" + drama.id, 0);
        if (saved < 0 || saved >= drama.episodes.size()) saved = 0;
        final int resumeIndex = drama.firstPlayableEpisode(saved);
        TextView play = button(resumeIndex >= 0
                ? "▶  播放 / 继续第 " + drama.episodes.get(resumeIndex).episodeIndex + " 集"
                : "尚未取得授权播放地址",
                () -> {
                    if (resumeIndex >= 0) openPlayer(drama, resumeIndex);
                    else Toast.makeText(this, "已识别剧集元数据，尚缺少授权播放地址", Toast.LENGTH_LONG).show();
                });
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(55));
        playParams.topMargin = dp(23);
        info.addView(play, playParams);
        Set<String> favs = CatalogStore.prefs(this).getStringSet("favorites", new HashSet<>());
        TextView favorite = button(favs.contains(drama.id) ? "♥ 取消收藏" : "♡ 加入收藏", () -> {
            SharedPreferences pref = CatalogStore.prefs(this);
            Set<String> items = new HashSet<>(pref.getStringSet("favorites", new HashSet<>()));
            if (!items.add(drama.id)) items.remove(drama.id);
            pref.edit().putStringSet("favorites", items).apply();
            renderPage();
        });
        LinearLayout.LayoutParams favParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(52));
        favParams.topMargin = dp(10);
        info.addView(favorite, favParams);
        if (drama.metadataAdapter) {
            TextView note = text(drama.hasPlayableEpisodes()
                    ? "导入的剧集已配置独立授权播放地址。"
                    : "仅有剧集元数据：video_id 不是可播放 URL。", 14,
                    Color.rgb(255, 202, 139), false);
            note.setPadding(0, dp(12), 0, 0);
            info.addView(note);
        }
        sectionTitle(col, "选择剧集");
        LinearLayout episodes = vertical();
        LinearLayout currentRow = null;
        for (int i = 0; i < drama.episodes.size(); i++) {
            if (i % 5 == 0) {
                currentRow = new LinearLayout(this);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(59));
                rp.bottomMargin = dp(9);
                episodes.addView(currentRow, rp);
            }
            final int episodeIndex = i;
            Drama.Episode episodeData = drama.episodes.get(i);
            TextView item = button(episodeData.episodeIndex + (episodeData.isPlayable() ? " ▶" : " ·"),
                    () -> openPlayer(drama, episodeIndex));
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(dp(91), dp(50));
            ep.rightMargin = dp(10);
            currentRow.addView(item, ep);
        }
        col.addView(episodes);
        play.requestFocus();
    }

    private void search(LinearLayout col) {
        heading(col, "搜索短剧", "搜索本机当前片单，使用电视遥控器输入关键词");
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(WHITE);
        input.setHintTextColor(MUTED);
        input.setHint("输入剧名或题材");
        input.setTextSize(18);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setPadding(dp(15), 0, dp(15), 0);
        input.setBackground(shape(PANEL, 10, ACCENT, 1));
        col.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        LinearLayout results = vertical();
        TextView go = button("搜索", () -> showSearchResults(results, input.getText().toString()));
        LinearLayout.LayoutParams goP = new LinearLayout.LayoutParams(dp(160), dp(52));
        goP.topMargin = dp(12);
        col.addView(go, goP);
        col.addView(results);
        input.setOnEditorActionListener((v, actionId, event) -> {
            showSearchResults(results, input.getText().toString());
            return true;
        });
        showSearchResults(results, "");
    }

    private void showSearchResults(LinearLayout container, String query) {
        container.removeAllViews();
        String needle = query.trim().toLowerCase(java.util.Locale.ROOT);
        List<Drama> found = new ArrayList<>();
        for (Drama drama : catalog) {
            if (needle.isEmpty() || drama.title.toLowerCase(java.util.Locale.ROOT).contains(needle)
                    || drama.genre.toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                found.add(drama);
            }
        }
        if (found.isEmpty()) sectionTitle(container, "没有找到匹配剧集");
        else addRow(container, "找到 " + found.size() + " 部", found);
    }

    private void settings(LinearLayout col) {
        heading(col, "片源与设置", "配置合法授权的 HTTPS JSON 片单。所有数据保存在当前电视中");
        sectionTitle(col, "在线片单地址");
        EditText url = new EditText(this);
        url.setSingleLine(true);
        url.setTextSize(16);
        url.setTextColor(WHITE);
        url.setHintTextColor(MUTED);
        url.setHint("https://example.com/catalog.json");
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setText(CatalogStore.prefs(this).getString(CatalogStore.CATALOG_URL, ""));
        url.setSelectAllOnFocus(true);
        url.setPadding(dp(14), 0, dp(14), 0);
        url.setBackground(shape(PANEL, 9, MUTED, 1));
        col.addView(url, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        TextView save = button("保存并更新剧库", () -> {
            String value = url.getText().toString().trim();
            if (!CatalogStore.isHttpsUrl(value)) {
                Toast.makeText(this, "请输入有效 HTTPS 地址", Toast.LENGTH_LONG).show();
                return;
            }
            CatalogStore.prefs(this).edit().putString(CatalogStore.CATALOG_URL, value).apply();
            selected = null;
            currentTab = "精选";
            loadCatalog();
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(235), dp(55));
        sp.topMargin = dp(15);
        col.addView(save, sp);
        TextView reset = button("恢复公开演示片单", () -> {
            CatalogStore.prefs(this).edit().remove(CatalogStore.CATALOG_URL).apply();
            deleteFile("catalog-cache.json");
            selected = null;
            currentTab = "精选";
            loadCatalog();
        });
        LinearLayout.LayoutParams resetP = new LinearLayout.LayoutParams(dp(235), dp(55));
        resetP.topMargin = dp(11);
        col.addView(reset, resetP);
        sectionTitle(col, "使用说明");
        TextView instructions = text("● 支持 HTTPS MP4 / HLS(m3u8)，选集与自动连播。\n\n"
                + "● 示例 JSON 格式见 GitHub 仓库；片单不是直接的视频列表，每部剧都有独立 episodes。\n\n"
                + "● 本应用不是红果官方客户端，也不包含未获授权的视频接口、绕过 DRM 的功能或平台账号登录。\n\n"
                + "● 若媒体服务器要求登录、特殊请求头或 DRM，此版不能直接播放。", 16, MUTED, false);
        instructions.setLineSpacing(dp(5), 1f);
        col.addView(instructions);
    }

    private void openPlayer(Drama drama, int episode) {
        if (episode < 0 || episode >= drama.episodes.size()
                || !drama.episodes.get(episode).isPlayable()) {
            Toast.makeText(this, "本集尚无授权的 HTTPS 媒体地址", Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("drama", drama.toJson().toString());
        intent.putExtra("episode", episode);
        startActivity(intent);
    }

    @Override public void onBackPressed() {
        if (selected != null) {
            selected = null;
            renderPage();
        } else if (!currentTab.equals("精选")) {
            currentTab = "精选";
            renderPage();
        } else {
            super.onBackPressed();
        }
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        return super.onKeyDown(keyCode, event);
    }
}
