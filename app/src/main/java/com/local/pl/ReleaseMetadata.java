package com.local.pl;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.util.*;
import java.util.regex.*;

public final class ReleaseMetadata {
    public final LinkedHashMap<String,String> fields=new LinkedHashMap<>();
    public final String imageUrl;
    public final List<String> imageUrls;

    private ReleaseMetadata(List<String> urls){
        this.imageUrls=Collections.unmodifiableList(urls);
        this.imageUrl=urls.isEmpty()?"":urls.get(0);
    }

    public static ReleaseMetadata from(Document doc){
        // The first forum post contains the release description, not replies
        // or the user upload/download ratio FAQ link.
        Element post=doc.selectFirst("td.message");
        if(post==null)post=doc.selectFirst(".post_body");
        if(post==null)post=doc.selectFirst(".post_body_inner");
        String info=post==null?"":post.wholeText();
        if(info.length()>30000)info=info.substring(0,30000);

        // PornoLab frequently uses <var class="postImg" title="https://...">
        // instead of an <img src> element for externally hosted covers.
        LinkedHashSet<String> candidates=new LinkedHashSet<>();
        if(post!=null){
            for(Element cover:post.select("var.postImg[title], var.postImg[data-src], img[src]")){
                String address="";
                if(cover.hasClass("postImg")){
                    address=cover.attr("title");
                    if(address.isEmpty())address=cover.attr("data-src");
                }else address=cover.absUrl("src");
                if(address.startsWith("http://")){
                    address="https://"+address.substring(7);
                }
                if(address.startsWith("https://")&&!address.contains("smiles")&&
                    !address.contains("avatar")&&!address.contains("logo")&&
                    !address.contains("emoticon")){
                    candidates.add(address);
                }
                if(candidates.size()>=8)break;
            }
        }
        ReleaseMetadata result=new ReleaseMetadata(new ArrayList<>(candidates));

        for(String[] f:new String[][]{
            {"Год","Год производства|Год выпуска"},
            {"Страна","Страна"},
            {"Жанр","Жанр"},
            {"Студия","Студия"},
            {"Участники","В ролях"},
            {"Режиссёр","Режисс[её]р"},
            {"Продолжительность","Продолжительность"},
            {"Качество","Качество видео"},
            {"Формат","Формат видео"},
            {"Видео","Видео кодек|Видео:"},
            {"Аудио","Аудио кодек"},
            {"Оценка (из оформления раздачи)","Рейтинг фильма|Оценка фильма"}
        }){
            String value=find(info,f[1]);
            if(!value.isEmpty())result.fields.put(f[0],value);
        }
        return result;
    }
    private static String find(String info,String label){
        Pattern p=Pattern.compile("(?im)(?:^|\\n)\\s*(?:"+label+")\\s*[:：]\\s*([^\\r\\n]{1,220})");
        Matcher m=p.matcher(info);
        if(!m.find())return "";
        String result=m.group(1).replace('\u00a0',' ').trim();
        // No fake ratings: display it only if a numeric score appears in
        // the original forum-post content, never in the torrent ratio FAQ.
        if(label.contains("Рейтинг фильма")&&!result.matches(".*\\d.*"))return "";
        return result.length()>200?result.substring(0,200):result;
    }
}
