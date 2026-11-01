package com.diandongfangshui.guoyingtv;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class PublicPlaybackTest {
 @Test public void normalPublicResponseIsPlainMediaWithExpiry()throws Exception{ContentServices.Playback p=PublicWebsiteRepository.parsePublicPlayback(new JSONObject().put("base_resp",new JSONObject().put("StatusCode",0)).put("main_url","https://cdn.example/public.mp4").put("url_expire",2000),1000);assertFalse(p.requiresAuthorizedDecoder());assertEquals(2000000,p.expiresAtMs);}
 @Test(expected=java.io.IOException.class) public void denialDoesNotBecomeMedia()throws Exception{PublicWebsiteRepository.parsePublicPlayback(new JSONObject().put("base_resp",new JSONObject().put("StatusCode",403)),1000);}
 @Test(expected=java.io.IOException.class) public void expiredResponseFails()throws Exception{PublicWebsiteRepository.parsePublicPlayback(new JSONObject().put("base_resp",new JSONObject().put("StatusCode",0)).put("main_url","https://cdn.example/public.mp4").put("url_expire",1),2000);}
}
