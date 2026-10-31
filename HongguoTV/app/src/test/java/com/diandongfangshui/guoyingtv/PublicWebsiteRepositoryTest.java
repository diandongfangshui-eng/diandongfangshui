package com.diandongfangshui.guoyingtv;
import org.junit.Test;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;
public class PublicWebsiteRepositoryTest {
 private String fixture(String name) throws Exception {
  try(InputStream in=getClass().getResourceAsStream("/public-"+name+".html")) {
   ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;
   while((n=in.read(b))!=-1)out.write(b,0,n);
   return out.toString(StandardCharsets.UTF_8.name());
  }
 }
 @Test public void actualHomeResponseMapsTitlesAndIds() throws Exception {
  JSONObject page=PublicWebsiteRepository.parsePage(fixture("home"));
  JSONArray sections=page.getJSONArray("homeSections");assertTrue(sections.length()>0);
  JSONObject source=sections.getJSONObject(0).getJSONArray("video_list").getJSONObject(0);
  Drama d=PublicWebsiteRepository.adapt(source);
  assertEquals(source.getString("series_id"),d.id);
  assertEquals(source.getString("series_title"),d.title);
  assertFalse(d.hasPlayableEpisodes());
 }
 @Test public void actualDetailKeepsAll46EpisodeIdsWithoutInventingMedia() throws Exception {
  JSONObject source=PublicWebsiteRepository.parsePage(fixture("detail")).getJSONObject("seriesDetail");
  Drama d=PublicWebsiteRepository.adapt(source);
  assertEquals(46,d.episodes.size());assertEquals("7690963410778868761",d.episodes.get(0).videoId);
  assertEquals("7690963495944211481",d.episodes.get(45).videoId);assertFalse(d.hasPlayableEpisodes());
  assertEquals(3,source.getInt("accessible_episode_cnt"));
 }
 @Test public void changedOrBlockedHtmlFailsClearly() throws Exception {
  try{PublicWebsiteRepository.parsePage("<html>blocked</html>");fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("公开页面"));}
 }
 @Test public void protectedPlaybackIsExplicit() throws Exception {
  try{new PublicWebsiteRepository().resolve("7690961604636052505",0,true);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("protectedPlayback"));}
 }
}
