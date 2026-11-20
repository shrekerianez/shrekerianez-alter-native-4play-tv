package com.local.pl;

import android.webkit.JavascriptInterface;
import android.content.SharedPreferences;
import android.app.AlertDialog;
import android.content.Intent;
import org.json.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class PLBridge {
    private final LampaActivity app;
    private final ExecutorService io=Executors.newFixedThreadPool(2);
    private final Map<Integer,Item> found=new ConcurrentHashMap<>();
    private volatile int generation=0;
    private static class Item {
        String title,download;long size;int seeds;
        Item(String t,String d,long s,int x){title=t;download=d;size=s;seeds=x;}
    }
    PLBridge(LampaActivity app){this.app=app;}
    void close(){generation++;io.shutdownNow();}
    @JavascriptInterface public void overlay(boolean visible){app.onOverlay(visible);}
    @JavascriptInterface public void settings(){app.showSettings();}

    public static String localBase(String input,int defaultPort){
        try{
            String s=input.trim();
            if(!s.startsWith("http://")&&!s.startsWith("https://"))s="http://"+s;
            URI u=new URI(s);
            if(!"http".equalsIgnoreCase(u.getScheme())||u.getUserInfo()!=null||
                u.getRawQuery()!=null||u.getRawFragment()!=null||u.getHost()==null ||
                (u.getRawPath()!=null&&!u.getRawPath().isEmpty()&&!"/".equals(u.getRawPath())))
                throw new IllegalArgumentException("Используй http://локальный-IP:порт без пути");
            String host=u.getHost();
            String[] p=host.split("\\.");
            if(p.length!=4)throw new IllegalArgumentException("Укажи IPv4-адрес в локальной сети");
            int[] oct=new int[4];
            for(int i=0;i<4;i++){oct[i]=Integer.parseInt(p[i]);if(oct[i]<0||oct[i]>255)throw new IllegalArgumentException();}
            boolean privateIP=oct[0]==10||oct[0]==192&&oct[1]==168||
                oct[0]==172&&oct[1]>=16&&oct[1]<=31||oct[0]==127;
            if(!privateIP)throw new IllegalArgumentException("Укажи IP в доверенной локальной сети");
            int port=u.getPort()==-1?defaultPort:u.getPort();
            if(port<1||port>65535)throw new IllegalArgumentException("Неверный порт");
            return "http://"+host+":"+port;
        }catch(Exception e){throw new IllegalArgumentException("Неверный адрес: нужен локальный IP и порт");}
    }
    @JavascriptInterface public void search(String query,int category){
        SharedPreferences p=app.getSharedPreferences("pl",0);
        final String server=p.getString("jackett",""),key=p.getString("jackett_key","");
        final int ticket=++generation;
        if(server.isEmpty()||key.isEmpty()){
            app.send("error","Открой Настройки PL, введи адрес и API-ключ Jackett.");
            return;
        }
        io.execute(()->{
            try{
                String base=localBase(server,9117);
                String url=base+"/api/v2.0/indexers/pornolab/results/torznab/api?apikey="+
                    enc(key)+"&t=search&q="+enc(query==null?"":query.trim());
                if(category>0)url+="&cat="+category;
                byte[] rss=request(url,4*1024*1024,15000);
                DocumentBuilderFactory dbf=DocumentBuilderFactory.newInstance();
                dbf.setNamespaceAware(true);
                dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
                dbf.setFeature("http://xml.org/sax/features/external-general-entities",false);
                dbf.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
                dbf.setExpandEntityReferences(false);
                Document doc=dbf.newDocumentBuilder().parse(new ByteArrayInputStream(rss));
                NodeList nodes=doc.getElementsByTagName("item");
                ArrayList<Item> items=new ArrayList<>();
                JSONArray arr=new JSONArray();
                for(int i=0;i<nodes.getLength()&&items.size()<100;i++){
                    Element el=(Element)nodes.item(i);
                    String title=tag(el,"title"),link=tag(el,"link");
                    NodeList enclosures=el.getElementsByTagName("enclosure");
                    if(enclosures.getLength()>0){
                        String d=((Element)enclosures.item(0)).getAttribute("url");
                        if(!d.isEmpty())link=d;
                    }
                    if(title.isEmpty()||link.isEmpty())continue;
                    long size=parseLong(tag(el,"size"));
                    int seeds=0;
                    NodeList attr=el.getElementsByTagNameNS("*","attr");
                    for(int a=0;a<attr.getLength();a++){
                        Element at=(Element)attr.item(a);
                        if("seeders".equals(at.getAttribute("name"))||"seed".equals(at.getAttribute("name")))
                            seeds=(int)parseLong(at.getAttribute("value"));
                    }
                    Item item=new Item(title,link,size,seeds);
                    items.add(item);
                    JSONObject j=new JSONObject();
                    j.put("id",items.size()-1);j.put("title",title);
                    j.put("size",size);j.put("seeders",seeds);
                    arr.put(j);
                }
                if(ticket!=generation)return;
                found.clear();
                for(int i=0;i<items.size();i++)found.put(i,items.get(i));
                JSONObject output=new JSONObject();
                output.put("items",arr);output.put("message","PornoLab · найдено: "+items.size());
                app.send("results",output.toString());
            }catch(Exception e){if(ticket==generation)app.send("error",e.getMessage());}
        });
    }
    @JavascriptInterface public void play(int id){
        Item item=found.get(id);
        if(item==null){app.send("error","Релиз не найден в текущей выдаче");return;}
        SharedPreferences p=app.getSharedPreferences("pl",0);
        String address=p.getString("torrserver","");
        if(address.isEmpty()){app.send("error","Укажи адрес TorrServer в настройках PL");return;}
        app.info("Загрузка метаданных для TorrServer…");
        io.execute(()->{
            try{
                TorrBridge torr=new TorrBridge(address);
                TorrBridge.Torrent result;
                if(item.download.startsWith("magnet:")){
                    result=torr.fromMagnetLink(item.download,item.title);
                }else{
                    String allowed=localBase(p.getString("jackett",""),9117);
                    if(!item.download.startsWith(allowed+"/") &&
                       !item.download.startsWith("https://pornolab.net/forum/dl.php") &&
                       !item.download.startsWith("https://www.pornolab.net/forum/dl.php"))
                        throw new IOException("Неожиданный адрес загрузки от Jackett");
                    byte[] data=request(item.download,12*1024*1024,25000);
                    if(data.length<20||data[0]!='d')throw new IOException("Jackett вернул не .torrent: проверь авторизацию PornoLab в Jackett");
                    result=torr.fromRawTorrent(data,item.title);
                }
                TorrBridge.Torrent torrent=result;
                app.runOnUiThread(()->{
                    String[] names=new String[torrent.files.size()];
                    for(int i=0;i<names.length;i++)names[i]=torrent.files.get(i).name;
                    new AlertDialog.Builder(app).setTitle("Выбери видео")
                        .setItems(names,(d,i)->app.openPlayer(torr.playUrl(torrent.hash,torrent.files.get(i).id),names[i]))
                        .setNegativeButton("Отмена",null).show();
                });
            }catch(Exception e){app.send("error",e.getMessage());}
        });
    }
    private static String tag(Element item,String name){
        NodeList nodes=item.getElementsByTagName(name);
        if(nodes.getLength()>0)return nodes.item(0).getTextContent().trim();
        return "";
    }
    private static String enc(String value){
        try{return java.net.URLEncoder.encode(value,"UTF-8");}
        catch(Exception e){return value;}
    }
    private static long parseLong(String s){try{return Long.parseLong(s);}catch(Exception e){return 0;}}
    private static byte[] request(String url,int limit,int timeout)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(7000);c.setReadTimeout(timeout);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent","PL Android TV/0.3");
        try{
            int status=c.getResponseCode();
            if(status==401||status==403)throw new IOException("Jackett: неверный API-ключ или требуется вход");
            if(status!=200)throw new IOException("Сервер ответил HTTP "+status);
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buf=new byte[8192];int n;
                while((n=in.read(buf))!=-1){
                    out.write(buf,0,n);
                    if(out.size()>limit)throw new IOException("Ответ сервера слишком большой");
                }
                return out.toByteArray();
            }
        }finally{c.disconnect();}
    }
}