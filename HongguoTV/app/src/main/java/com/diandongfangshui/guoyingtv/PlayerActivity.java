package com.diandongfangshui.guoyingtv;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
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
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.media3.exoplayer.ExoPlayer;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class PlayerActivity extends Activity {
    private ExoPlayer player;
    private PlayerView video;
    private TextView episodeTitle;
    private TextView playbackHint;
    private Drama drama;
    private int currentEpisode = 0;
    private final List<Float> speeds = new ArrayList<>();
    private int speedIndex = 0;
    private boolean switching = false;
    private int retries = 0;
    private final android.os.Handler recovery = new android.os.Handler(android.os.Looper.getMainLooper());

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        try {
            drama = Drama.fromJson(new JSONObject(getIntent().getStringExtra("drama")));
            currentEpisode = Math.max(0, Math.min(
                    getIntent().getIntExtra("episode", 0), drama.episodes.size() - 1));
        } catch (Exception e) {
            Toast.makeText(this, "剧集数据无效：" + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        speeds.add(1f);
        speeds.add(1.25f);
        speeds.add(1.5f);
        speeds.add(2f);
        buildPlayer();
        playEpisode(currentEpisode);
    }

    private int dp(int count) {
        return Math.round(count * getResources().getDisplayMetrics().density);
    }

    private TextView label(String value, int size, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(Color.WHITE);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private void buildPlayer() {
        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(Color.BLACK);
        setContentView(screen);
        player = new ExoPlayer.Builder(this).build();
        video = new PlayerView(this);
        video.setPlayer(player);
        video.setUseController(false);
        video.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        video.setFocusable(true);
        screen.addView(video, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(dp(22), dp(10), dp(22), dp(15));
        GradientDrawable surface = new GradientDrawable();
        surface.setColor(Color.argb(185, 13, 18, 27));
        surface.setCornerRadius(dp(12));
        overlay.setBackground(surface);
        episodeTitle = label("", 19, true);
        overlay.addView(episodeTitle);
        playbackHint = label("← / → 快退快进 10 秒     ↑ / ↓ 上下集     确定键 暂停 / 继续     菜单键 倍速     返回键 退出", 13, false);
        playbackHint.setTextColor(Color.rgb(201, 208, 219));
        playbackHint.setPadding(0, dp(7), 0, 0);
        overlay.addView(playbackHint);
        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        overlayParams.leftMargin = dp(48);
        overlayParams.rightMargin = dp(48);
        overlayParams.bottomMargin = dp(22);
        screen.addView(overlay, overlayParams);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED && !switching) {
                    clearCompletedPosition();
                    if (currentEpisode + 1 < drama.episodes.size()) {
                        playEpisode(currentEpisode + 1);
                    } else {
                        Toast.makeText(PlayerActivity.this, "本剧已播完", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override public void onPlayerError(PlaybackException error) {
                int status = 0;
                for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                    if (cause instanceof androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)
                        status = ((androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) cause).responseCode;
                }
                if (PlaybackPolicy.retry(retries, status)) {
                    retries++;
                    long resume = player.getCurrentPosition();
                    int failedEpisode = currentEpisode;
                    playbackHint.setText("网络或播放错误，正在重试 " + retries + "/2");
                    recovery.postDelayed(() -> {
                        if (player != null && currentEpisode == failedEpisode) {
                            player.prepare(); player.seekTo(Math.max(0, resume)); player.play();
                        }
                    }, retries * 1500L);
                    return;
                }
                playbackHint.setText("播放失败：" + error.getErrorCodeName()
                        + "。请检查片源是否可公开访问、支持当前设备解码。返回键退出");
            }
        });
        video.requestFocus();
    }

    private String progressKey(int episode) {
        return "pos_" + drama.id + "_" + episode;
    }

    private void savePosition() {
        if (player == null || drama == null
                || player.getPlaybackState() == Player.STATE_ENDED) return;
        long pos = Math.max(0L, player.getCurrentPosition());
        CatalogStore.prefs(this).edit()
                .putLong(progressKey(currentEpisode), pos)
                .putInt("last_" + drama.id, currentEpisode).apply();
    }

    private void clearCompletedPosition() {
        CatalogStore.prefs(this).edit().remove(progressKey(currentEpisode)).apply();
    }

    private void playEpisode(int episode) {
        if (player == null || episode < 0 || episode >= drama.episodes.size()) return;
        if (!drama.episodes.get(episode).isPlayable()) {
            Toast.makeText(this, "这一集尚无可播放的授权媒体地址", Toast.LENGTH_LONG).show();
            return;
        }
        recovery.removeCallbacksAndMessages(null);
        retries = 0;
        switching = true;
        if (player.getMediaItemCount() > 0 && player.getPlaybackState() != Player.STATE_ENDED) savePosition();
        currentEpisode = episode;
        SharedPreferences prefs = CatalogStore.prefs(this);
        String previous = prefs.getString("recent_ids", "");
        List<String> updated = new ArrayList<>();
        updated.add(drama.id);
        for (String id : previous.split(",")) {
            if (!id.isEmpty() && !id.equals(drama.id) && updated.size() < 30) updated.add(id);
        }
        prefs.edit().putString("recent_ids", android.text.TextUtils.join(",", updated))
                .putInt("last_" + drama.id, episode).apply();
        Drama.Episode data = drama.episodes.get(episode);
        episodeTitle.setText(drama.title + "  ·  " + data.title);
        playbackHint.setText("← / → 快退快进 10 秒     ↑ / ↓ 上下集     确定键 暂停 / 继续     菜单键 倍速     返回键 退出");
        player.setMediaItem(MediaItem.fromUri(data.url));
        player.prepare();
        long lastPosition = prefs.getLong(progressKey(episode), 0L);
        if (lastPosition > 0) player.seekTo(PlaybackPolicy.resume(lastPosition, player.getDuration()));
        player.setPlaybackSpeed(speeds.get(speedIndex));
        player.play();
        switching = false;
    }

    private void seekBy(long diff) {
        if (player == null) return;
        long duration = player.getDuration();
        long target = Math.max(0L, player.getCurrentPosition() + diff);
        if (duration > 0) target = Math.min(target, duration);
        player.seekTo(target);
    }

    private void togglePlayback() {
        if (player == null) return;
        if (player.isPlaying()) player.pause();
        else player.play();
    }

    private void switchSpeed() {
        if (player == null) return;
        speedIndex = (speedIndex + 1) % speeds.size();
        float speed = speeds.get(speedIndex);
        player.setPlaybackSpeed(speed);
        Toast.makeText(this, "播放速度：" + speed + "x", Toast.LENGTH_SHORT).show();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN || player == null)
            return super.dispatchKeyEvent(event);
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                seekBy(-10000);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seekBy(10000);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_CHANNEL_UP:
                playEpisode(currentEpisode - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                playEpisode(currentEpisode + 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_SPACE:
                togglePlayback();
                return true;
            case KeyEvent.KEYCODE_MENU:
                switchSpeed();
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    @Override protected void onPause() {
        recovery.removeCallbacksAndMessages(null);
        if (player != null) {
            savePosition();
            player.pause();
        }
        super.onPause();
    }

    @Override protected void onDestroy() {
        recovery.removeCallbacksAndMessages(null);
        if (player != null) {
            savePosition();
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
