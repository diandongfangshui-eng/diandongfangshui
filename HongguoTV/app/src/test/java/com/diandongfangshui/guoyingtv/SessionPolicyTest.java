package com.diandongfangshui.guoyingtv;
import org.junit.Test;
import java.net.URL;
import static org.junit.Assert.*;
public class SessionPolicyTest {
 @Test public void cookieStaysOnExactHttpsContentOrigin()throws Exception{
  assertTrue(SessionPolicy.accepts(new URL("https://hongguoduanju.com/api/pc/profile")));
  assertFalse(SessionPolicy.accepts(new URL("https://hongguoduanju.com.attacker.example/profile")));
  assertFalse(SessionPolicy.accepts(new URL("https://other.example/profile")));
  assertFalse(SessionPolicy.accepts(new URL("http://hongguoduanju.com/profile")));
  assertFalse(SessionPolicy.accepts(new URL("https://hongguoduanju.com:8443/profile")));
 }
 @Test(expected=IllegalArgumentException.class) public void rejectsCookieHeaderInjection(){SessionPolicy.header("a=b\r\nOther: value");}
 @Test public void absentSessionRemainsAnonymous(){assertEquals("",SessionPolicy.header(null));}
}
