package com.local.pl;

import android.webkit.CookieManager;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Locale;

public class TorrBridge {
    private static final String SITE="https://pornolab.net/forum/";
    private final String base;

    public static class FileEntry {
        public final int id;
        public final String name;
        public FileEntry(int id,String name){this.id=id;this.name=name;}
    }
    public static class Torrent {
        public final String hash;
        public final ArrayList<FileEntry> files;
        Torrent(String hash,ArrayList<FileEntry> files){this.hash=hash;this.files=files;}
    }
    public TorrBridge(String input){
        String u=input.trim();
        if(!u.startsWith("http://")&&!u.startsWith("https://"))u="http://"+u;
        try {
            URI uri=new URI(u);
            String host=uri.getHost();
            if(uri.getUserInfo()!=null||host==null||uri.getRawQuery()!=null||uri.getRawFragment()!=null)
                throw new IllegalArgumentException("Введи IP и порт TorrServer");
            if(!"http".equalsIgnoreCase(uri.getScheme()))
                throw new IllegalArgumentException("Для локальной сети нужен адрес http://IP:порт");
            if(!privateAddress(host))
                throw new IllegalArgumentException("Допустим только локальный IP TorrServer, например 192.168.1.100");
            int port=uri.getPort();
            if(port==-1)port=8090;
            if(port<1||port>65535)throw new IllegalArgumentException("Некорректный порт");
            base="http://"+host+":"+port;
        } catch(URISyntaxException ex){
            throw new IllegalArgumentException("Неверный адрес TorrServer");
        }
    }
    private boolean privateAddress(String host){
        if(host.equals("localhost")||host.equals("127.0.0.1"))return true;
        String[] bits=host.split("\\.");
        if(bits.length!=4)return false;
        try{
            int[] a=new int[4];
            for(int i=0;i<4;i++){a[i]=Integer.parseInt(bits[i]);if(a[i]<0||a[i]>255)return false;}
            return a[0]==10 || (a[0]==192&&a[1]==168) ||
                (a[0]==172&&a[1]>=16&&a[1]<=31) || a[0]==127;
        }catch(NumberFormatException e){return false;}
    }
    public void test()throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(base+"/echo").openConnection();
        c.setConnectTimeout(4500);c.setReadTimeout(4500);
        try{
            int status=c.getResponseCode();
            if(status!=200)throw new IOException("TorrServer ответил HTTP "+status);
        }finally{c.disconnect();}
    }
    public Torrent fromTopic(Document doc,String title)throws Exception{
        String magnet=null,download=null;
        for(Element a:doc.select("a[href]")){
            String link=a.absUrl("href");
            if(link.startsWith("magnet:")&&magnet==null)magnet=link;
            if(link.startsWith("https://pornolab.net/forum/dl.php") || link.startsWith("https://www.pornolab.net/forum/dl.php")){
                download=link;break;
            }
        }
        JSONObject reply;
        if(download!=null){
            byte[] torrentBytes=fetchTorrent(download);
            reply=upload(torrentBytes,title);
        }else if(magnet!=null){
            JSONObject req=new JSONObject();
            req.put("action","add");req.put("link",magnet);
            req.put("title",title);req.put("save_to_db",true);
            reply=postJson(req);
        }else throw new IOException("На странице не найдена ссылка на .torrent. Проверь вход в аккаунт.");
        if(reply.has("error"))throw new IOException(reply.optString("error"));
        String hash=reply.optString("hash");
        if(hash.isEmpty())throw new IOException("TorrServer не вернул хеш торрента");
        ArrayList<FileEntry> files=new ArrayList<>();
        for(int attempts=0;attempts<18;attempts++){
            JSONObject req=new JSONObject();req.put("action","get");req.put("hash",hash);
            JSONObject state=postJson(req);
            JSONArray raw=state.optJSONArray("file_stats");
            if(raw!=null){
                files.clear();
                for(int i=0;i<raw.length();i++){
                    JSONObject f=raw.optJSONObject(i);
                    if(f==null)continue;
                    String path=f.optString("path");
                    String low=path.toLowerCase(Locale.ROOT);
                    if(low.matches("(?s).*\\.(mp4|mkv|avi|mov|wmv|webm|mpeg|mpg|m2ts|ts|vob|flv)$"))
                        files.add(new FileEntry(f.optInt("id",i+1),path));
                }
                if(raw.length()>0)break;
            }
            Thread.sleep(1100);
        }
        if(files.isEmpty())throw new IOException("TorrServer не получил список видеофайлов. Проверь сиды и работу сервера.");
        return new Torrent(hash,files);
    }
    public Torrent fromRawTorrent(byte[] bytes,String title)throws Exception{
        JSONObject reply=upload(bytes,title);
        return awaitTorrent(reply);
    }
    public Torrent fromMagnetLink(String magnet,String title)throws Exception{
        if(!magnet.startsWith("magnet:?"))throw new IOException("Неверная magnet-ссылка");
        JSONObject req=new JSONObject();req.put("action","add");req.put("link",magnet);
        req.put("title",title);req.put("save_to_db",true);
        return awaitTorrent(postJson(req));
    }
    private Torrent awaitTorrent(JSONObject reply)throws Exception{
        if(reply.has("error"))throw new IOException(reply.optString("error"));
        String hash=reply.optString("hash");
        if(hash.isEmpty())throw new IOException("Нет хеша от TorrServer");
        ArrayList<FileEntry> files=new ArrayList<>();
        for(int attempts=0;attempts<25;attempts++){
            JSONObject req=new JSONObject();req.put("action","get");req.put("hash",hash);
            JSONObject state=postJson(req);
            JSONArray raw=state.optJSONArray("file_stats");
            if(raw!=null&&raw.length()>0){
                for(int i=0;i<raw.length();i++){
                    JSONObject f=raw.optJSONObject(i);
                    if(f==null)continue;
                    String path=f.optString("path");
                    String low=path.toLowerCase(Locale.ROOT);
                    if(low.matches("(?s).*\\.(mp4|mkv|avi|mov|wmv|webm|mpeg|mpg|m2ts|ts|vob|flv)$"))
                        files.add(new FileEntry(f.optInt("id",i+1),path));
                }
                break;
            }
            Thread.sleep(1000);
        }
        if(files.isEmpty())throw new IOException("Не удалось получить список видеофайлов от TorrServer");
        return new Torrent(hash,files);
    }

    private byte[] fetchTorrent(String address)throws Exception{
        URL u=new URL(address);
        if(!"https".equals(u.getProtocol())||
          !(u.getHost().equalsIgnoreCase("pornolab.net")||u.getHost().equalsIgnoreCase("www.pornolab.net"))||
          !u.getPath().endsWith("/dl.php"))
            throw new IOException("Неверная ссылка на торрент");
        HttpURLConnection c=(HttpURLConnection)u.openConnection();
        c.setConnectTimeout(12000);c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent","Mozilla/5.0 (Android TV; PL)");
        c.setRequestProperty("Referer",SITE);
        String cookie=CookieManager.getInstance().getCookie(SITE);
        if(cookie!=null)c.setRequestProperty("Cookie",cookie);
        try {
            if(c.getResponseCode()!=200)throw new IOException("Ошибка загрузки .torrent: HTTP "+c.getResponseCode());
            try(InputStream in=c.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                byte[] buf=new byte[8192];int n;
                while((n=in.read(buf))!=-1){
                    bytes.write(buf,0,n);
                    if(bytes.size()>12*1024*1024)throw new IOException(".torrent слишком большой");
                }
                byte[] torrent=bytes.toByteArray();
                if(torrent.length<20||torrent[0]!='d')
                    throw new IOException("Сайт вернул страницу вместо .torrent. Возможно, требуется авторизация.");
                return torrent;
            }
        }finally{c.disconnect();}
    }
    private JSONObject upload(byte[] data,String title)throws Exception{
        String boundary="----PL"+System.currentTimeMillis();
        HttpURLConnection c=(HttpURLConnection)new URL(base+"/torrent/upload").openConnection();
        c.setConnectTimeout(10000);c.setReadTimeout(30000);c.setDoOutput(true);
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);
        try(OutputStream out=c.getOutputStream()){
            String head="--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"PL.torrent\"\r\nContent-Type: application/x-bittorrent\r\n\r\n";
            out.write(head.getBytes(StandardCharsets.UTF_8));out.write(data);
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            String save="--"+boundary+"\r\nContent-Disposition: form-data; name=\"save\"\r\n\r\ntrue\r\n";
            out.write(save.getBytes(StandardCharsets.UTF_8));
            String closing="--"+boundary+"--\r\n";out.write(closing.getBytes(StandardCharsets.UTF_8));
        }
        try {
            String response=readResponse(c);
            return jsonObject(response);
        }finally{c.disconnect();}
    }
    private JSONObject postJson(JSONObject body)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(base+"/torrents").openConnection();
        c.setConnectTimeout(6500);c.setReadTimeout(12500);
        c.setRequestMethod("POST");c.setDoOutput(true);
        c.setRequestProperty("Content-Type","application/json; charset=utf-8");
        try(OutputStream out=c.getOutputStream()){
            out.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        try{return jsonObject(readResponse(c));}
        finally{c.disconnect();}
    }
    private String readResponse(HttpURLConnection c)throws Exception{
        int code=c.getResponseCode();
        InputStream input=(code>=200&&code<300)?c.getInputStream():c.getErrorStream();
        String response="";
        if(input!=null)try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n;
            while((n=in.read(b))!=-1){
                out.write(b,0,n);if(out.size()>2*1024*1024)break;
            }
            response=out.toString("UTF-8");
        }
        if(code==401)throw new IOException("На TorrServer включена авторизация. В этой версии поддерживается сервер без пароля в доверенной локальной сети.");
        if(code<200||code>=300)throw new IOException("TorrServer HTTP "+code+": "+response);
        return response;
    }
    private JSONObject jsonObject(String body)throws Exception{
        String s=body.trim();
        if(s.startsWith("{"))return new JSONObject(s);
        if(s.startsWith("[")){
            JSONArray a=new JSONArray(s);
            if(a.length()>0&&a.optJSONObject(0)!=null)return a.getJSONObject(0);
        }
        throw new IOException("Неожиданный ответ TorrServer");
    }
    public JSONObject status(String hash)throws Exception{
        if(hash==null||!hash.matches("[a-fA-F0-9]{40}"))
            throw new IOException("Некорректный хеш торрента");
        JSONObject request=new JSONObject();
        request.put("action","get");
        request.put("hash",hash);
        return postJson(request);
    }
    public String playUrl(String hash,int id){
        return base+"/stream/PL?link="+URLEncoder(hash)+"&index="+id+"&play";
    }
    private String URLEncoder(String s){
        try{return java.net.URLEncoder.encode(s,"UTF-8");}
        catch(Exception e){return s;}
    }
}