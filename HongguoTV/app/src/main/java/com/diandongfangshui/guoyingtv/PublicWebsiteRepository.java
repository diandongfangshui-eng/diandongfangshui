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
            if (c.getResponseCode() != 200) throw new IOException("官网 HTTP " + c.getResponseCode());
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
        target.put("series_episode_count",source.optInt("episode_cnt"));
        target.put("series_title",source.optString("series_title",source.optString("series_name")));
        target.put("series_cover",source.optString("series_cover"));
        StringBuilder info = new StringBuilder(source.optString("series_intro"));
        info.append("\n官网总集数：").append(source.optInt("episode_cnt"));
        if(source.has("accessible_episode_cnt")) info.append("；网页可访问：").append(source.optInt("accessible_episode_cnt")).append("集");
        JSONArray actors=source.optJSONArray("celebrities");
        if(actors!=null) for(int i=0;i<Math.min(actors.length(),8);i++) info.append("\n").append(actors.getJSONObject(i).optString("nickname")).append(" ").append(actors.getJSONObject(i).optString("sub_title"));
        info.append("\n播放权限由官方网页决定；未获得原生媒体播放授权。");
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
        throw new IOException("官网全站搜索接口尚未验证；可在已加载的官网剧目中筛选标题，或打开官方网页搜索");
    }
    public Drama detail(String id) throws Exception {
        if(!id.matches("[0-9]+"))throw new IllegalArgumentException("剧集ID无效");
        return adapt(request("/detail?series_id="+id).getJSONObject("seriesDetail"));
    }
    public ContentServices.Playback resolve(String id,int episode,boolean refresh) throws Exception {
        throw new IOException("官方播放器标记 protectedPlayback=true，公开SSR未提供媒体地址；需官方网页播放或正式授权SDK");
    }
    public String status(){return "官网公开访客；官方 App 账号授权尚未接入";}
    public boolean canSynchronize(){return false;}
}
