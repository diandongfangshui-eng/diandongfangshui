package com.diandongfangshui.guoyingtv;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reads public SSR data; does not emulate App authentication or bypass protectedPlayback. */
public final class PublicWebsiteRepository implements ContentServices.CatalogRepository,
        ContentServices.SearchRepository, ContentServices.SeriesRepository,
        ContentServices.PlaybackResolver, ContentServices.AuthenticationProvider {
    public static final String ORIGIN = "https://hongguoduanju.com";
    public interface HttpSession {
        String cookie(); String userAgent();
        default void received(Map<String,List<String>> headers){}
    }
    private final HttpSession session;
    public PublicWebsiteRepository(){this(null);}
    public PublicWebsiteRepository(HttpSession session){this.session=session;}
    private void applySession(HttpURLConnection c) {
        if(session==null)return;
        if(!SessionPolicy.accepts(c.getURL()))throw new SecurityException("禁止将会话发送至其他来源");
        String cookie=SessionPolicy.header(session.cookie()), ua=SessionPolicy.header(session.userAgent());
        if(!cookie.isEmpty())c.setRequestProperty("Cookie",cookie);
        if(!ua.isEmpty())c.setRequestProperty("User-Agent",ua);
    }
    private int responseCode(HttpURLConnection c)throws IOException {
        int code=c.getResponseCode();
        if(session!=null&&SessionPolicy.accepts(c.getURL()))session.received(c.getHeaderFields());
        return code;
    }
    public JSONObject accountProfile()throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(ORIGIN+"/api/pc/profile").openConnection();
        c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setInstanceFollowRedirects(false);
        try {
            applySession(c);int code=responseCode(c);if(code!=200)throw new IOException("HTTP "+code);
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream in=c.getInputStream()){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>262144)throw new IOException("账号响应过大");out.write(buffer,0,n);}}
            JSONObject response=new JSONObject(out.toString("UTF-8"));
            if(!response.has("profile"))throw new IOException("官网账号响应结构尚未识别");
            return response;
        }finally{c.disconnect();}
    }
    public void clearPlaybackCache(){synchronized(playbackCache){playbackCache.clear();}}

    public static JSONObject parsePage(String html) throws Exception {
        String marker = "_ROUTER_DATA = ";
        int start = html.indexOf(marker);
        if (start < 0) throw new IOException("官网未返回公开页面数据，可能需要浏览器验证");
        JSONObject root = new JSONObject(new JSONTokener(html.substring(start + marker.length())));
        JSONObject loader = root.getJSONObject("loaderData");
        Iterator<String> keys = loader.keys();
        while (keys.hasNext()) {
            JSONObject value = loader.optJSONObject(keys.next());
            if (value != null) return value;
        }
        throw new IOException("官网页面数据为空");
    }
    private JSONObject request(String path) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(ORIGIN + path).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "GuoyingTV/0.5 PublicWebsiteReader");
        c.setInstanceFollowRedirects(false);
        try {
            applySession(c);int status=responseCode(c);
            if (status != 200) throw new IOException("官网 HTTP " + status);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try(InputStream in = c.getInputStream()) {
                byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) != -1) {
                    if (out.size() + n > 4*1024*1024) throw new IOException("官网响应超过4MB");
                    out.write(b,0,n);
                }
            }
            return parsePage(out.toString(StandardCharsets.UTF_8.name()));
        } finally { c.disconnect(); }
    }
    public static Drama adapt(JSONObject source) throws Exception {
        JSONObject target = new JSONObject();
        target.put("series_id",source.getString("series_id"));
        target.put("episode_cnt",source.optInt("episode_cnt"));
        target.put("accessible_episode_cnt",source.optInt("accessible_episode_cnt",0));
        target.put("series_episode_count",source.optInt("episode_cnt"));
        target.put("series_title",source.optString("series_title",source.optString("series_name")));
        target.put("series_cover",source.optString("series_cover"));
        StringBuilder info = new StringBuilder(source.optString("series_intro"));
        info.append("\n官网总集数：").append(source.optInt("episode_cnt"));
        if(source.has("accessible_episode_cnt")) info.append("；网页可访问：").append(source.optInt("accessible_episode_cnt")).append("集");
        JSONArray actors=source.optJSONArray("celebrities");
        if(actors!=null) for(int i=0;i<Math.min(actors.length(),8);i++) info.append("\n").append(actors.getJSONObject(i).optString("nickname")).append(" ").append(actors.getJSONObject(i).optString("sub_title"));
        info.append("\n目录不代表全部分集可播；公开范围以服务返回为准。");
        target.put("description",info.toString());
        JSONArray cats=source.optJSONArray("category_list"),tags=source.optJSONArray("tags");
        target.put("category",cats!=null && cats.length()>0?cats.getJSONObject(0).optString("name","官网"):tags!=null && tags.length()>0?tags.optString(0):"官网");
        JSONArray ids=source.optJSONArray("vid_list"),eps=new JSONArray();
        // A list card has no episode IDs; the explicit placeholder is replaced by detail before selection.
        if(ids!=null && ids.length()>0) {
            for(int i=0;i<ids.length();i++) eps.put(new JSONObject().put("episode_index",i+1).put("video_id",ids.getString(i)).put("title","第 "+(i+1)+" 集 · 官网权限").put("playback_url",""));
        } else eps.put(new JSONObject().put("episode_index",1).put("video_id","detail-required:"+source.getString("series_id")).put("title","加载官方分集目录").put("playback_url",""));
        target.put("episodes",eps);
        return Drama.fromJson(target,2);
    }
    private List<Drama> list(JSONArray values) throws Exception {
        LinkedHashMap<String,Drama> rows=new LinkedHashMap<>();
        if(values!=null) for(int i=0;i<values.length();i++) { Drama d=adapt(values.getJSONObject(i));rows.put(d.id,d); }
        return new ArrayList<>(rows.values());
    }
    public List<Drama> home(int page) throws Exception {
        if(page>1) return Collections.emptyList();
        JSONArray sections=request("/").getJSONArray("homeSections"),all=new JSONArray();
        for(int i=0;i<sections.length();i++) {
            JSONArray rows=sections.getJSONObject(i).optJSONArray("video_list");
            if(rows!=null)for(int j=0;j<rows.length();j++)all.put(rows.getJSONObject(j));
        }
        return list(all);
    }
    /** Homepage's actual ranked sections, rather than claiming the empty rank SSR response is a full chart. */
    public List<Drama> ranking(int page) throws Exception {
        if(page>1)return Collections.emptyList();
        JSONArray sections=request("/").getJSONArray("homeSections");
        for(int i=0;i<sections.length();i++) {
            JSONObject section=sections.getJSONObject(i);
            if("all".equals(section.optString("tab_type")))return list(section.optJSONArray("video_list"));
        }
        throw new IOException("官网首页热播榜字段已变化");
    }
    public List<Drama> category(String route,int page) throws Exception {
        if(!route.matches("[a-z-]+")) throw new IllegalArgumentException("分类参数无效");
        return list(request("/category/"+route+(page<=1?"":"?page="+page)).optJSONArray("recommendList"));
    }
    public List<String> categories() throws Exception {
        JSONArray selectors=request("/category/real-drama").getJSONArray("selectorList");
        List<String> names=new ArrayList<>();
        for(int i=0;i<selectors.length();i++) {
            JSONArray items=selectors.getJSONObject(i).getJSONArray("items");
            for(int j=0;j<items.length();j++)names.add(items.getJSONObject(j).getString("show_name"));
        }
        return names;
    }
    public List<Drama> search(String keyword,int page) throws Exception {
        if(page>1)return Collections.emptyList();
        if(keyword==null || keyword.trim().isEmpty())return Collections.emptyList();
        HttpURLConnection c=(HttpURLConnection)new URL(ORIGIN+"/api/search/suggestion?query="+URLEncoder.encode(keyword.trim(),"UTF-8")).openConnection();
        c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setInstanceFollowRedirects(false);
        try {
            applySession(c);int status=responseCode(c);
            if(status!=200)throw new IOException("官网关键词建议 HTTP "+status+"；完整搜索可能需要官方验证");
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream in=c.getInputStream()) {
                byte[] b=new byte[8192];int n;
                while((n=in.read(b))!=-1) {if(out.size()+n>2*1024*1024)throw new IOException("建议响应过大");out.write(b,0,n);}
            }
            return parseSuggestions(out.toString("UTF-8"));
        } finally {c.disconnect();}
    }
    /** Keyword suggestions with actual series metadata; not the gated full search result endpoint. */
    public static List<Drama> parseSuggestions(String json) throws Exception {
        JSONArray rows=new JSONObject(json).getJSONArray("suggestionList");
        LinkedHashMap<String,Drama> result=new LinkedHashMap<>();
        for(int i=0;i<rows.length();i++) {
            JSONObject data=rows.getJSONObject(i).optJSONObject("video_data");
            if(data==null||!data.has("series_id")||!data.has("series_title"))continue;
            JSONObject card=new JSONObject(data.toString());
            // Suggestions only contain accessible preview IDs; detail must load the full vid_list.
            card.put("vid_list",new JSONArray());
            Drama d=adapt(card);result.put(d.id,d);
        }
        return new ArrayList<>(result.values());
    }
    public Drama detail(String id) throws Exception {
        if(!id.matches("[0-9]+"))throw new IllegalArgumentException("剧集ID无效");
        return adapt(request("/detail?series_id="+id).getJSONObject("seriesDetail"));
    }
    private final Map<String,ContentServices.Playback> playbackCache = new HashMap<>();
    public ContentServices.Playback resolve(String id,int episode,boolean refresh) throws Exception {
        Drama series = detail(id);
        if (episode < 0 || episode >= series.episodes.size()) throw new IllegalArgumentException("分集序号超出目录");
        return resolveVideo(id, series.episodes.get(episode).videoId, refresh);
    }
    /** Normal public fallback used by the official site's own public player. Respect declared episode scope. */
    public ContentServices.Playback resolvePublic(String seriesId,int episode,boolean refresh)throws Exception {
        Drama detail=detail(seriesId);
        int accessible=detail.toJson().optInt("accessible_episode_cnt",0);
        if(episode<0||episode>=detail.episodes.size())throw new IllegalArgumentException("分集序号无效");
        if(episode>=accessible)throw new IOException("本集不在官网声明的公开播放范围内（公开 "+accessible+" 集）；需平台提供对应授权");
        String videoId=detail.episodes.get(episode).videoId,cacheId="public:"+seriesId+":"+videoId;
        synchronized(playbackCache){ContentServices.Playback cached=playbackCache.get(cacheId);if(!refresh&&cached!=null&&cached.usable(System.currentTimeMillis())){cached.publicEpisodeCount=accessible;return cached;}}
        HttpURLConnection c=(HttpURLConnection)new URL(ORIGIN+"/api/video/protected/fallback-play-info").openConnection();
        c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setInstanceFollowRedirects(false);c.setRequestMethod("POST");c.setDoOutput(true);
        c.setRequestProperty("Content-Type","application/json; charset=utf-8");
        byte[] body=new JSONObject().put("item_id",videoId).toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(body.length);
        try{
            applySession(c);try(OutputStream out=c.getOutputStream()){out.write(body);}
            int code=responseCode(c);if(code!=200)throw new IOException("公开播放请求 HTTP "+code);
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream in=c.getInputStream()){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){if(out.size()+n>1048576)throw new IOException("公开播放响应过大");out.write(buf,0,n);}}
            ContentServices.Playback p=parsePublicPlayback(new JSONObject(out.toString("UTF-8")),System.currentTimeMillis());p.publicEpisodeCount=accessible;
            synchronized(playbackCache){if(playbackCache.size()>16)playbackCache.clear();playbackCache.put(cacheId,p);}return p;
        }finally{c.disconnect();}
    }
    public static ContentServices.Playback parsePublicPlayback(JSONObject response,long now)throws Exception {
        if(response.getJSONObject("base_resp").getInt("StatusCode")!=0)throw new IOException("平台未提供该分集的公开播放权限");
        long expiry=response.getLong("url_expire");if(expiry<=0||expiry>Long.MAX_VALUE/1000)throw new IOException("公开播放有效期无效");
        ContentServices.Playback p=new ContentServices.Playback(response.getString("main_url"),expiry*1000,"","","mp4","","");
        if(!p.usable(now))throw new IOException("公开播放地址已过期");return p;
    }
    public ContentServices.Playback resolveVideo(String seriesId, String videoId, boolean refresh) throws Exception {
        if (!seriesId.matches("[0-9]+") || !videoId.matches("[0-9]+")) throw new IllegalArgumentException("剧集ID无效");
        String cacheId=seriesId+":"+videoId;
        synchronized(playbackCache) {
            ContentServices.Playback cached=playbackCache.get(cacheId);
            if(!refresh && cached!=null && cached.usable(System.currentTimeMillis()))return cached;
        }
        HttpURLConnection c=(HttpURLConnection)new URL(ORIGIN+"/api/video/protected/play-info").openConnection();
        c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setInstanceFollowRedirects(false);
        c.setRequestMethod("POST");c.setDoOutput(true);
        c.setRequestProperty("Content-Type","application/json; charset=utf-8");
        byte[] body=new JSONObject().put("series_id",seriesId).put("item_id",videoId).put("definition","720p").toString().getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(body.length);
        try {
            applySession(c);
            try(OutputStream out=c.getOutputStream()) {out.write(body);}
            int status=responseCode(c);
            if(status!=200)throw new IOException("播放授权请求 HTTP "+status);
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream in=c.getInputStream()) {
                byte[] buffer=new byte[8192];int n;
                while((n=in.read(buffer))!=-1) {if(out.size()+n>1024*1024)throw new IOException("播放响应过大");out.write(buffer,0,n);}
            }
            ContentServices.Playback result=parsePlayback(new JSONObject(out.toString("UTF-8")),System.currentTimeMillis());
            synchronized(playbackCache) { if(playbackCache.size()>16)playbackCache.clear();playbackCache.put(cacheId,result); }
            return result;
        } finally {c.disconnect();}
    }
    /** Parses normal service authorization, preserving encrypted media as encrypted. Does not unwrap keys. */
    public static ContentServices.Playback parsePlayback(JSONObject response, long nowMs) throws Exception {
        JSONObject status=response.getJSONObject("base_resp");
        int code=status.getInt("StatusCode");
        if(code!=0)throw new IOException("内容服务未授予播放权限，错误码 "+code);
        JSONObject p=response.getJSONObject("play_info");
        long seconds=p.optLong("expires_at",0);
        if(seconds<=0 || seconds>Long.MAX_VALUE/1000)throw new IOException("播放授权有效期无效");
        String audio="";
        JSONObject a=p.optJSONObject("audio");
        if(a!=null)audio=a.optString("main_url","");
        if(!audio.isEmpty()&&!UrlPolicy.isHttpsUrl(audio))throw new IOException("音频地址无效");
        ContentServices.Playback result=new ContentServices.Playback(p.getString("main_url"),seconds*1000,
                audio,p.optString("backup_url",""),p.optString("format",""),p.optString("definition",""),p.optString("player_access_key",""));
        result.rawPlayInfo = p;
        if(!result.usable(nowMs))throw new IOException("播放授权已过期，请刷新");
        if("dash".equals(result.format) && result.playAuth.isEmpty())throw new IOException("加密DASH缺少播放器授权凭据");
        return result;
    }
    public String status(){return "官网公开访客；官方 App 账号授权尚未接入";}
    public boolean canSynchronize(){return false;}
}
