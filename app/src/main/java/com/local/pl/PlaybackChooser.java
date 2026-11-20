package com.local.pl;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.Toast;

public final class PlaybackChooser {
    private PlaybackChooser(){}

    private static final String KEY="player_mode";
    private static final String[] VALUES={"internal","ask","external"};
    private static final String[] LABELS={
        "Встроенный плеер PL (Media3)",
        "Спрашивать перед просмотром",
        "Внешний плеер (системный выбор)"
    };

    public static String label(SharedPreferences prefs){
        String v=prefs.getString(KEY,"internal");
        for(int i=0;i<VALUES.length;i++)if(VALUES[i].equals(v))return LABELS[i];
        return LABELS[0];
    }

    public static void configure(Activity a,SharedPreferences prefs,Runnable onSaved){
        String selected=prefs.getString(KEY,"internal");
        int checked=0;
        for(int i=0;i<VALUES.length;i++)if(VALUES[i].equals(selected))checked=i;
        new AlertDialog.Builder(a)
            .setTitle("Плеер по умолчанию")
            .setSingleChoiceItems(LABELS,checked,(dialog,which)->{
                prefs.edit().putString(KEY,VALUES[which]).apply();
                dialog.dismiss();
                onSaved.run();
            })
            .setNegativeButton("Отмена",null)
            .show();
    }

    public static void play(Activity a,SharedPreferences prefs,String url,String title,String hash,String server){
        String v=prefs.getString(KEY,"internal");
        if("external".equals(v)){external(a,url);return;}
        if("ask".equals(v)){
            new AlertDialog.Builder(a).setTitle("Где открыть видео?")
                .setItems(new String[]{"Встроенный плеер PL","Другой установленный плеер"},
                    (d,which)->{
                        if(which==0)internal(a,url,title,hash,server);
                        else external(a,url);
                    })
                .setNegativeButton("Отмена",null).show();
            return;
        }
        internal(a,url,title,hash,server);
    }

    private static void internal(Activity a,String url,String title,String hash,String server){
        Intent intent=new Intent(a,PlayerActivity.class);
        intent.putExtra("url",url);
        intent.putExtra("title",title);
        intent.putExtra("torrent_hash",hash);
        intent.putExtra("torrserver",server);
        a.startActivity(intent);
    }

    private static void external(Activity a,String url){
        Intent intent=new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse(url),"video/*");
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        try{
            // Deliberately use Android's installed-application selector.
            // It does not require QUERY_ALL_PACKAGES permission.
            a.startActivity(Intent.createChooser(intent,"Выбери видеоплеер"));
        }catch(ActivityNotFoundException ex){
            Toast.makeText(a,"Внешний видеоплеер не установлен",Toast.LENGTH_LONG).show();
        }
    }
}
