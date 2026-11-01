package com.diandongfangshui.guoyingtv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/** Paged native browsing of the catalog actually returned by the public service. */
public final class BrowseActivity extends Activity {
    private PublicWebsiteRepository repository;
    private PublicWebsiteRepository.CatalogPage current;
    private final ExecutorService network=Executors.newSingleThreadExecutor();
    private final ThreadPoolExecutor images=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(24),new ThreadPoolExecutor.DiscardOldestPolicy());
    private LinearLayout root,types,filters,grid;
    private ScrollView scroll;
    private TextView status,previous,next,first,last,retry;
    private EditText jump;
    private int generation;
    private volatile boolean destroyed;
    private boolean loading;
    private String requestedPath="real-drama",focusAfter="grid";
    private int requestedPage=1;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(9));if(stroke!=0)d.setStroke(dp(2),stroke);return d;}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private TextView label(String text,int size){TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(Color.WHITE);return t;}
    private TextView button(String text,Runnable action){TextView b=label(text,14);b.setFocusable(true);b.setClickable(true);b.setGravity(Gravity.CENTER);b.setPadding(dp(15),dp(9),dp(15),dp(9));b.setBackground(shape(0xff252d3c,0));b.setOnFocusChangeListener((v,f)->b.setBackground(shape(f?0xff613c3f:0xff252d3c,f?0xffff6a5f:0)));b.setOnClickListener(v->action.run());return b;}
    private void addButton(LinearLayout row,TextView v){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(44));lp.rightMargin=dp(8);row.addView(v,lp);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);repository=WebsiteSession.repository(this);
        root=column();root.setBackgroundColor(0xff0d111a);root.setPadding(dp(24),dp(16),dp(24),dp(12));setContentView(root);
        LinearLayout heading=new LinearLayout(this);addButton(heading,button("‹ 返回",this::finish));TextView title=label("全部片库",27);heading.addView(title);root.addView(heading);
        types=new LinearLayout(this);root.addView(types);renderTypes();
        HorizontalScrollView filterScroll=new HorizontalScrollView(this);filterScroll.setHorizontalScrollBarEnabled(false);filters=new LinearLayout(this);filterScroll.addView(filters);root.addView(filterScroll,new LinearLayout.LayoutParams(-1,dp(50)));
        HorizontalScrollView pagingScroll=new HorizontalScrollView(this);pagingScroll.setHorizontalScrollBarEnabled(false);LinearLayout paging=new LinearLayout(this);pagingScroll.addView(paging);root.addView(pagingScroll,new LinearLayout.LayoutParams(-1,dp(50)));
        first=button("首页",()->turn(1,"first"));previous=button("上一页",()->turn(current.page-1,"previous"));next=button("下一页",()->turn(current.page+1,"next"));last=button("末页",()->turn(current.totalPages,"last"));
        for(TextView v:new TextView[]{first,previous,next,last})addButton(paging,v);
        jump=new EditText(this);jump.setSingleLine();jump.setTextColor(Color.WHITE);jump.setHintTextColor(0xffa8b2c2);jump.setHint("页码");jump.setInputType(InputType.TYPE_CLASS_NUMBER);paging.addView(jump,new LinearLayout.LayoutParams(dp(85),dp(44)));
        addButton(paging,button("跳转",()->{if(current==null||loading)return;try{int page=Integer.parseInt(jump.getText().toString());if(page<1||page>current.totalPages)throw new IllegalArgumentException();load(current.path,page,"grid");}catch(Exception e){Toast.makeText(this,"页码应为1至"+current.totalPages,Toast.LENGTH_SHORT).show();}}));
        retry=button("重试",()->load(requestedPath,requestedPage,"grid"));addButton(paging,retry);
        status=label("正在连接官网分类…",14);status.setPadding(0,dp(5),0,dp(7));root.addView(status);
        scroll=new ScrollView(this);scroll.setFillViewport(true);grid=column();scroll.addView(grid);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        String path=state==null?"real-drama":state.getString("path","real-drama");int page=state==null?1:state.getInt("page",1);load(path,page,"grid");
    }
    private void renderTypes(){
        types.removeAllViews();String[] paths={"real-drama","comic-drama","ai-drama"},names={"真人剧","漫剧","AI剧"};
        for(int i=0;i<paths.length;i++){String path=paths[i];TextView v=button(names[i],()->load(path,1,"grid"));if(current!=null&&current.path.split("/")[0].equals(path))v.setTextColor(0xffff8177);addButton(types,v);}
    }
    private void turn(int page,String focus){if(current!=null&&!loading&&page>=1&&page<=current.totalPages)load(current.path,page,focus);}
    private void controls(){boolean ready=current!=null&&!loading;first.setEnabled(ready&&current.page>1);previous.setEnabled(ready&&current.page>1);next.setEnabled(ready&&current.page<current.totalPages);last.setEnabled(ready&&current.page<current.totalPages);retry.setEnabled(!loading);for(TextView v:new TextView[]{first,previous,next,last,retry})v.setAlpha(v.isEnabled()?1f:0.35f);}
    private void load(String path,int page,String focus){
        if(destroyed||loading)return;loading=true;requestedPath=path;requestedPage=page;focusAfter=focus;int token=++generation;
        controls();status.setText("正在读取官网分类第 "+page+" 页…");grid.removeAllViews();
        network.execute(()->{
            try{PublicWebsiteRepository.CatalogPage result=repository.catalogPage(path,page);
                runOnUiThread(()->{if(destroyed||token!=generation)return;loading=false;current=result;render();});
            }catch(Exception error){runOnUiThread(()->{if(destroyed||token!=generation)return;loading=false;status.setText("片库读取失败："+error.getMessage()+" · 请选择重试");controls();retry.requestFocus();});}
        });
    }
    private void render(){
        controls();renderTypes();filters.removeAllViews();String name="全部题材";
        for(PublicWebsiteRepository.CategoryFilter f:current.filters){TextView v=button(f.title,()->load(f.path,1,"grid"));if(f.path.equals(current.path)){v.setTextColor(0xffff8177);name=f.title;}addButton(filters,v);}
        String type=current.path.startsWith("real-drama")?"真人剧":current.path.startsWith("comic-drama")?"漫剧":"AI剧";
        status.setText(type+" / "+name+" · 官网报告 "+current.total+" 条 · 第 "+current.page+" / "+current.totalPages+" 页 · 本页 "+current.items.size()+" 部（目录数量不代表全部分集可播）");
        grid.removeAllViews();LinearLayout row=null;View firstCard=null;
        for(int i=0;i<current.items.size();i++){
            Drama d=current.items.get(i);if(i%6==0){row=new LinearLayout(this);grid.addView(row,new LinearLayout.LayoutParams(-1,dp(220)));}
            LinearLayout card=column();card.setPadding(dp(7),dp(7),dp(7),dp(7));card.setFocusable(true);card.setClickable(true);card.setContentDescription(d.title+"，目录"+d.toJson().optInt("episode_cnt")+"集");card.setBackground(shape(0xff191f2b,0));
            card.setOnFocusChangeListener((v,f)->card.setBackground(shape(f?0xff4e333b:0xff191f2b,f?0xffff6a5f:0)));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(210),1);cp.rightMargin=dp(7);row.addView(card,cp);
            ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackgroundColor(0xff30394a);card.addView(image,new LinearLayout.LayoutParams(-1,dp(135)));poster(image,d.poster,generation);
            TextView title=label(d.title,14);title.setMaxLines(2);card.addView(title);TextView count=label("目录"+d.toJson().optInt("episode_cnt")+"集 · "+d.genre,11);count.setTextColor(0xffaab5c9);count.setMaxLines(1);card.addView(count);
            card.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class).putExtra("open_series_id",d.id)));
            if(firstCard==null)firstCard=card;
        }
        int remainder=current.items.size()%6;if(remainder!=0&&row!=null)for(int i=remainder;i<6;i++)row.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        scroll.scrollTo(0,0);View target=focusAfter.equals("next")&&next.isEnabled()?next:focusAfter.equals("previous")&&previous.isEnabled()?previous:focusAfter.equals("last")?last:focusAfter.equals("first")?first:firstCard;
        if(target==null||!target.isEnabled())target=next.isEnabled()?next:retry;View focus=target;focus.post(focus::requestFocus);
    }
    private void poster(ImageView view,String url,int token){
        if(!UrlPolicy.isHttpsUrl(url))return;
        images.execute(()->{HttpURLConnection c=null;try{
            c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(5000);c.setInstanceFollowRedirects(false);if(c.getResponseCode()!=200)return;
            ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] data=new byte[4096];int n;while((n=in.read(data))!=-1){if(out.size()+n>3000000)return;out.write(data,0,n);}}
            BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=4;byte[] bytes=out.toByteArray();Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);
            if(bitmap!=null)runOnUiThread(()->{if(!destroyed&&token==generation&&view.isAttachedToWindow())view.setImageBitmap(bitmap);});
        }catch(Exception ignored){}finally{if(c!=null)c.disconnect();}});
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);if(current!=null){state.putString("path",current.path);state.putInt("page",current.page);}}
    @Override protected void onDestroy(){destroyed=true;generation++;network.shutdownNow();images.shutdownNow();super.onDestroy();}
}
