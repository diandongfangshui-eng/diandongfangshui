package com.diandongfangshui.guoyingtv;

import android.app.Activity;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;
import com.pandora.common.env.Env;
import com.pandora.common.env.config.Config;
import com.pandora.ttlicense2.LicenseManager;
import com.pandora.ttlicense2.C;
import com.ss.ttvideoengine.TTVideoEngine;
import com.ss.ttvideoengine.TTVideoEngineInterface;
import com.ss.ttvideoengine.model.VideoModel;
import com.ss.ttvideoengine.VideoEngineCallback;
import com.ss.ttvideoengine.source.DirectUrlSource;

/** Official SDK integration. Own package license required; credentials stay in process memory. */
public final class AuthorizedSdkPlayerActivity extends Activity implements SurfaceHolder.Callback {
    private TextView status;
    private TTVideoEngine engine;
    private SurfaceView surface;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean destroyed;
    private boolean loading;
    private boolean envInitialized;
    private boolean diagnosticLoading;
    private int licenseStatus;
    private PublicWebsiteRepository repository;
    private final android.os.Handler clock = new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean resumeAfterPause;
    private int generation;
    private int speedIndex;
    private boolean completedEpisode;
    private boolean forceRefresh;
    private final float[] speeds = {1f,1.25f,1.5f,2f};
    private final Runnable progressTick = new Runnable(){public void run(){saveProgress();if(!destroyed)clock.postDelayed(this,5000);}};
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        repository=WebsiteSession.repository(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(0xff10151e);
        status=new TextView(this);status.setTextColor(0xffffffff);status.setTextSize(20);status.setPadding(32,20,32,20);page.addView(status);
        Button back=new Button(this);back.setText("返回");back.setOnClickListener(v->finish());page.addView(back);
        Button retry=new Button(this);retry.setText("重新请求播放");retry.setOnClickListener(v->{
            if(licenseStatus!=C.LicenseStatus.LICENSE_STATUS_OK){show("需要本应用的有效SDK License才能开始或重试播放");return;}
            saveProgress();generation++;if(engine!=null){engine.releaseAsync();engine=null;}loading=false;forceRefresh=true;load();
        });page.addView(retry);
        Button inspect=new Button(this);inspect.setText("检查真实播放模型（不播放）");inspect.setOnClickListener(v->inspectModel());page.addView(inspect);
        surface=new SurfaceView(this);page.addView(surface,new LinearLayout.LayoutParams(-1,0,1));setContentView(page);
        surface.getHolder().addCallback(this);
        checkLicense();
        clock.postDelayed(progressTick,5000);
    }
    private void show(String message){if(!destroyed)runOnUiThread(()->{if(!destroyed)status.setText(message);});}
    private void checkLicense() {
        try {
            // Diagnostic remains available even without an AppID or license file.
            LicenseManager.init(getApplicationContext());
            licenseStatus=LicenseManager.getInstance().checkSDKAuth(C.SDK.SDK_VOD_PLAY);
            if(licenseStatus==C.LicenseStatus.LICENSE_NATIVE_LOADED_FAIL) {
                show("官方SDK状态=3：本地ttlicense2库加载失败；尚未进行License真实性校验。请检查设备ABI和native依赖，不能据此判断License无效。仍可检查真实模型而不播放。");return;
            }
            String appId=getSharedPreferences("authorized_sdk",MODE_PRIVATE).getString("app_id",BuildConfig.VOD_APP_ID);
            if(appId.isEmpty()) {show("官方SDK License状态="+licenseStatus+"（0=未配置/非法，1=有效）\n尚未配置果影TV自己的AppID与assets/license/vod.lic；未请求播放或复用原版身份。");return;}
            if(!envInitialized) {
            envInitialized=true;
            Env.init(new Config.Builder().setApplicationContext(getApplicationContext()).setAppID(appId)
                .setAppName("GuoyingTV").setAutoStartAppLog(false)
                .setLicenseUri("assets:///license/vod.lic")
                .setLicenseCallback(new LicenseManager.Callback(){
                    public void onLicenseLoadSuccess(String a,String b){runOnUiThread(()->checkReady());}
                    public void onLicenseLoadError(String a,Exception e,boolean retry){show("官方License加载失败："+e.getClass().getSimpleName()+"，可重试="+retry);}
                    public void onLicenseLoadRetry(String a){show("官方License正在重试加载");}
                    public void onLicenseUpdateSuccess(String a,String b){runOnUiThread(()->checkReady());}
                    public void onLicenseUpdateError(String a,Exception e,boolean retry){show("官方License更新失败："+e.getClass().getSimpleName()+"，可重试="+retry);}
                    public void onLicenseUpdateRetry(String a){show("官方License正在重试更新");}
                }).build());
            }
            checkReady();
        } catch(Throwable error) {show("官方SDK初始化失败："+error.getClass().getSimpleName()+"；未获得授权播放。");}
    }
    private void checkReady() {
        if(destroyed)return;
        try {
            licenseStatus=LicenseManager.getInstance().checkSDKAuth(C.SDK.SDK_VOD_PLAY);
            if(licenseStatus==C.LicenseStatus.LICENSE_NATIVE_LOADED_FAIL){show("官方SDK状态=3：本地ttlicense2库加载失败；未进行License真实性校验。");return;}
            if(licenseStatus!=C.LicenseStatus.LICENSE_STATUS_OK){show("官方SDK实际License状态="+licenseStatus+"；尚未完成授权，加载回调成功后会自动重查。可检查真实模型而不播放。");return;}
            show("SDK License有效，等待播放Surface");
            if(surface.getHolder().getSurface().isValid())load();
        }catch(Throwable e){show("SDK授权检查失败："+e.getClass().getSimpleName());}
    }
    private void inspectModel() {
        if(diagnosticLoading||destroyed)return;
        String series=getIntent().getStringExtra("series_id"),video=getIntent().getStringExtra("video_id");
        if(series==null||video==null){show("缺少真实剧集/分集ID");return;}
        diagnosticLoading=true;show("正在正常请求并解析真实播放模型（不播放）");
        worker.execute(()->{
            try {
                ContentServices.Playback p=repository.resolveVideo(series,video,true);
                VideoModel model=new VideoModel();model.initWithJson(OfficialVideoModelAdapter.map(p.rawPlayInfo));model.setUpResolution(new java.util.HashMap<>());
                java.util.List<com.ss.ttvideoengine.model.VideoInfo> v=model.videoRef.mDynamicVideoList;
                java.util.List<com.ss.ttvideoengine.model.VideoInfo> a=model.videoRef.mOriginalAudioInfo;
                int vc=0,ac=0;boolean ve=false,ae=false;
                if(v!=null)for(com.ss.ttvideoengine.model.VideoInfo track:v){
                    if(track.getMediatype()==com.ss.ttvideoengine.model.VideoRef.TYPE_VIDEO){vc++;ve|=track.mEncrypt;}
                    if(track.getMediatype()==com.ss.ttvideoengine.model.VideoRef.TYPE_AUDIO){ac++;ae|=track.mEncrypt;}
                }
                show("官方SDK实际模型解析：视频轨="+vc+"，音频轨="+ac+"\n视频加密="+ve+"，音频加密="+ae+"\n模型类型DASH="+model.isDashSource()+"；未调用播放器、未跳过License。");
            }catch(Throwable e){show("官方SDK模型解析失败："+e.getClass().getSimpleName()+"；未播放。");}
            finally {runOnUiThread(()->diagnosticLoading=false);}
        });
    }
    @Override protected void onResume(){super.onResume();if(envInitialized)checkReady();if(engine!=null&&resumeAfterPause)engine.play();}
    private void load(){
        if(engine!=null||loading||licenseStatus!=C.LicenseStatus.LICENSE_STATUS_OK||destroyed)return;
        String series=getIntent().getStringExtra("series_id"),video=getIntent().getStringExtra("video_id");
        if(series==null||video==null){show("缺少真实剧集/分集ID");return;}
        loading=true;
        final int requestGeneration=++generation;
        final boolean refresh=forceRefresh;forceRefresh=false;completedEpisode=false;
        show("正在正常请求官方播放模型");
        worker.execute(()->{
            try {
                ContentServices.Playback p=repository.resolveVideo(series,video,refresh);
                runOnUiThread(()->{
                    if(destroyed||requestGeneration!=generation)return;
                    try {
                        engine=new TTVideoEngine(this);engine.setSurfaceHolder(surface.getHolder());
                        engine.setIntOption(TTVideoEngineInterface.PLAYER_OPTION_IMAGE_LAYOUT,TTVideoEngineInterface.IMAGE_LAYOUT_ASPECT_FIT);
                        engine.setVideoEngineCallback(new VideoEngineCallback(){
                            @Override public void onRenderStart(TTVideoEngine e){if(e!=engine)return;markRecent(series);show("已渲染首帧 · 左右快进退 · 上下切集 · 确定暂停 · 菜单倍速");}
                            @Override public void onPrepared(TTVideoEngine e){
                                if(e!=engine)return;
                                long last=CatalogStore.prefs(AuthorizedSdkPlayerActivity.this).getLong(progressKey(),0);
                                int resume=(int)Math.min(Integer.MAX_VALUE,PlaybackPolicy.resume(last,e.getDuration()));
                                if(resume>0)e.seekTo(resume,null);
                                e.setPlaybackParams(new com.ss.ttm.player.PlaybackParams().allowDefaults().setSpeed(speeds[speedIndex]));
                            }
                            @Override public void onCompletion(TTVideoEngine e){if(e!=engine)return;completedEpisode=true;CatalogStore.prefs(AuthorizedSdkPlayerActivity.this).edit().remove(progressKey()).apply();changeEpisode(1,true);}
                            @Override public void onError(com.ss.ttvideoengine.utils.Error e){if(requestGeneration!=generation)return;show("官方SDK播放错误：code="+e.code+" internal="+e.internalCode);}
                        });
                        if(!p.audioUrl.isEmpty()) {
                            VideoModel model=new VideoModel();
                            model.initWithJson(OfficialVideoModelAdapter.map(p.rawPlayInfo));model.setUpResolution(new java.util.HashMap<>());
                            engine.setIntOption(TTVideoEngineInterface.PLAYER_OPTION_ENABLE_DASH,1);
                            engine.setDecryptionKey(p.playAuth);
                            engine.setVideoModel(model);
                        } else {
                            DirectUrlSource source=new DirectUrlSource.Builder().setVid(video)
                                .addItem(new DirectUrlSource.UrlItem.Builder().setUrl(p.url).setFormat(p.format)
                                    .setCacheKey(TTVideoEngine.computeMD5(p.url)).setPlayAuth(p.playAuth).build()).build();
                            engine.setStrategySource(source);
                        }
                        engine.play();
                    }catch(Throwable e){show("官方SDK设置播放源失败："+e.getClass().getSimpleName());}
                });
            }catch(Exception e){show("播放模型请求失败："+e.getMessage());}
            finally {runOnUiThread(()->loading=false);}
        });
    }
    private void markRecent(String series){
        java.util.LinkedHashSet<String> ids=new java.util.LinkedHashSet<>();ids.add(series);
        for(String id:CatalogStore.prefs(this).getString("recent_ids","").split(","))if(!id.isEmpty()&&ids.size()<30)ids.add(id);
        CatalogStore.prefs(this).edit().putString("recent_ids",android.text.TextUtils.join(",",ids)).apply();
    }
    private String progressKey(){return "pos_"+getIntent().getStringExtra("series_id")+"_"+getIntent().getIntExtra("episode_index",0);}
    private void saveProgress(){
        if(engine==null||completedEpisode)return;
        String series=getIntent().getStringExtra("series_id");
        CatalogStore.prefs(this).edit().putLong(progressKey(),Math.max(0,engine.getCurrentPlaybackTime()))
            .putInt("last_"+series,getIntent().getIntExtra("episode_index",0)).apply();
    }
    private void changeEpisode(int delta,boolean completed){
        if(loading||destroyed)return;
        if(!completed)saveProgress();
        String series=getIntent().getStringExtra("series_id"),current=getIntent().getStringExtra("video_id");
        loading=true;
        worker.execute(()->{
            try{
                Drama detail=repository.detail(series);int found=-1;
                for(int i=0;i<detail.episodes.size();i++)if(detail.episodes.get(i).videoId.equals(current)){found=i;break;}
                final int next=found+delta;
                if(found<0||next<0||next>=detail.episodes.size()){show(completed?"本剧已播完":"没有更多分集");return;}
                runOnUiThread(()->{
                    if(destroyed)return;
                    if(engine!=null){engine.releaseAsync();engine=null;}
                    getIntent().putExtra("video_id",detail.episodes.get(next).videoId).putExtra("episode_index",next);
                    loading=false;load();
                });
            }catch(Exception e){show("选集失败："+e.getMessage());}
            finally {runOnUiThread(()->{if(engine!=null)loading=false;});}
        });
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        if(e.getAction()!=KeyEvent.ACTION_DOWN||engine==null)return super.dispatchKeyEvent(e);
        switch(e.getKeyCode()){
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_DPAD_RIGHT:
                int target=Math.max(0,engine.getCurrentPlaybackTime()+(e.getKeyCode()==KeyEvent.KEYCODE_DPAD_LEFT?-10000:10000));
                if(engine.getDuration()>0)target=Math.min(target,engine.getDuration());engine.seekTo(target,null);return true;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if(engine.isShouldPlay())engine.pause();else engine.play();return true;
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_MEDIA_PREVIOUS: changeEpisode(-1,false);return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: case KeyEvent.KEYCODE_MEDIA_NEXT: changeEpisode(1,false);return true;
            case KeyEvent.KEYCODE_MENU:
                speedIndex=(speedIndex+1)%speeds.length;engine.setPlaybackParams(new com.ss.ttm.player.PlaybackParams().allowDefaults().setSpeed(speeds[speedIndex]));show("倍速 "+speeds[speedIndex]+"x");return true;
            default:return super.dispatchKeyEvent(e);
        }
    }
    @Override public void surfaceCreated(SurfaceHolder h){if(engine!=null)engine.setSurfaceHolder(h);else if(licenseStatus==C.LicenseStatus.LICENSE_STATUS_OK)load();}
    @Override public void surfaceChanged(SurfaceHolder h,int f,int w,int height){}
    @Override public void surfaceDestroyed(SurfaceHolder h){if(engine!=null)engine.pause();}
    @Override protected void onPause(){super.onPause();saveProgress();resumeAfterPause=engine!=null&&engine.isShouldPlay();if(engine!=null)engine.pause();}
    @Override protected void onDestroy(){saveProgress();destroyed=true;generation++;clock.removeCallbacksAndMessages(null);worker.shutdownNow();if(engine!=null){engine.releaseAsync();engine=null;}super.onDestroy();}
}
