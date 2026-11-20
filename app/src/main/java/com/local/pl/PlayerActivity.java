package com.local.pl;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Player with optional live read-only stats from the local TorrServer. */
public class PlayerActivity extends Activity {
    private ExoPlayer player;
    private TextView stats;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Runnable tick;
    private volatile boolean polling;
    private String hash,server;

    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String url=getIntent().getStringExtra("url");
        hash=getIntent().getStringExtra("torrent_hash");
        server=getIntent().getStringExtra("torrserver");
        if(url==null||!url.startsWith("http://")){finish();return;}

        FrameLayout root=new FrameLayout(this);
        PlayerView view=new PlayerView(this);
        view.setUseController(true);
        view.setControllerAutoShow(true);
        view.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        view.setFocusable(true);
        root.addView(view,new FrameLayout.LayoutParams(-1,-1));

        if(hash!=null&&hash.matches("[a-fA-F0-9]{40}")&&server!=null&&!server.isEmpty()){
            stats=new TextView(this);
            stats.setTextColor(Color.WHITE);
            stats.setTextSize(13);
            stats.setTypeface(null,Typeface.BOLD);
            stats.setBackgroundColor(0xbb11121b);
            stats.setPadding(dp(14),dp(9),dp(14),dp(9));
            stats.setText("TorrServer: получаем статистику…");
            stats.setFocusable(false);
            FrameLayout.LayoutParams caption=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT);
            caption.setMargins(dp(12),dp(12),dp(16),0);
            root.addView(stats,caption);
        }

        setContentView(root);
        player=new ExoPlayer.Builder(this).build();
        view.setPlayer(player);
        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
        player.addListener(new Player.Listener(){
            @Override public void onPlayerError(androidx.media3.common.PlaybackException error){
                Toast.makeText(PlayerActivity.this,"Ошибка воспроизведения: "+error.getMessage(),Toast.LENGTH_LONG).show();
            }
        });
        player.prepare();
        player.play();
        view.requestFocus();
    }

    @Override protected void onStart(){
        super.onStart();
        if(stats==null)return;
        polling=true;
        tick=new Runnable(){
            @Override public void run(){
                if(!polling)return;
                worker.execute(()->{
                    String output;
                    try{output=TorrentStats.overlay(new TorrBridge(server).status(hash));}
                    catch(Exception ex){output="Статистика TorrServer недоступна";}
                    String text=output;
                    ui.post(()->{if(polling&&stats!=null)stats.setText(text);});
                });
                ui.postDelayed(this,2500);
            }
        };
        ui.post(tick);
    }
    @Override protected void onStop(){
        polling=false;
        if(tick!=null)ui.removeCallbacks(tick);
        if(player!=null)player.pause();
        super.onStop();
    }
    @Override protected void onDestroy(){
        polling=false;
        if(tick!=null)ui.removeCallbacks(tick);
        worker.shutdownNow();
        if(player!=null){player.release();player=null;}
        super.onDestroy();
    }
}