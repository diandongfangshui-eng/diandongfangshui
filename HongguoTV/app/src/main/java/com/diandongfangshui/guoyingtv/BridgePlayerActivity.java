package com.diandongfangshui.guoyingtv;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** On-demand, range-capable MP4 playback from the user's own local content bridge. */
public final class BridgePlayerActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ExoPlayer player;
    private BridgeApi api;
    private TextView titleText, helpText;
    private String seriesId, seriesTitle;
    private String[] ids, titles;
    private int current, requestId = 0;
    private boolean retryUsed = false;
    private boolean switching = false;
    private final float[] speeds = {1f, 1.25f, 1.5f, 2f};
    private int speed = 0;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        seriesId = getIntent().getStringExtra("series_id");
        seriesTitle = getIntent().getStringExtra("series_title");
        ids = getIntent().getStringArrayExtra("episode_ids");
        titles = getIntent().getStringArrayExtra("episode_titles");
        if (ids == null || titles == null || ids.length == 0 || ids.length != titles.length
                || ids.length > 400 || seriesId == null || seriesId.isEmpty()) {
            Toast.makeText(this, "片库数据无效", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        current = Math.max(0, Math.min(ids.length - 1, getIntent().getIntExtra("episode", 0)));
        try {
            api = BridgeApi.fromSaved(this);
        } catch (Exception e) {
            Toast.makeText(this, "请重新设置内容服务", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        build();
        playEpisode(current);
    }

    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);
        player = new ExoPlayer.Builder(this).build();
        PlayerView video = new PlayerView(this);
        video.setUseController(false);
        video.setPlayer(player);
        video.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        video.setFocusable(true);
        root.addView(video, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(dp(20), dp(10), dp(20), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xcc121721);
        bg.setCornerRadius(dp(12));
        overlay.setBackground(bg);
        titleText = new TextView(this);
        titleText.setTextSize(19);
        titleText.setTextColor(Color.WHITE);
        overlay.addView(titleText);
        helpText = new TextView(this);
        helpText.setTextSize(13);
        helpText.setTextColor(0xffd2dae5);
        helpText.setPadding(0, dp(6), 0, 0);
        overlay.addView(helpText);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        lp.leftMargin = dp(35);
        lp.rightMargin = dp(35);
        lp.bottomMargin = dp(25);
        root.addView(overlay, lp);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (!switching && state == Player.STATE_ENDED) {
                    int completed = current;
                    CatalogStore.prefs(BridgePlayerActivity.this).edit()
                            .remove(positionKey(completed)).apply();
                    if (current + 1 < ids.length) playEpisode(current + 1);
                    else helpText.setText("全部剧集已播放完毕 · 按返回键退出");
                }
            }

            @Override public void onPlayerError(PlaybackException error) {
                if (!retryUsed) {
                    retryUsed = true;
                    long resume = Math.max(0, player.getCurrentPosition());
                    helpText.setText("播放异常，重新解析媒体地址...");
                    resolveAndStart(current, true, resume);
                } else {
                    helpText.setText("播放失败：" + error.getErrorCodeName()
                            + "。请确认内容服务及上游片源可用。按菜单键重试。");
                }
            }
        });
        video.requestFocus();
    }

    private String positionKey(int index) {
        return "bridge_pos_" + seriesId + "_" + ids[index];
    }

    private void savePosition() {
        if (player == null || ids == null || player.getPlaybackState() == Player.STATE_ENDED)
            return;
        CatalogStore.prefs(this).edit()
                .putLong(positionKey(current), Math.max(0L, player.getCurrentPosition()))
                .putInt("bridge_last_" + seriesId, current).apply();
    }

    private void playEpisode(int index) {
        if (index < 0 || index >= ids.length || player == null) return;
        switching = true;
        if (player.getMediaItemCount() != 0 && player.getPlaybackState() != Player.STATE_ENDED) {
            savePosition();
        }
        current = index;
        retryUsed = false;
        CatalogStore.prefs(this).edit().putInt("bridge_last_" + seriesId, index).apply();
        titleText.setText(seriesTitle + " · " + titles[index]);
        helpText.setText("加载播放源...  左/右快退快进 10 秒 · 上/下切集 · 确定暂停 · 菜单重试");
        long last = CatalogStore.prefs(this).getLong(positionKey(index), 0L);
        resolveAndStart(index, false, last);
    }

    private void resolveAndStart(int episodeIndex, boolean fresh, long position) {
        int generation = ++requestId;
        worker.execute(() -> {
            try {
                String path = api.resolve(ids[episodeIndex], fresh);
                runOnUiThread(() -> {
                    if (isFinishing() || generation != requestId || player == null) return;
                    try {
                        Map<String, String> headers = new LinkedHashMap<>();
                        if (!api.token().isEmpty()) {
                            headers.put("Authorization", "Bearer " + api.token());
                        }
                        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                                .setUserAgent("GuoyingTV/Bridge-0.3")
                                .setConnectTimeoutMs(8000)
                                .setReadTimeoutMs(15000)
                                .setAllowCrossProtocolRedirects(false)
                                .setDefaultRequestProperties(headers);
                        ProgressiveMediaSource media = new ProgressiveMediaSource.Factory(http)
                                .createMediaSource(MediaItem.fromUri(path));
                        player.setMediaSource(media);
                        player.prepare();
                        if (position > 0) player.seekTo(position);
                        player.setPlaybackSpeed(speeds[speed]);
                        player.play();
                        switching = false;
                        helpText.setText("左/右快退快进 10 秒 · 上/下切集 · 确定暂停 · 菜单重新解析");
                    } catch (Exception error) {
                        switching = false;
                        helpText.setText("播放初始化失败：" + error.getMessage());
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (generation != requestId || isFinishing()) return;
                    switching = false;
                    helpText.setText("媒体地址获取失败：" + e.getMessage()
                            + " · 按菜单键重试");
                });
            }
        });
    }

    private void seek(long by) {
        if (player == null) return;
        long target = Math.max(0, player.getCurrentPosition() + by);
        if (player.getDuration() > 0) target = Math.min(target, player.getDuration());
        player.seekTo(target);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (player == null || event.getAction() != KeyEvent.ACTION_DOWN)
            return super.dispatchKeyEvent(event);
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (player.isPlaying()) player.pause(); else player.play();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                seek(-10000);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seek(10000);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                playEpisode(current - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                playEpisode(current + 1);
                return true;
            case KeyEvent.KEYCODE_MENU:
                retryUsed = true;
                resolveAndStart(current, true, Math.max(0, player.getCurrentPosition()));
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                player.play();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                player.pause();
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    @Override protected void onPause() {
        if (player != null) { savePosition(); player.pause(); }
        super.onPause();
    }

    @Override protected void onDestroy() {
        ++requestId;
        worker.shutdownNow();
        if (player != null) {
            savePosition();
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
