package com.diandongfangshui.guoyingtv;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import org.json.JSONObject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/** Uses only this application's own first-party WebView login, never another app's credentials. */
public final class WebsiteSession implements ContentServices.AuthenticationProvider {
 private static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
 private final PublicWebsiteRepository repository;
 private volatile String state="官网登录状态尚未检查";
 public WebsiteSession(Context context){repository=repository(context);}
 public static PublicWebsiteRepository repository(Context context){
  final String ua;
  final CookieManager manager;
  try{ua=WebSettings.getDefaultUserAgent(context.getApplicationContext());manager=CookieManager.getInstance();}
  catch(RuntimeException | LinkageError unavailable){return new PublicWebsiteRepository();}
  return new PublicWebsiteRepository(new PublicWebsiteRepository.HttpSession(){
   public String cookie(){return manager.getCookie(PublicWebsiteRepository.ORIGIN);}
   public String userAgent(){return ua;}
   public void received(Map<String,List<String>> headers){
    if(headers==null)return;
    for(Map.Entry<String,List<String>> h:headers.entrySet())if("Set-Cookie".equalsIgnoreCase(h.getKey())&&h.getValue()!=null)
     for(String value:h.getValue())manager.setCookie(PublicWebsiteRepository.ORIGIN,value);
    manager.flush();
   }
  });
 }
 public interface Callback {void complete(String status);}
 public void refresh(Callback callback){
  WORKER.execute(()->{
   try{
    JSONObject response=repository.accountProfile();
    Object profile=response.opt("profile");
    if(profile==null||profile==JSONObject.NULL)state="官网未登录";
    else if(profile instanceof JSONObject && ((JSONObject)profile).has("nickname"))state="官网已登录："+((JSONObject)profile).optString("nickname");
    else state="官网会话已返回，登录状态尚未确认";
   }catch(Exception e){state="官网账号状态检查失败："+e.getMessage();}
   new Handler(Looper.getMainLooper()).post(()->callback.complete(state));
  });
 }
 public String status(){return state;}
 public boolean canSynchronize(){return false;}
 public static void clear(Runnable done){CookieManager.getInstance().removeAllCookies(value->{CookieManager.getInstance().flush();done.run();});}
}
