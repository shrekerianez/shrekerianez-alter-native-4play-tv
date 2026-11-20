package com.local.pl;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
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
        menu.addView(button("О приложении и кэше",()->settings()));
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
        note(b,"PL автономный: поиск и загрузка идут прямо с PornoLab, торрент воспроизводится на приставке. Для закрытых разделов войди в аккаунт.");
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
        request++;shell("Релиз");LinearLayout b=body();
        section(b,e.title);
        note(b,"Воспроизведение происходит на самой приставке. PL скачивает части торрент-файла в локальный кэш. Для получения .torrent может понадобиться вход.");
        b.addView(button("▶  Смотреть в PL",()->startTorrent(e)));
        b.addView(button("Открыть на PornoLab",()->web(e.url)));
        b.addView(button("Назад к разделу",()->open(pageUrl,pageTitle,false)));
    }
    private void startTorrent(Entry e){
        toast("Получаем .torrent с PornoLab…");
        io.execute(()->{
            try{
                Document doc=getDocument(e.url);
                String download="";
                for(Element a:doc.select("a[href]")){
                    String candidate=a.absUrl("href");
                    if(safeSite(candidate)&&Uri.parse(candidate).getPath().endsWith("/dl.php")){
                        download=candidate;break;
                    }
                }
                if(download.isEmpty())throw new IOException("На странице нет ссылки на .torrent. Возможно, требуется авторизация на PornoLab.");
                byte[] data=fetchTorrentFile(download);
                LocalTorrentEngine engine=LocalTorrentEngine.get(this);
                List<LocalTorrentEngine.VideoFile> files=engine.videos(data);
                if(files.isEmpty())throw new IOException("В раздаче не найдено видеофайлов");
                ui.post(()->selectFile(data,files,e.title));
            }catch(Exception ex){ui.post(()->new AlertDialog.Builder(this)
                .setTitle("Не удалось открыть раздачу")
                .setMessage(ex.getMessage())
                .setNegativeButton("Закрыть",null)
                .setPositiveButton("Войти на сайт",(d,w)->web(SITE+"login.php")).show());}
        });
    }
    private byte[] fetchTorrentFile(String address)throws Exception{
        if(!safeSite(address)||!Uri.parse(address).getPath().endsWith("/dl.php"))
            throw new IOException("Загрузка разрешена только с PornoLab");
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();
        c.setConnectTimeout(12000);c.setReadTimeout(25000);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent",WebSettings.getDefaultUserAgent(this));
        c.setRequestProperty("Referer",SITE);
        String cookie=android.webkit.CookieManager.getInstance().getCookie(SITE);
        if(cookie!=null)c.setRequestProperty("Cookie",cookie);
        try{
            int code=c.getResponseCode();
            if(code!=200)throw new IOException("PornoLab: ошибка скачивания торрента HTTP "+code);
            try(InputStream in=c.getInputStream();ByteArrayOutputStream result=new ByteArrayOutputStream()){
                byte[] buf=new byte[8192];int n;
                while((n=in.read(buf))!=-1){
                    result.write(buf,0,n);
                    if(result.size()>12*1024*1024)throw new IOException("Размер .torrent превышает 12 МБ");
                }
                byte[] data=result.toByteArray();
                if(data.length<20||data[0]!='d')
                    throw new IOException("Сайт вернул страницу вместо .torrent. Проверь вход в PornoLab.");
                return data;
            }
        }finally{c.disconnect();}
    }
    private void selectFile(byte[] data,List<LocalTorrentEngine.VideoFile> files,String title){
        String[] names=new String[files.size()];
        for(int i=0;i<names.length;i++)names[i]=files.get(i).name;
        new AlertDialog.Builder(this).setTitle("Что будем смотреть?")
            .setItems(names,(d,i)->{
                toast("Подключаем встроенный торрент-движок. Буферизация зависит от сидов…");
                io.execute(()->{
                    try{
                        String url=LocalTorrentEngine.get(this).play(data,files.get(i).index);
                        ui.post(()->{
                            Intent intent=new Intent(this,PlayerActivity.class);
                            intent.putExtra("url",url);
                            intent.putExtra("title",names[i]);
                            startActivity(intent);
                        });
                    }catch(Throwable t){ui.post(()->new AlertDialog.Builder(this)
                        .setTitle("Не удалось начать воспроизведение").setMessage(String.valueOf(t.getMessage()))
                        .setPositiveButton("OK",null).show());}
                });
            })
            .setNegativeButton("Отмена",null).show();
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
        request++;shell("PL · Автономный режим");LinearLayout b=body();
        File root=getExternalFilesDir(null);
        note(b,"Jackett, NAS и TorrServer не нужны. Авторизация производится через официальный сайт PornoLab. Локальный торрент-движок скачивает части видео в память приставки.");
        if(root!=null){
            StatFs stat=new StatFs(root.getAbsolutePath());
            long free=stat.getAvailableBytes()/1024/1024;
            note(b,"Свободное место в памяти устройства: "+free+" МБ. Для больших видео может не хватить места.");
        }
        b.addView(button("Вход в PornoLab",()->web(SITE+"login.php")));
        b.addView(button("Снова открыть каталог",()->home()));
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