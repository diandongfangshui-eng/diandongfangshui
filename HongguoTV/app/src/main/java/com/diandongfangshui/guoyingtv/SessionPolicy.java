package com.diandongfangshui.guoyingtv;
import java.net.URL;
/** Exact first-party boundary for this application's own website session. */
public final class SessionPolicy {
 private SessionPolicy(){}
 public static boolean accepts(URL url){return "https".equalsIgnoreCase(url.getProtocol()) && "hongguoduanju.com".equalsIgnoreCase(url.getHost()) && (url.getPort()==-1||url.getPort()==443) && url.getUserInfo()==null;}
 public static String header(String value){if(value==null)return "";if(value.indexOf('\r')>=0||value.indexOf('\n')>=0)throw new IllegalArgumentException("会话头包含非法换行");return value;}
}
