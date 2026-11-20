package com.local.pl;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.webkit.*;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class LampaActivity extends Activity {
    private WebView web;
    private PLBridge bridge;
    private boolean overlayOpen=false;
    private boolean loaded=false;
    private static final String HOME="https://yumata.github.io/lampa/";

    @Override public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(5894|1024|512);
        if(!getSharedPreferences("pl",MODE_PRIVATE).getBoolean("adult",false)){
            new AlertDialog.Builder(this).setTitle("PL · 18+")
                .setMessage("PL открывает каталог материалов для совершеннолетних. Подтвердите, что вам исполнилось 18 лет.")
                .setCancelable(false).setPositiveButton("Мне есть 18 лет",(d,v)->{
                    getSharedPreferences("pl",MODE_PRIVATE).edit().putBoolean("adult",true).apply();
                    start();
                }).setNegativeButton("Выход",(d,v)->finish()).show();
        } else start();
    }
    private void start(){
        bridge=new PLBridge(this);
        web=new WebView(this);
        web.setBackgroundColor(Color.rgb(17,18,27));
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setMediaPlaybackRequiresUserGesture(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.addJavascriptInterface(bridge,"PLNative");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest req){
                if(!req.isForMainFrame())return false;
                android.net.Uri uri=req.getUrl();
                boolean trusted="https".equals(uri.getScheme())&&
                    "yumata.github.io".equalsIgnoreCase(uri.getHost())&&
                    (uri.getPath()!=null&&uri.getPath().startsWith("/lampa"));
                return !trusted;
            }
            @Override public void onPageFinished(WebView view,String url){
                if(url.startsWith(HOME))inject();
            }
        });
        setContentView(web);
        web.loadUrl(HOME);
    }
    private void inject(){
        if(web==null)return;
        try(InputStream in=getAssets().open("pl-lampa.js");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            byte[] buf=new byte[8192];int n;
            while((n=in.read(buf))!=-1)bytes.write(buf,0,n);
            web.evaluateJavascript(new String(bytes.toByteArray(),StandardCharsets.UTF_8),null);
            loaded=true;
        }catch(Exception ex){Toast.makeText(this,"Ошибка плагина PL: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
    }
    void onOverlay(boolean open){runOnUiThread(()->overlayOpen=open);}
    void send(String method,String payload){
        runOnUiThread(()->{
            if(web!=null && loaded)web.evaluateJavascript(
                "window.PLHost&&window.PLHost."+method+"("+JSONObject.quote(payload)+")",null);
        });
    }
    void info(String message){
        runOnUiThread(()->Toast.makeText(this,message,Toast.LENGTH_LONG).show());
    }
    void showSettings(){
        runOnUiThread(()->{
            android.content.SharedPreferences p=getSharedPreferences("pl",MODE_PRIVATE);
            LinearLayout form=new LinearLayout(this);
            form.setPadding(24,12,24,2);form.setOrientation(LinearLayout.VERTICAL);
            EditText jackett=new EditText(this);jackett.setSingleLine(true);
            jackett.setHint("Jackett: 192.168.1.100:9117");
            jackett.setText(p.getString("jackett",""));
            form.addView(jackett);
            EditText key=new EditText(this);key.setSingleLine(true);
            key.setHint("API-ключ Jackett");key.setText(p.getString("jackett_key",""));
            form.addView(key);
            EditText torr=new EditText(this);torr.setSingleLine(true);
            torr.setHint("TorrServer: 192.168.1.100:8090");
            torr.setText(p.getString("torrserver",""));
            form.addView(torr);
            new AlertDialog.Builder(this).setTitle("Подключения PL")
                .setMessage("Серверы должны быть в доверенной локальной сети. В Jackett добавь индексатор PornoLab.")
                .setView(form).setNegativeButton("Отмена",null)
                .setPositiveButton("Сохранить",(d,b)->{
                    String j=jackett.getText().toString().trim();
                    String t=torr.getText().toString().trim();
                    String k=key.getText().toString().trim();
                    try{
                        if(!j.isEmpty())PLBridge.localBase(j,9117);
                        if(!t.isEmpty())new TorrBridge(t);
                        p.edit().putString("jackett",j).putString("jackett_key",k)
                            .putString("torrserver",t).apply();
                        info("Настройки PL сохранены");
                    }catch(Exception ex){info(ex.getMessage());}
                }).show();
        });
    }
    void openPlayer(String url,String title){
        runOnUiThread(()->{
            Intent intent=new Intent(this,PlayerActivity.class);
            intent.putExtra("url",url);intent.putExtra("title",title);
            startActivity(intent);
        });
    }
    @Override public void onBackPressed(){
        if(overlayOpen && web!=null){
            web.evaluateJavascript("window.PLHost&&window.PLHost.close()",null);
        }else if(web!=null && web.canGoBack()){
            web.goBack();
        }else finish();
    }
    @Override protected void onDestroy(){
        if(bridge!=null)bridge.close();
        if(web!=null){web.removeJavascriptInterface("PLNative");web.destroy();web=null;}
        super.onDestroy();
    }
}