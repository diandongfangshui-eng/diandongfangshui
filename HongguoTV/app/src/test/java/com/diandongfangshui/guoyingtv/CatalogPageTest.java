package com.diandongfangshui.guoyingtv;
import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;
public class CatalogPageTest {
 private JSONObject page()throws Exception{
  try(java.io.InputStream in=getClass().getResourceAsStream("/public-category-page.json")){
   java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
   return new JSONObject(out.toString("UTF-8"));
  }
 }
 @Test public void realPagePreservesPaginationAndServerGenreRoutes()throws Exception{
  PublicWebsiteRepository.CatalogPage p=PublicWebsiteRepository.parseCatalogPage(page(),"real-drama");
  assertEquals(24,p.items.size());assertEquals(800,p.total);assertEquals(34,p.totalPages);assertEquals(1,p.page);
  assertTrue(p.filters.stream().anyMatch(f->f.path.equals("real-drama/urban")&&f.title.equals("都市")));
 }
 @Test public void canonicalFirstPageAndLaterPageUseDifferentUrls(){assertEquals("/category/real-drama/urban",PublicWebsiteRepository.categoryPath("real-drama/urban",1));assertEquals("/category/real-drama/urban?page=2",PublicWebsiteRepository.categoryPath("real-drama/urban",2));}
 @Test(expected=IllegalArgumentException.class) public void rejectsForeignOrTraversingCategory(){PublicWebsiteRepository.categoryPath("../other",1);}
 @Test(expected=java.io.IOException.class) public void rejectsResponseForDifferentGenre()throws Exception{PublicWebsiteRepository.parseCatalogPage(page(),"real-drama/urban");}
}
