package com.diandongfangshui.guoyingtv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** TV remote focused client for a self-hosted content bridge, not for official platform APIs. */
public final class BridgeActivity extends Activity {
    private static final int BG = 0xff111621, PANEL = 0xff242c3b, FOCUS = 0xfff05c50;
    private static final int TEXT = Color.WHITE, SECONDARY = 0xffa8b4c6;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final ExecutorService POSTERS = Executors.newFixedThreadPool(3);
    private String lastSeriesId = "";
    private LinearLayout root, body, listView;
    private EditText baseInput, tokenInput, searchInput;
    private TextView stateText;
    private BridgeApi api;
    private final List<BridgeApi.Series> currentList = new ArrayList<>();
    private BridgeApi.Series detail;
    private boolean searching;
    private int page = 1;
    private String keyword = "";
    private int generation = 0;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        buildUI();
        String saved = CatalogStore.prefs(this).getString(BridgeApi.PREF_BASE, "");
        if (!saved.isEmpty()) {
            try {
                api = BridgeApi.fromSaved(this);
                loadList(false, 1);
            } catch (Exception e) {
                state("保存的地址不受支持，请检查设置");
            }
        }
    }

    private int dp(float x) { return (int)(x * getResources().getDisplayMetrics().density + 0.5f); }

    private GradientDrawable shape(int color, boolean focus) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(focus ? 0xff4b353e : color);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(focus ? 2 : 1), focus ? FOCUS : PANEL);
        return bg;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private TextView button(String label, Runnable task) {
        TextView view = text(label, 17, TEXT);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(14), dp(11), dp(14), dp(11));
        view.setBackground(shape(PANEL, false));
        view.setFocusable(true);
        view.setClickable(true);
        view.setOnFocusChangeListener((v, focused) -> {
            view.setBackground(shape(PANEL, focused));
            view.setScaleX(focused ? 1.05f : 1f);
            view.setScaleY(focused ? 1.05f : 1f);
            view.setElevation(dp(focused ? 10 : 0));
        });
        view.setOnClickListener(v -> task.run());
        return view;
    }

    private void buildUI() {
        root = column();
        root.setBackgroundColor(BG);
        setContentView(root);
        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        root.addView(scroller);
        body = column();
        body.setPadding(dp(40), dp(22), dp(40), dp(40));
        scroller.addView(body);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(header);
        TextView back = button("‹ 返回果影 TV", () -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(188), dp(52)));
        TextView title = text("  短剧片库 · 大屏模式", 28, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title);
        TextView subtitle = text("遥控器方向键浏览海报 · 确定查看分集 · 菜单键回到搜索 · 使用自己管理的内容服务", 14, SECONDARY);
        subtitle.setPadding(0, dp(16), 0, dp(10));
        body.addView(subtitle);

        LinearLayout settings = new LinearLayout(this);
        settings.setOrientation(LinearLayout.HORIZONTAL);
        settings.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(settings);
        baseInput = edit("http://192.168.1.10:8787", false);
        baseInput.setText(CatalogStore.prefs(this).getString(BridgeApi.PREF_BASE, ""));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(52), 4f);
        bp.rightMargin = dp(10);
        settings.addView(baseInput, bp);
        tokenInput = edit("可选：服务访问口令", true);
        tokenInput.setText(CatalogStore.prefs(this).getString(BridgeApi.PREF_TOKEN, ""));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, dp(52), 2f);
        tp.rightMargin = dp(10);
        settings.addView(tokenInput, tp);
        TextView connect = button("连接", this::connect);
        settings.addView(connect, new LinearLayout.LayoutParams(dp(115), dp(52)));

        stateText = text("仅允许局域网 IPv4；若使用 HTTP，局域网传输不加密，请勿输入平台账号密码。", 13, 0xffffc996);
        stateText.setPadding(0, dp(10), 0, dp(12));
        body.addView(stateText);
        LinearLayout commands = new LinearLayout(this);
        commands.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(commands);
        commands.addView(button("精选首页", () -> loadList(false, 1)));
        commands.addView(space(12));
        searchInput = edit("输入关键词搜索剧名", false);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(dp(310), dp(52));
        commands.addView(searchInput, searchParams);
        commands.addView(space(10));
        commands.addView(button("搜索", this::search));
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            search();
            return true;
        });

        listView = column();
        body.addView(listView);
        heading("设置服务地址，点击「连接」后即可浏览。");
    }

    private View space(int width) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(width), dp(1)));
        return v;
    }

    private EditText edit(String hint, boolean secret) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(TEXT);
        input.setHintTextColor(SECONDARY);
        input.setTextSize(15);
        input.setHint(hint);
        input.setSelectAllOnFocus(true);
        input.setPadding(dp(13), 0, dp(13), 0);
        input.setBackground(shape(PANEL, false));
        input.setInputType(secret ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT);
        return input;
    }

    private void state(String message) { if (stateText != null) stateText.setText(message); }

    private void heading(String title) {
        listView.removeAllViews();
        TextView h = text(title, 22, TEXT);
        h.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        h.setPadding(0, dp(19), 0, dp(12));
        listView.addView(h);
    }

    private void connect() {
        final BridgeApi candidate;
        try {
            candidate = new BridgeApi(baseInput.getText().toString(), tokenInput.getText().toString());
        } catch (Exception e) {
            state(e.getMessage());
            return;
        }
        state("正在检测内容服务连接...");
        worker.execute(() -> {
            try {
                candidate.health();
                runOnUiThread(() -> {
                    api = candidate;
                    CatalogStore.prefs(this).edit()
                            .putString(BridgeApi.PREF_BASE, candidate.base())
                            .putString(BridgeApi.PREF_TOKEN, candidate.token())
                            .apply();
                    state("连接成功 · 内容服务 API v1 · 口令仅存储在本电视");
                    loadList(false, 1);
                });
            } catch (Exception e) {
                runOnUiThread(() -> state("连接失败：" + e.getMessage()));
            }
        });
    }

    private void search() {
        keyword = searchInput.getText().toString().trim();
        if (keyword.isEmpty()) {
            state("请输入搜索关键词");
            return;
        }
        loadList(true, 1);
    }

    private void loadList(boolean useSearch, int newPage) {
        if (api == null) { state("请先连接内容服务"); return; }
        searching = useSearch;
        page = newPage;
        detail = null;
        final int request = ++generation;
        state("正在加载" + (useSearch ? "搜索结果" : "精选目录") + "，第 " + newPage + " 页...");
        heading("读取片库中...");
        worker.execute(() -> {
            try {
                List<BridgeApi.Series> records = useSearch
                        ? api.search(keyword, newPage) : api.home(newPage);
                runOnUiThread(() -> {
                    if (request != generation || isFinishing()) return;
                    currentList.clear();
                    currentList.addAll(records);
                    state("已连接 · " + records.size() + " 部 / 第 " + newPage + " 页");
                    renderList();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (request != generation) return;
                    state("加载失败：" + e.getMessage());
                    heading("无法获取数据 · 检查电脑/NAS 上的服务是否运行");
                });
            }
        });
    }

    private void renderList() {
        heading(searching ? "搜索：" + keyword : "短剧精选");
        View focusCard = null;
        if (currentList.isEmpty()) {
            listView.addView(text("没有剧目，或者服务未获取到可用内容。", 16, SECONDARY));
        } else {
            LinearLayout row = null;
            for (int i = 0; i < currentList.size(); i++) {
                if (i % 4 == 0) {
                    row = new LinearLayout(this);
                    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dp(280));
                    rowParams.bottomMargin = dp(20);
                    listView.addView(row, rowParams);
                }
                BridgeApi.Series series = currentList.get(i);
                LinearLayout card = column();
                card.setFocusable(true);
                card.setClickable(true);
                card.setPadding(dp(9), dp(9), dp(9), dp(9));
                card.setBackground(shape(PANEL, false));
                card.setContentDescription(series.title + "，确定键查看分集");
                ImageView image = new ImageView(this);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setBackground(shape(0xff3b4455, false));
                card.addView(image, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(187)));
                fetchPoster(image, series.cover);
                TextView name = text(series.title, 17, TEXT);
                name.setMaxLines(2);
                name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                name.setPadding(0, dp(10), 0, dp(4));
                card.addView(name);
                if (!series.badge.isEmpty()) {
                    TextView tag = text(series.badge, 12, 0xffffbcaa);
                    tag.setMaxLines(1);
                    card.addView(tag);
                }
                card.setOnFocusChangeListener((v, focused) -> {
                    card.setBackground(shape(PANEL, focused));
                    card.setScaleX(focused ? 1.045f : 1f);
                    card.setScaleY(focused ? 1.045f : 1f);
                    card.setElevation(dp(focused ? 12 : 0));
                    if (focused) lastSeriesId = series.id;
                });
                card.setOnClickListener(v -> {
                    lastSeriesId = series.id;
                    loadDetail(series.id);
                });
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(274), 1f);
                cp.rightMargin = dp(13);
                row.addView(card, cp);
                if (focusCard == null || series.id.equals(lastSeriesId)) focusCard = card;
            }
        }
        LinearLayout pager = new LinearLayout(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(57));
        params.topMargin = dp(13);
        listView.addView(pager, params);
        pager.addView(button("上一页", () -> loadList(searching, Math.max(1, page - 1))));
        pager.addView(space(12));
        pager.addView(button("下一页", () -> loadList(searching, Math.min(200, page + 1))));
        if (focusCard != null) focusCard.requestFocus();
    }

    private void fetchPoster(ImageView target, String uri) {
        if (!UrlPolicy.isHttpsUrl(uri)) return;
        POSTERS.execute(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(uri).openConnection();
                c.setConnectTimeout(5000);
                c.setReadTimeout(8000);
                c.setInstanceFollowRedirects(false);
                if (c.getResponseCode() != 200 || c.getContentLength() > 3_000_000) return;
                try (InputStream in = c.getInputStream()) {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = 2;
                    Bitmap image = BitmapFactory.decodeStream(in, null, opts);
                    if (image != null) runOnUiThread(() -> {
                        if (!isFinishing() && target.isAttachedToWindow()) target.setImageBitmap(image);
                    });
                }
            } catch (Exception ignored) {
                // Poster failures retain the placeholder.
            } finally {
                if (c != null) c.disconnect();
            }
        });
    }

    private void loadDetail(String id) {
        if (api == null) return;
        int request = ++generation;
        state("正在读取剧集详情...");
        heading("载入分集列表...");
        worker.execute(() -> {
            try {
                BridgeApi.Series s = api.series(id);
                runOnUiThread(() -> {
                    if (request != generation || isFinishing()) return;
                    detail = s;
                    state("已加载 " + s.episodes.size() + " 集");
                    renderDetail(s);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (request != generation) return;
                    state("详情失败：" + e.getMessage());
                    heading("暂时无法加载剧集详情");
                    listView.addView(button("返回片库", this::renderList));
                });
            }
        });
    }

    private void renderDetail(BridgeApi.Series series) {
        heading(series.title + " · " + series.episodes.size() + " 集");
        listView.addView(text(series.description, 16, SECONDARY));
        LinearLayout.LayoutParams space = new LinearLayout.LayoutParams(dp(150), dp(52));
        space.topMargin = dp(12);
        listView.addView(button("‹ 返回片库", this::renderList), space);
        if (series.episodes.isEmpty()) {
            listView.addView(text("服务未返回可播放分集。", 15, SECONDARY));
            return;
        }
        LinearLayout line = null;
        View firstEpisode = null;
        for (int i = 0; i < series.episodes.size(); i++) {
            if (i % 6 == 0) {
                line = new LinearLayout(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(60));
                lp.topMargin = dp(8);
                listView.addView(line, lp);
            }
            final int index = i;
            BridgeApi.Episode ep = series.episodes.get(i);
            TextView b = button(String.valueOf(ep.number), () -> play(series, index));
            LinearLayout.LayoutParams epParams = new LinearLayout.LayoutParams(dp(94), dp(52));
            epParams.rightMargin = dp(12);
            line.addView(b, epParams);
            if (firstEpisode == null) firstEpisode = b;
        }
        if (firstEpisode != null) firstEpisode.requestFocus();
    }

    private void play(BridgeApi.Series series, int episodeIndex) {
        String[] ids = new String[series.episodes.size()];
        String[] titles = new String[series.episodes.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = series.episodes.get(i).id;
            titles[i] = series.episodes.get(i).title;
        }
        Intent intent = new Intent(this, BridgePlayerActivity.class);
        intent.putExtra("series_id", series.id);
        intent.putExtra("series_title", series.title);
        intent.putExtra("episode_ids", ids);
        intent.putExtra("episode_titles", titles);
        intent.putExtra("episode", episodeIndex);
        startActivity(intent);
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (detail != null) {
                detail = null;
                ++generation;
                renderList();
            } else if (searchInput != null) {
                searchInput.requestFocus();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override public void onBackPressed() {
        if (detail != null) {
            detail = null;
            ++generation;
            renderList();
        } else {
            super.onBackPressed();
        }
    }

    @Override protected void onDestroy() {
        ++generation;
        worker.shutdownNow();
        super.onDestroy();
    }
}
