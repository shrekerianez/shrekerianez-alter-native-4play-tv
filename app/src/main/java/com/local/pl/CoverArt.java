package com.local.pl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.webkit.CookieManager;
import java.io.*;
import java.net.*;
import java.util.*;

/** Download and downscale real cover images referenced in PornoLab posts. */
public final class CoverArt {
    private CoverArt(){}

    public static Bitmap fetch(Context context,List<String> candidates){
        for(String address:candidates){
            HttpURLConnection connection=null;
            try{
                URL url=new URL(address);
                String host=url.getHost().toLowerCase(Locale.ROOT);
                if(!"https".equalsIgnoreCase(url.getProtocol())||host.isEmpty()||
                    host.equals("localhost")||host.endsWith(".local")||
                    host.equals("127.0.0.1")||host.equals("0.0.0.0")||
                    host.startsWith("192.168.")||host.startsWith("10.")||
                    host.startsWith("172.16.")||url.getUserInfo()!=null)continue;
                connection=(HttpURLConnection)url.openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(6000);connection.setReadTimeout(9000);
                connection.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 12; TV) AppleWebKit/537.36 Chrome/125 Safari/537.36");
                connection.setRequestProperty("Referer","https://pornolab.net/forum/");
                if(host.equals("pornolab.net")||host.endsWith(".pornolab.net")){
                    String cookie=CookieManager.getInstance().getCookie("https://pornolab.net/forum/");
                    if(cookie!=null)connection.setRequestProperty("Cookie",cookie);
                }
                if(connection.getResponseCode()!=200)continue;
                String type=connection.getContentType();
                if(type==null||!type.toLowerCase(Locale.ROOT).startsWith("image/"))continue;
                if(connection.getContentLengthLong()>4*1024*1024)continue;
                byte[] bytes;
                try(InputStream stream=connection.getInputStream();
                    ByteArrayOutputStream output=new ByteArrayOutputStream()){
                    byte[] chunk=new byte[8192];int n;
                    while((n=stream.read(chunk))!=-1){
                        output.write(chunk,0,n);
                        if(output.size()>4*1024*1024)throw new IOException("Image too big");
                    }
                    bytes=output.toByteArray();
                }
                BitmapFactory.Options size=new BitmapFactory.Options();
                size.inJustDecodeBounds=true;
                BitmapFactory.decodeByteArray(bytes,0,bytes.length,size);
                if(size.outWidth<50||size.outHeight<50)continue;
                BitmapFactory.Options options=new BitmapFactory.Options();
                options.inSampleSize=1;
                while(size.outWidth/options.inSampleSize>520||
                      size.outHeight/options.inSampleSize>720)options.inSampleSize*=2;
                Bitmap cover=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                if(cover!=null)return cover;
            }catch(Exception ignored){
                // Try the next cover image; hosts may require HTTPS or expire.
            }finally{
                if(connection!=null)connection.disconnect();
            }
        }
        return null;
    }
}
