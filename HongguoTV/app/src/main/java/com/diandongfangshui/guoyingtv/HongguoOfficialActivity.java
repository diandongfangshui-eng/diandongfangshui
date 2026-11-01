package com.diandongfangshui.guoyingtv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Loads public pages from the actual hongguoduanju.com website.
 * This is NOT an App API replacement or a DRM / authentication bypass.
 * The site itself determines which episodes can be played in a web browser.
 */
public final class HongguoOfficialActivity extends Activity {
    private static final String HOME = "https://hongguoduanju.com/";
    private static final String RANK = "https://hongguoduanju.com/rank/hot-drama";
    private static final String DRAMA = "https://hongguoduanju.com/category/real-drama";
    private static final int BG = 0xff10151e;
    private FrameLayout root;
    private LinearLayout toolbar;
    private TextView status;
    private WebView browser;
    private View fullscreenVideo;
    private WebChromeClient.CustomViewCallback videoCallback;
    private TextView cursor;
    private boolean pointerMode = false;
    private float cursorX = 0f, cursorY = 0f;

    private static boolean isOfficialPage(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) return false;
        String host = uri.getHost();
        return "hongguoduanju.com".equalsIgnoreCase(host)
                || "www.hongguoduanju.com".equalsIgnoreCase(host);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView action(String value, Runnable fn) {
        TextView button = new TextView(this);
        button.setText(value);
        button.setTextSize(15);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(11), dp(10), dp(11), dp(10));
        button.setFocusable(true);
        button.setClickable(true);
        GradientDrawable ordinary = new GradientDrawable();
        ordinary.setColor(0xff293240);
        ordinary.setCornerRadius(dp(9));
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(0xff684048);
        focused.setCornerRadius(dp(9));
        focused.setStroke(dp(2), 0xffef685b);
        button.setBackground(ordinary);
        button.setOnFocusChangeListener((v, hasFocus) -> {
            button.setBackground(hasFocus ? focused : ordinary);
            button.setScaleX(hasFocus ? 1.06f : 1f);
            button.setScaleY(hasFocus ? 1.06f : 1f);
            button.setElevation(dp(hasFocus ? 9 : 0));
        });
        button.setOnClickListener(v -> fn.run());
        return button;
    }

    private void addAction(LinearLayout row, String label, Runnable fn) {
        TextView button = action(label, fn);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(43));
        p.rightMargin = dp(9);
        row.addView(button, p);
    }

    private void loadOfficial(String url) {
        if (browser == null) return;
        Uri uri = Uri.parse(url);
        if (isOfficialPage(uri)) browser.loadUrl(uri.toString());
        else Toast.makeText(this, "仅允许浏览红果官网 HTTPS 页面", Toast.LENGTH_LONG).show();
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        setContentView(root);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        root.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(12), dp(6), dp(12), dp(6));
        toolbar.setBackgroundColor(BG);
        page.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
        addAction(toolbar, "‹ 返回", this::finish);
        addAction(toolbar, "官网首页", () -> loadOfficial(HOME));
        addAction(toolbar, "热播榜", () -> loadOfficial(RANK));
        addAction(toolbar, "真人剧", () -> loadOfficial(DRAMA));
        addAction(toolbar, "后退", () -> { if (browser.canGoBack()) browser.goBack(); });
        addAction(toolbar, "刷新", () -> browser.reload());
        addAction(toolbar, "遥控光标", this::togglePointer);
        addAction(toolbar, "放大", () -> browser.zoomIn());
        addAction(toolbar, "缩小", () -> browser.zoomOut());

        status = new TextView(this);
        status.setText("官方网站公开页面 · 菜单键打开工具栏 · 选择“遥控光标”后方向键移动、确定键点击");
        status.setTextColor(0xffb8c3d2);
        status.setSingleLine(true);
        status.setTextSize(12);
        status.setPadding(dp(18), dp(5), dp(18), dp(5));
        page.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));

        browser = new WebView(this);
        browser.setFocusable(true);
        browser.setFocusableInTouchMode(true);
        browser.setBackgroundColor(Color.WHITE);
        page.addView(browser, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        android.webkit.WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        if (Build.VERSION.SDK_INT >= 26) {
            settings.setSafeBrowsingEnabled(true);
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, false);
        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                if (!req.isForMainFrame()) return false;
                if (isOfficialPage(req.getUrl())) return false;
                status.setText("已阻止跳转到非红果官网页面");
                return true;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (isOfficialPage(Uri.parse(url))) return false;
                status.setText("已阻止跳转到非红果官网页面");
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                status.setText(pointerMode
                        ? "光标模式：方向键移动 · 确定键点击 · 频道上下键滚动 · 菜单键退出光标"
                        : "官网公开内容 · 菜单键打开顶部工具栏 · 点击“遥控光标”进入光标模式");
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest req,
                                                  WebResourceError err) {
                if (req.isForMainFrame())
                    status.setText("网页加载失败：请检查电视网络、网站可访问性或 WebView 版本");
            }
        });
        browser.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest request) {
                request.deny();
            }
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullscreenVideo != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                fullscreenVideo = view;
                videoCallback = callback;
                toolbar.setVisibility(View.GONE);
                status.setVisibility(View.GONE);
                if (cursor != null) cursor.setVisibility(View.GONE);
                browser.setVisibility(View.GONE);
                root.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                view.requestFocus();
            }
            @Override public void onHideCustomView() { exitFullscreen(); }
        });
        cursor = new TextView(this);
        cursor.setText("＋");
        cursor.setTextColor(Color.WHITE);
        cursor.setTextSize(26);
        cursor.setGravity(Gravity.CENTER);
        cursor.setFocusable(false);
        cursor.setClickable(false);
        cursor.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        GradientDrawable crosshair = new GradientDrawable();
        crosshair.setShape(GradientDrawable.OVAL);
        crosshair.setColor(0xd6e34c41);
        crosshair.setStroke(dp(2), Color.WHITE);
        cursor.setBackground(crosshair);
        cursor.setElevation(dp(26));
        root.addView(cursor, new FrameLayout.LayoutParams(dp(45), dp(45)));
        cursor.setVisibility(View.GONE);
        loadOfficial(HOME);
        browser.requestFocus();
    }

    private void exitFullscreen() {
        if (fullscreenVideo == null) return;
        root.removeView(fullscreenVideo);
        fullscreenVideo = null;
        WebChromeClient.CustomViewCallback callback = videoCallback;
        videoCallback = null;
        toolbar.setVisibility(View.VISIBLE);
        status.setVisibility(View.VISIBLE);
        browser.setVisibility(View.VISIBLE);
        if (callback != null) callback.onCustomViewHidden();
        if (cursor != null && pointerMode) {
            cursor.setVisibility(View.VISIBLE);
            browser.post(this::positionPointer);
        }
        browser.requestFocus();
    }

    /** User-driven touch cursor for Android TV WebView pages without a touchscreen. */
    private void positionPointer() {
        if (!pointerMode || browser == null || cursor == null) return;
        int w = browser.getWidth(), h = browser.getHeight();
        if (w <= 0 || h <= 0) return;
        cursorX = Math.max(dp(18), Math.min(w - dp(18), cursorX));
        cursorY = Math.max(dp(18), Math.min(h - dp(18), cursorY));
        cursor.setX(browser.getLeft() + cursorX - dp(22));
        cursor.setY(toolbar.getHeight() + status.getHeight() + cursorY - dp(22));
    }

    private void togglePointer() {
        if (fullscreenVideo != null) return;
        pointerMode = !pointerMode;
        if (pointerMode) {
            cursorX = browser.getWidth() / 2f;
            cursorY = browser.getHeight() / 2f;
            cursor.setVisibility(View.VISIBLE);
            browser.post(this::positionPointer);
            browser.requestFocus();
            status.setText("光标模式：方向键移动 · 确定键点击 · 频道上下键滚动 · 菜单键退出");
        } else {
            cursor.setVisibility(View.GONE);
            status.setText("菜单键打开工具栏；也可选择“遥控光标”重新进入光标模式");
            toolbar.getChildAt(0).requestFocus();
        }
    }

    private void tapPointer() {
        if (browser == null || browser.getWidth() <= 0 || browser.getHeight() <= 0) return;
        long when = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(when, when, MotionEvent.ACTION_DOWN,
                cursorX, cursorY, 0);
        MotionEvent up = MotionEvent.obtain(when, when + 80, MotionEvent.ACTION_UP,
                cursorX, cursorY, 0);
        try {
            browser.dispatchTouchEvent(down);
            browser.dispatchTouchEvent(up);
        } finally {
            down.recycle();
            up.recycle();
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (!pointerMode || fullscreenVideo != null || event.getAction() != KeyEvent.ACTION_DOWN)
            return super.dispatchKeyEvent(event);
        int key = event.getKeyCode();
        int step = dp(event.getRepeatCount() > 3 ? 85 : 44);
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_LEFT: cursorX -= step; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: cursorX += step; break;
            case KeyEvent.KEYCODE_DPAD_UP: cursorY -= step; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: cursorY += step; break;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                tapPointer();
                return true;
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                browser.pageUp(false);
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                browser.pageDown(false);
                return true;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                togglePointer();
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
        positionPointer();
        return true;
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (fullscreenVideo != null) { exitFullscreen(); return true; }
            if (pointerMode) { togglePointer(); return true; }
            if (browser != null && browser.canGoBack()) { browser.goBack(); return true; }
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (browser != null && fullscreenVideo == null) {
                toolbar.getChildAt(0).requestFocus();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onPause() {
        if (browser != null) browser.onPause();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (browser != null) browser.onResume();
    }

    @Override protected void onDestroy() {
        if (fullscreenVideo != null) exitFullscreen();
        if (browser != null) {
            pointerMode = false;
            browser.stopLoading();
            browser.setWebChromeClient(null);
            browser.setWebViewClient(null);
            browser.destroy();
            browser = null;
        }
        super.onDestroy();
    }
}
