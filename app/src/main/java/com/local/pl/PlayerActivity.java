package com.local.pl;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public class PlayerActivity extends Activity {
    private ExoPlayer player;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String url=getIntent().getStringExtra("url");
        if(url==null||!url.startsWith("http://")){finish();return;}
        PlayerView view=new PlayerView(this);
        view.setUseController(true);
        view.setControllerAutoShow(true);
        view.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        view.setFocusable(true);
        setContentView(view);
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
    @Override protected void onStop(){super.onStop();if(player!=null)player.pause();}
    @Override protected void onDestroy(){
        if(player!=null){player.release();player=null;}
        super.onDestroy();
    }
}