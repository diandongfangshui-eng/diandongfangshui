package com.diandongfangshui.guoyingtv;
import org.json.*;
/** In-memory schema mapping to the downloaded official SDK's version-3 VideoModel. No key transformation. */
public final class OfficialVideoModelAdapter {
 private OfficialVideoModelAdapter(){}
 public static JSONObject map(JSONObject p)throws JSONException {
  if(p==null)throw new JSONException("Missing complete official playback model");
  JSONObject audio=p.getJSONObject("audio");
  JSONObject data=new JSONObject().put("version",3).put("video_id",p.getString("vid"))
   .put("video_duration",p.getDouble("duration")).put("status",10).put("enable_ssl",true)
   .put("media_type","video").put("url_expire",p.optLong("expires_at"));
  JSONObject dynamic=new JSONObject().put("dynamic_type","segment_base")
   .put("dynamic_video_list",new JSONArray().put(track(p,true)))
   .put("dynamic_audio_list",new JSONArray().put(track(audio,false)));
  return data.put("dynamic_video",dynamic);
 }
 private static JSONObject track(JSONObject input,boolean video)throws JSONException {
  JSONObject out=new JSONObject();
  String[] fields={"main_url","init_range","index_range","first_moof_range","codec_type","size","bitrate","kid"};
  for(String key:fields)if(input.has(key))out.put(key,input.get(key));
  if(input.has("backup_url"))out.put("backup_url",input.get("backup_url"));
  out.put("encrypt",true).put("spade_a",input.getString("player_access_key"))
    .put("url_expire",input.optLong("expires_at")).put("format","dash");
  if(video)out.put("vwidth",input.optInt("width")).put("vheight",input.optInt("height")).put("definition",input.optString("definition"));
  // Version 3 reads codec/dimensions from media-specific metadata and encryption/ranges from nested objects.
  JSONObject meta=new JSONObject().put(video?"vtype":"atype", "dash");
  for(String key:new String[]{"codec_type","size","bitrate","definition"})if(input.has(key))meta.put(key,input.get(key));
  if(video)meta.put("vwidth",input.optInt("width")).put("vheight",input.optInt("height"));
  out.put(video?"video_meta":"audio_meta",meta);
  JSONObject encryption=new JSONObject().put("encrypt",true).put("spade_a",input.getString("player_access_key"));
  if(input.has("kid"))encryption.put("kid",input.get("kid"));
  out.put("encrypt_info",encryption);
  JSONObject range=new JSONObject();
  for(String key:new String[]{"init_range","index_range","first_moof_range"})if(input.has(key))range.put(key,input.get(key));
  out.put("base_range_info",range);
  return out;
 }
}
