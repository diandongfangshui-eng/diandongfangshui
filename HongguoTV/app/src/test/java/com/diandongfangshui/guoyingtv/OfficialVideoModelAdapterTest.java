package com.diandongfangshui.guoyingtv;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;
public class OfficialVideoModelAdapterTest {
 @Test public void preservesSeparateTracksAndOpaqueCredential() throws Exception {
  JSONObject audio=new JSONObject().put("main_url","https://cdn.example/audio").put("player_access_key","opaque-test-token").put("init_range","0-20").put("index_range","21-30").put("codec_type","aac");
  JSONObject p=new JSONObject().put("vid","test-vid").put("duration",10.5).put("width",720).put("height",1280).put("main_url","https://cdn.example/video").put("player_access_key","opaque-test-token").put("init_range","0-40").put("index_range","41-50").put("codec_type","h264").put("definition","720p").put("audio",audio);
  JSONObject model=OfficialVideoModelAdapter.map(p),dyn=model.getJSONObject("dynamic_video");
  JSONObject video=dyn.getJSONArray("dynamic_video_list").getJSONObject(0),a=dyn.getJSONArray("dynamic_audio_list").getJSONObject(0);
  assertEquals("dash",video.getJSONObject("video_meta").getString("vtype"));assertEquals("dash",a.getJSONObject("audio_meta").getString("atype"));
  assertEquals(3,model.getInt("version"));assertEquals("https://cdn.example/video",video.getString("main_url"));
  assertEquals("https://cdn.example/audio",a.getString("main_url"));assertEquals("opaque-test-token",video.getString("spade_a"));assertEquals("opaque-test-token",a.getString("spade_a"));
  assertEquals("0-40",video.getString("init_range"));assertEquals(720,video.getInt("vwidth"));assertTrue(video.getBoolean("encrypt"));
  assertTrue(video.getJSONObject("encrypt_info").getBoolean("encrypt"));assertEquals("opaque-test-token",a.getJSONObject("encrypt_info").getString("spade_a"));
  assertEquals(720,video.getJSONObject("video_meta").getInt("vwidth"));assertEquals("0-40",video.getJSONObject("base_range_info").getString("init_range"));
  assertFalse(p.has("version")); // Mapping must not mutate the cached service response.
 }
 @Test(expected=JSONException.class) public void rejectsIncompleteSeparatedModel() throws Exception {OfficialVideoModelAdapter.map(new JSONObject().put("vid","x"));}
}
