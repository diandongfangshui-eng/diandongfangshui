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

/** Direct public MP4 playback using the official website's normal public fallback API. */
public final class PublicPlayerActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ExoPlayer player;
    private PublicWebsiteRepository api;
    private boolean renderedFrame;
    private TextView progressText;
    private boolean resumeOnReturn;
    private boolean audioClockStarted;
    private boolean sourceFailed;
    private int progressTicks;
    private final android.os.Handler timer=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable update=new Runnable(){public void run(){if(player!=null){progressText.setText((sourceFailed?"未能播放":player.getPlaybackState()==Player.STATE_ENDED?"已播完":player.isPlaying()?"播放中":renderedFrame?"已暂停":"正在加载")+" · "+clockTime(player.getCurrentPosition())+" / "+clockTime(player.getDuration())+" · "+speeds[speed]+"x");if(++progressTicks%5==0)savePosition();timer.postDelayed(this,1000);}}};
    private TextView titleText, helpText;
    private String seriesId, seriesTitle;
    private String[] ids, titles;
    private int current, requestedEpisode, requestId = 0;
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
                || ids.length > 2000 || seriesId == null || seriesId.isEmpty()) {
            Toast.makeText(this, "片库数据无效", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        current = Math.max(0, Math.min(ids.length - 1, getIntent().getIntExtra("episode", 0)));
        try {
            api = WebsiteSession.repository(this);
        } catch (Exception e) {
            Toast.makeText(this, "公开内容服务初始化失败", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        build();
        playEpisode(current);
        timer.postDelayed(update,1000);
    }

    private String clockTime(long ms){long sec=Math.max(0,ms)/1000;return String.format(java.util.Locale.ROOT,"%02d:%02d",sec/60,sec%60);}
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
        progressText=new TextView(this);progressText.setTextColor(Color.WHITE);progressText.setTextSize(12);overlay.addView(progressText);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        lp.leftMargin = dp(35);
        lp.rightMargin = dp(35);
        lp.bottomMargin = dp(25);
        root.addView(overlay, lp);
        player.addAnalyticsListener(new androidx.media3.exoplayer.analytics.AnalyticsListener(){
            @Override public void onAudioPositionAdvancing(androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime time,long start){audioClockStarted=true;}
        });
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (!switching && state == Player.STATE_ENDED) {
                    int completed = current;
                    CatalogStore.prefs(PublicPlayerActivity.this).edit()
                            .remove(positionKey(completed)).apply();
                    if (current + 1 < ids.length) playEpisode(current + 1);
                    else helpText.setText("本剧已播完 · 按返回键退出");
                }
            }

            @Override public void onRenderedFirstFrame(){renderedFrame=true;recordRecent();}
            @Override public void onPlayerError(PlaybackException error) {
                if(switching)return;
                int status=0;
                for(Throwable cause=error;cause!=null;cause=cause.getCause())if(cause instanceof androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)status=((androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)cause).responseCode;
                if (!retryUsed && PlaybackPolicy.retry(0,status)) {
                    retryUsed = true;
                    long resume = Math.max(0, player.getCurrentPosition());
                    helpText.setText("播放异常，重新解析媒体地址...");
                    resolveAndStart(current, true, resume);
                } else {
                    helpText.setText("播放失败：" + error.getErrorCodeName()
                            + "。按菜单键重新获取公开地址。");
                }
            }
        });
        video.requestFocus();
    }

    private String positionKey(int index) {
        return "pos_" + seriesId + "_" + index;
    }

    private void savePosition() {
        if (player == null || ids == null || !renderedFrame || player.getPlaybackState() == Player.STATE_ENDED)
            return;
        CatalogStore.prefs(this).edit()
                .putLong(positionKey(current), Math.max(0L, player.getCurrentPosition()))
                .putInt("last_" + seriesId, current).apply();
    }

    private void playEpisode(int index) {
        if (index < 0 || index >= ids.length || player == null) return;
        switching = true;
        if (player.getMediaItemCount() != 0 && player.getPlaybackState() != Player.STATE_ENDED) {
            savePosition();
        }
        player.stop();player.clearMediaItems();
        requestedEpisode=index;
        renderedFrame=false;audioClockStarted=false;
        retryUsed = false;

        titleText.setText(seriesTitle + " · " + titles[index]);
        helpText.setText("加载播放源...  左/右快退快进 10 秒 · 上/下切集 · 确定暂停 · 菜单重试");
        long last = CatalogStore.prefs(this).getLong(positionKey(index), 0L);
        resolveAndStart(index, false, last);
    }

    private void resolveAndStart(int episodeIndex, boolean fresh, long position) {
        switching = true;
        sourceFailed = false;
        int generation = ++requestId;
        worker.execute(() -> {
            try {
                ContentServices.Playback resolved=api.resolvePublic(seriesId,episodeIndex,fresh);
                String path=resolved.url;
                runOnUiThread(() -> {
                    if (isFinishing() || generation != requestId || player == null) return;
                    try {
                        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                                .setUserAgent("GuoyingTV/Public-0.7")
                                .setConnectTimeoutMs(8000).setReadTimeoutMs(15000)
                                .setAllowCrossProtocolRedirects(false);
                        ProgressiveMediaSource media = new ProgressiveMediaSource.Factory(http)
                                .createMediaSource(MediaItem.fromUri(path));
                        current=episodeIndex;
                        CatalogStore.prefs(this).edit().putInt("last_"+seriesId,current).apply();
                        player.setMediaSource(media);
                        player.prepare();
                        if (position > 0) player.seekTo(PlaybackPolicy.resume(position,player.getDuration()));
                        player.setPlaybackSpeed(speeds[speed]);
                        player.play();
                        switching = false;
                        helpText.setText("左右 10 秒 · 上下切集 · 确定暂停 · 菜单倍速 · 长按重取");
                    } catch (Exception error) {
                        switching = false;
                        helpText.setText("播放初始化失败：" + error.getMessage());
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (generation != requestId || isFinishing()) return;
                    switching = false;
                    sourceFailed = true;
                    helpText.setText("媒体地址获取失败：" + e.getMessage()
                            + " · 确定重试 · 上下切集");
                });
            }
        });
    }

    private void seek(long by) {
        if (player == null || player.getMediaItemCount()==0) return;
        long target = Math.max(0, player.getCurrentPosition() + by);
        if (player.getDuration() > 0) target = Math.min(target, player.getDuration());
        player.seekTo(target);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (switching && event.getKeyCode()!=KeyEvent.KEYCODE_BACK) return true;
        if (player == null || event.getAction() != KeyEvent.ACTION_DOWN)
            return super.dispatchKeyEvent(event);
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if(player.getMediaItemCount()==0){resolveAndStart(requestedEpisode,true,0);return true;}
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
                playEpisode(requestedEpisode - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                playEpisode(requestedEpisode + 1);
                return true;
            case KeyEvent.KEYCODE_MENU:
                if(event.getRepeatCount()==0){speed=(speed+1)%speeds.length;player.setPlaybackSpeed(speeds[speed]);helpText.setText("倍速 "+speeds[speed]+"x");}
                else if(event.getRepeatCount()==1){retryUsed=true;resolveAndStart(requestedEpisode,true,player.getMediaItemCount()>0&&current==requestedEpisode?Math.max(0,player.getCurrentPosition()):0);}
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
        timer.removeCallbacks(update);
        if (player != null) { resumeOnReturn=player.isPlaying();savePosition(); player.pause(); }
        super.onPause();
    }

    private void recordRecent(){java.util.LinkedHashSet<String> recent=new java.util.LinkedHashSet<>();recent.add(seriesId);for(String id:CatalogStore.prefs(this).getString("recent_ids","").split(","))if(!id.isEmpty()&&recent.size()<30)recent.add(id);CatalogStore.prefs(this).edit().putString("recent_ids",android.text.TextUtils.join(",",recent)).apply();}
    @Override protected void onResume(){super.onResume();timer.removeCallbacks(update);timer.postDelayed(update,1000);if(player!=null&&resumeOnReturn)player.play();}
    @Override protected void onDestroy() {
        timer.removeCallbacksAndMessages(null);
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
