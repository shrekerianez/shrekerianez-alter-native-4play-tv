package com.local.pl;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import android.webkit.*;
import android.text.TextUtils;
import org.jsoup.*;
import org.jsoup.nodes.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public class MainActivity extends Activity {
    private static final String SITE="https://pornolab.net/forum/";
    private static final int BG=0xff11121b, PANEL=0xff202232, FOCUS=0xff514075, WHITE=0xfff6f2ff, MUTED=0xffb4adc7;
    private final ExecutorService io=Executors.newFixedThreadPool(3);
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final String[][] groups={
        {"1670","Эротическое видео"},{"1672","Зарубежные фильмы"},{"1717","Фильмы DVD / HD"},
        {"1674","Русское видео"},{"1753","Видео на русском"},{"1677","Зарубежные ролики"},
        {"1800","Японское и китайское видео"},{"11","Тематическое видео"},{"1688","Гей-видео"}
    };
    private SharedPreferences prefs;
    private LinearLayout menu, main, root;
    private WebView browser;
    private String pageUrl="",pageTitle="Главная";
    private boolean browsing=false;
    private int request=0;
    private final ArrayDeque<String[]> backStack=new ArrayDeque<>();

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs=getSharedPreferences("pl",MODE_PRIVATE);
        android.webkit.CookieManager.getInstance().setAcceptCookie(true);
        if(!prefs.getBoolean("adult",false)){
            new AlertDialog.Builder(this).setTitle("PL · 18+")
                .setMessage("Приложение открывает материалы для взрослых. Подтвердите, что вам исполнилось 18 лет.")
                .setCancelable(false)
                .setPositiveButton("Мне есть 18 лет",(d,w)->{prefs.edit().putBoolean("adult",true).apply();home();})
                .setNegativeButton("Выход",(d,w)->finish()).show();
        } else home();
    }
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextColor(color);t.setTextSize(size);
        if(bold)t.setTypeface(null,Typeface.BOLD);t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }
    private GradientDrawable panel(int color,boolean focus){
        GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(10));
        if(focus)g.setStroke(dp(2),0xffd2b9ff);
        return g;
    }
    private Button button(String title,Runnable action){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);
        b.setTextColor(WHITE);b.setTextSize(15);b.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
        b.setPadding(dp(15),dp(4),dp(12),dp(4));b.setBackground(panel(PANEL,false));
        b.setOnClickListener(v->action.run());
        b.setOnFocusChangeListener((v,focused)->b.setBackground(panel(focused?FOCUS:PANEL,focused)));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(50));
        p.bottomMargin=dp(8);b.setLayoutParams(p);return b;
    }
    private void shell(String title){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.HORIZONTAL);root.setBackgroundColor(BG);
        menu=new LinearLayout(this);menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(12),dp(15),dp(10),dp(15));menu.setBackgroundColor(PANEL);
        root.addView(menu,new LinearLayout.LayoutParams(dp(246),-1));
        main=new LinearLayout(this);main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(22),dp(12),dp(20),dp(12));
        root.addView(main,new LinearLayout.LayoutParams(0,-1,1));
        setContentView(root);
        TextView mark=text("▶ PL",30,WHITE,true);mark.setPadding(dp(6),0,0,dp(10));menu.addView(mark);
        menu.addView(button("Главная",()->home()));
        menu.addView(button("Поиск",()->search()));
        ScrollView scroll=new ScrollView(this);LinearLayout list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for(String[] g:groups){
            list.addView(button(g[1],()->open(SITE+"viewforum.php?f="+g[0],g[1],true)));
        }
        scroll.addView(list);menu.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        menu.addView(button("Вход на сайт",()->web(SITE+"login.php")));
        menu.addView(button("TorrServer · настройки",()->settings()));
        menu.addView(button("Видеоплеер",()->PlaybackChooser.configure(this,prefs,()->settings())));
        TextView heading=text(title,27,WHITE,true);heading.setSingleLine(true);
        heading.setEllipsize(TextUtils.TruncateAt.END);heading.setPadding(0,0,0,dp(13));
        main.addView(heading);
    }
    private LinearLayout body(){
        ScrollView s=new ScrollView(this);s.setFillViewport(true);
        main.addView(s,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0,dp(8),0,dp(35));s.addView(content);return content;
    }
    private void note(LinearLayout holder,String message){
        TextView t=text(message,16,MUTED,false);t.setPadding(0,dp(12),0,dp(20));holder.addView(t);
    }
    private void home(){
        request++;pageUrl="";pageTitle="Главная";backStack.clear();shell("Каталог PL");
        LinearLayout b=body();
        note(b,"Поиск напрямую на PornoLab, воспроизведение через TorrServer на этой приставке. Для закрытых раздач войди в аккаунт. Управление: стрелки, OK, Назад.");
        GridLayout grid=new GridLayout(this);grid.setColumnCount(3);b.addView(grid);
        for(String[] g:groups){
            Button tile=button("▶  "+g[1],()->open(SITE+"viewforum.php?f="+g[0],g[1],true));
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams();
            lp.width=dp(205);lp.height=dp(105);lp.setMargins(0,0,dp(12),dp(12));
            grid.addView(tile,lp);
        }
        if(grid.getChildCount()>0)grid.getChildAt(0).requestFocus();
    }
    private static class Entry{
        String title,url; Entry(String t,String u){title=t;url=u;}
    }
    private void open(String url,String title,boolean push){
        if(!safeSite(url)){toast("Недопустимая ссылка");return;}
        if(push && !pageUrl.isEmpty())backStack.push(new String[]{pageUrl,pageTitle});
        pageUrl=url;pageTitle=title;final int ticket=++request;
        shell(title);LinearLayout b=body();note(b,"Загружаем раздел…");
        io.execute(()->{
            try{
                Document doc=getDocument(url);
                List<Entry> folders=new ArrayList<>(),topics=new ArrayList<>();
                Set<String> seen=new HashSet<>();
                Matcher m=Pattern.compile("[?&]f=(\\d+)").matcher(url);
                String own=m.find()?m.group(1):"";
                for(Element a:doc.select("a[href*=viewforum.php]")){
                    String link=a.absUrl("href"),name=a.text().trim();
                    Matcher id=Pattern.compile("[?&]f=(\\d+)").matcher(link);
                    if(!id.find()||id.group(1).equals(own)||name.length()<4||name.length()>115||!safeSite(link))continue;
                    if(seen.add("f"+id.group(1)))folders.add(new Entry(name,link));
                    if(folders.size()==45)break;
                }
                for(Element a:doc.select("a[href*=viewtopic.php]")){
                    String link=a.absUrl("href"),name=a.text().trim();
                    Matcher id=Pattern.compile("[?&]t=(\\d+)").matcher(link);
                    if(!id.find()||name.length()<7||name.length()>260||!safeSite(link))continue;
                    if(seen.add("t"+id.group(1)))topics.add(new Entry(name,link));
                    if(topics.size()==65)break;
                }
                ui.post(()->{
                    if(ticket!=request||browsing)return;
                    shell(title);LinearLayout view=body();
                    if(!folders.isEmpty()){section(view,"Подразделы");
                        for(Entry e:folders)view.addView(button("▣  "+e.title,()->open(e.url,e.title,true)));}
                    if(!topics.isEmpty()){section(view,"Раздачи");
                        for(Entry e:topics)view.addView(button("▶  "+e.title,()->release(e)));}
                    if(folders.isEmpty()&&topics.isEmpty()){
                        note(view,"Список пуст. Возможно, требуется вход на сайт или формат страницы изменился.");
                        view.addView(button("Открыть на сайте",()->web(url)));
                    }
                });
            }catch(Exception e){ui.post(()->{if(ticket==request&&!browsing){
                shell(title);LinearLayout v=body();note(v,"Ошибка загрузки: "+e.getMessage());
                v.addView(button("Повторить",()->open(url,title,false)));
                v.addView(button("Войти на сайт",()->web(SITE+"login.php")));
            }});}
        });
    }
    private void section(LinearLayout box,String text){
        TextView t=text(text,21,WHITE,true);t.setPadding(0,dp(13),0,dp(10));box.addView(t);
    }
    private void release(Entry e){
        final int ticket=++request;
        shell("Релиз");LinearLayout b=body();
        section(b,e.title);
        note(b,"Воспроизведение через TorrServer. На этой приставке адрес по умолчанию 127.0.0.1:8090. Для получения .torrent может потребоваться вход на официальный сайт.");
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);
        b.addView(info);
        note(info,"Читаем описание и характеристики с PornoLab…");
        b.addView(button("▶  Смотреть через TorrServer",()->startTorrent(e)));
        b.addView(button("Открыть на PornoLab",()->web(e.url)));
        b.addView(button("Настроить TorrServer",()->settings()));
        b.addView(button("Назад к разделу",()->open(pageUrl,pageTitle,false)));
        io.execute(()->{
            try{
                ReleaseMetadata m=ReleaseMetadata.from(getDocument(e.url));
                ui.post(()->{
                    if(request!=ticket||browsing)return;
                    info.removeAllViews();
                    if(m.fields.isEmpty()){
                        note(info,"Структурированные данные пока не найдены. Полное описание доступно на странице раздачи.");
                    }else{
                        section(info,"Информация из раздачи");
                        for(Map.Entry<String,String> field:m.fields.entrySet()){
                            TextView label=text(field.getKey()+":  "+field.getValue(),15,WHITE,false);
                            label.setPadding(0,dp(6),0,dp(6));info.addView(label);
                        }
                    }
                    if(!m.imageUrl.isEmpty())loadCover(info,m.imageUrl,ticket);
                });
            }catch(Exception ex){ui.post(()->{
                if(request==ticket&&!browsing){info.removeAllViews();note(info,"Не удалось прочитать описание: "+ex.getMessage());}
            });}
        });
    }
    private void loadCover(LinearLayout holder,String imageUrl,int ticket){
        io.execute(()->{
            try{
                URL u=new URL(imageUrl);
                if(!u.getProtocol().equalsIgnoreCase("https"))return;
                String host=u.getHost().toLowerCase(Locale.ROOT);
                if(host.equals("localhost")||host.equals("127.0.0.1")||host.endsWith(".local"))return;
                HttpURLConnection connection=(HttpURLConnection)u.openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(5500);connection.setReadTimeout(7000);
                try{
                    if(connection.getResponseCode()!=200||connection.getContentLengthLong()>4000000)return;
                    String type=connection.getContentType();
                    if(type==null||!type.toLowerCase(Locale.ROOT).startsWith("image/"))return;
                    byte[] bytes;
                    try(InputStream stream=connection.getInputStream();
                        ByteArrayOutputStream out=new ByteArrayOutputStream()){
                        byte[] buffer=new byte[4096];int n;
                        while((n=stream.read(buffer))!=-1){
                            out.write(buffer,0,n);
                            if(out.size()>4000000)return;
                        }
                        bytes=out.toByteArray();
                    }
                    Bitmap bm=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                    if(bm==null)return;
                    ui.post(()->{
                        if(request!=ticket||browsing)return;
                        ImageView cover=new ImageView(this);
                        cover.setImageBitmap(bm);cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        holder.addView(cover,new LinearLayout.LayoutParams(dp(265),dp(310)));
                    });
                }finally{connection.disconnect();}
            }catch(Exception ignored){}
        });
    }
    private void startTorrent(Entry e){
        final String host=prefs.getString("torrserver","127.0.0.1:8090");
        if(host.isEmpty()){toast("Сначала настрой адрес TorrServer");settings();return;}
        new AlertDialog.Builder(this).setTitle("PL")
            .setMessage("Передать торрент на ваш TorrServer и открыть поток?")
            .setNegativeButton("Отмена",null)
            .setPositiveButton("Продолжить",(d,w)->{
                toast("Получаем торрент и список файлов…");
                io.execute(()->{
                    try{
                        TorrBridge bridge=new TorrBridge(host);
                        TorrBridge.Torrent torrent=bridge.fromTopic(getDocument(e.url),e.title);
                        ui.post(()->selectFile(bridge,torrent));
                    }catch(Exception ex){ui.post(()->new AlertDialog.Builder(this)
                        .setTitle("Не удалось начать просмотр").setMessage(ex.getMessage())
                        .setPositiveButton("OK",null).show());}
                });
            }).show();
    }
    private void selectFile(TorrBridge bridge,TorrBridge.Torrent torrent){
        if(torrent.files.isEmpty()){toast("В раздаче нет поддерживаемых видеофайлов");return;}
        String[] names=new String[torrent.files.size()];
        for(int i=0;i<names.length;i++)names[i]=torrent.files.get(i).name;
        new AlertDialog.Builder(this).setTitle("Выбери видео").setItems(names,(d,i)->{
            PlaybackChooser.play(this,prefs,bridge.playUrl(torrent.hash,torrent.files.get(i).id),names[i]);
        }).setNegativeButton("Отмена",null).show();
    }
    private void search(){
        request++;shell("Поиск по трекеру");LinearLayout b=body();
        note(b,"Для поиска нужен вход в аккаунт PornoLab.");
        EditText input=new EditText(this);input.setSingleLine();input.setTextColor(WHITE);
        input.setHintTextColor(MUTED);input.setHint("Название раздачи");
        b.addView(input,new LinearLayout.LayoutParams(-1,dp(65)));
        b.addView(button("Найти",()->{
            String q=input.getText().toString().trim();
            if(q.isEmpty())return;
            try{open(SITE+"tracker.php?nm="+URLEncoder.encode(q,"UTF-8"),"Поиск: "+q,false);}
            catch(Exception e){toast(e.getMessage());}
        }));
        input.requestFocus();
    }
    private void settings(){
        request++;shell("TorrServer");LinearLayout b=body();
        note(b,"Если TorrServe установлен прямо на Google TV Streamer, оставь 127.0.0.1:8090. Запусти сервер в TorrServe перед просмотром. Если сервер на другом устройстве, введи его локальный IP и порт.");
        EditText address=new EditText(this);address.setTextColor(WHITE);address.setTextSize(19);
        address.setSingleLine(true);address.setHint("192.168.1.100:8090");
        address.setText(prefs.getString("torrserver","127.0.0.1:8090"));
        b.addView(address,new LinearLayout.LayoutParams(-1,dp(65)));
        b.addView(button("Сохранить и проверить",()->{
            String host=address.getText().toString().trim();
            try{
                TorrBridge bridge=new TorrBridge(host);
                prefs.edit().putString("torrserver",host).apply();
                toast("Адрес сохранён. Проверяем соединение…");
                io.execute(()->{
                    try{bridge.test();ui.post(()->toast("TorrServer доступен"));}
                    catch(Exception e){ui.post(()->new AlertDialog.Builder(this)
                        .setTitle("Соединение не установлено").setMessage(e.getMessage())
                        .setPositiveButton("OK",null).show());}
                });
            }catch(Exception e){toast(e.getMessage());}
        }));
        b.addView(button("Плеер: "+PlaybackChooser.label(prefs),()->PlaybackChooser.configure(this,prefs,()->settings())));
        b.addView(button("Вход в PornoLab",()->web(SITE+"login.php")));
        b.addView(button("Вернуться в каталог",()->home()));
    }
    private Document getDocument(String url)throws Exception{
        if(!safeSite(url))throw new IOException("Недопустимый адрес");
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(13000);c.setReadTimeout(18000);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent",WebSettings.getDefaultUserAgent(this));
        String cookie=android.webkit.CookieManager.getInstance().getCookie(SITE);
        if(cookie!=null)c.setRequestProperty("Cookie",cookie);
        try(InputStream s=c.getInputStream()){
            if(!safeSite(c.getURL().toString()))throw new IOException("Переадресация вне трекера");
            return Jsoup.parse(s,null,c.getURL().toString());
        }finally{c.disconnect();}
    }
    private boolean safeSite(String url){
        try{Uri u=Uri.parse(url);return "https".equalsIgnoreCase(u.getScheme())
            && ("pornolab.net".equalsIgnoreCase(u.getHost())||"www.pornolab.net".equalsIgnoreCase(u.getHost()));}
        catch(Exception e){return false;}
    }
    private void web(String url){
        if(!safeSite(url)){toast("Разрешён только PornoLab");return;}
        browsing=true;request++;
        LinearLayout vertical=new LinearLayout(this);vertical.setOrientation(LinearLayout.VERTICAL);
        vertical.setBackgroundColor(BG);
        vertical.addView(button("←  Вернуться в PL",()->closeBrowser()));
        browser=new WebView(this);
        browser.getSettings().setJavaScriptEnabled(true);
        browser.getSettings().setDomStorageEnabled(true);
        browser.getSettings().setUserAgentString(WebSettings.getDefaultUserAgent(this));
        browser.getSettings().setBuiltInZoomControls(true);
        browser.getSettings().setDisplayZoomControls(false);
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(browser,false);
        browser.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
                return !safeSite(r.getUrl().toString());
            }
            @Override public void onPageFinished(WebView v,String url){android.webkit.CookieManager.getInstance().flush();}
        });
        vertical.addView(browser,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(vertical);browser.loadUrl(url);
    }
    private void closeBrowser(){
        browsing=false;
        if(browser!=null){browser.stopLoading();browser.destroy();browser=null;}
        if(!pageUrl.isEmpty())open(pageUrl,pageTitle,false);else home();
    }
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    @Override public void onBackPressed(){
        if(browsing){closeBrowser();return;}
        if(!backStack.isEmpty()){String[] p=backStack.pop();open(p[0],p[1],false);}
        else home();
    }
    @Override protected void onDestroy(){request++;io.shutdownNow();if(browser!=null)browser.destroy();super.onDestroy();}
}