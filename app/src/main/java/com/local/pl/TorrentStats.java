package com.local.pl;

import org.json.JSONObject;
import java.util.Locale;

/** Human-readable values from TorrServer /torrents action:get. */
public final class TorrentStats {
    private TorrentStats(){}

    public static String size(long bytes){
        if(bytes<0)return "—";
        if(bytes>=1073741824L)return String.format(Locale.US,"%.2f ГБ",bytes/1073741824.0);
        if(bytes>=1048576L)return String.format(Locale.US,"%.1f МБ",bytes/1048576.0);
        if(bytes>=1024L)return String.format(Locale.US,"%.0f КБ",bytes/1024.0);
        return bytes+" Б";
    }
    public static String speed(double bytesPerSecond){
        if(Double.isNaN(bytesPerSecond)||bytesPerSecond<0)return "—";
        return size((long)bytesPerSecond)+"/с";
    }
    public static String shortLine(JSONObject o){
        if(o==null)return "Нет статистики TorrServer";
        StringBuilder b=new StringBuilder();
        if(o.has("download_speed"))b.append("↓ ").append(speed(o.optDouble("download_speed",0)));
        if(o.has("upload_speed"))b.append("  ↑ ").append(speed(o.optDouble("upload_speed",0)));
        if(o.has("connected_seeders"))b.append("  ● Сиды: ").append(o.optInt("connected_seeders"));
        if(o.has("active_peers"))b.append("  Пиры: ").append(o.optInt("active_peers"));
        return b.length()==0?"Ожидаем информацию от TorrServer…":b.toString();
    }
    public static String overlay(JSONObject o){
        if(o==null)return "Ожидаем TorrServer…";
        String line=shortLine(o);
        long loaded=o.optLong("loaded_size",-1);
        long total=o.optLong("torrent_size",-1);
        if(loaded>=0 && total>0){
            line+="\nЗагружено: "+size(loaded)+" / "+size(total)+" ("+
                String.format(Locale.US,"%.1f%%",Math.min(100,100.0*loaded/total))+")";
        }else if(loaded>=0)line+="\nЗагружено: "+size(loaded);
        return line;
    }
    public static String full(JSONObject o){
        if(o==null)return "TorrServer не вернул данные";
        StringBuilder b=new StringBuilder();
        String title=o.optString("title","");
        if(title.isEmpty())title=o.optString("name","");
        if(!title.isEmpty())b.append(title).append("\n\n");
        b.append(shortLine(o));
        long loaded=o.optLong("loaded_size",-1);
        long total=o.optLong("torrent_size",-1);
        if(loaded>=0){
            b.append("\nПолучено: ").append(size(loaded));
            if(total>0) b.append(" из ").append(size(total)).append(" (")
                 .append(String.format(Locale.US,"%.1f%%",Math.min(100,100.0*loaded/total))).append(")");
        }else if(total>0){b.append("\nРазмер: ").append(size(total));}
        if(o.has("preloaded_bytes"))
            b.append("\nБуфер: ").append(size(o.optLong("preloaded_bytes",0)));
        if(o.has("total_peers"))
            b.append("\nВсего пиров: ").append(o.optInt("total_peers",0));
        String status=o.optString("stat_string","");
        if(!status.isEmpty())b.append("\nСостояние: ").append(status);
        return b.toString();
    }
}
